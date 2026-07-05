# Gemini Audio Parse Batch Processor Design

**작성일:** 2026-07-01
**상태:** 합의 완료, 구현 대기

---

## 1. 배경과 목표

### 배경

`NavigationParseService.parse()`는 사용자의 음성 파일을 받아 `GeminiAudioParseClient.parse()`로 Gemini API를 동기 호출하고 있다.
요청이 들어올 때마다 곧바로 호출하는 1:1 구조라, 동시 요청이 늘면 다음 문제가 생긴다.

- **Rate Limit 압박** — 짧은 시간에 N건이 들어오면 분당 호출 한도를 빠르게 소진한다.
- **호출 비용/네트워크 왕복 비효율** — 건당 1회씩 호출하므로, 묶을 수 있는 호출도 묶지 않고 그대로 N번 발생한다.

본 기능(예약 길찾기 파싱)은 **실시간 응답 UX가 아니라 예약 등록 흐름**에 속한다.
즉 사용자가 음성을 제출한 뒤 응답까지 5~10초 추가 지연이 발생해도 UX에 큰 영향이 없다.
이 여유를 활용해 "큐에 모아 한 번에 호출"하는 배치 구조로 비용/한도 문제를 함께 해결한다.

### 목표

동시에 들어오는 여러 음성 파싱 요청을, **1초 단위로 큐에 모았다가 최대 10건을 묶어 Gemini를 1회 호출**하고,
응답을 **요청별 id로 정확히 분배**하는 비동기 배치 처리 구조를 만든다.

### 비목표

- 분산 환경(여러 인스턴스) 간 배치 공유 — 단일 JVM 안에서만 모은다.
- 실패 재시도(retry) — 첫 호출이 실패하면 해당 요청은 그대로 실패 처리한다.
- 동적으로 배치 사이즈/주기를 조정 — 상수로 고정한다.

---

## 2. 아키텍처

### 컴포넌트 구성

```
[요청 스레드 A]                          ┌──────────────────────────────────┐
[요청 스레드 B]   ── submit(req) ──→     │  NavigationParseBatchProcessor   │
[요청 스레드 C]                          │  (@Component, singleton)         │
       ...                               │                                  │
                                         │  ├── queue (LinkedBlockingQueue, │
                                         │  │         capacity=10)          │
                                         │  └── pending (ConcurrentHashMap, │
                                         │              id → CF)            │
                                         └──────────────────────────────────┘
                                                       │
                              @Scheduled(fixedDelay=1s)│
                                                       ▼
                                          큐 drain → 묶음 1회 호출
                                                       │
                                                       ▼
                                          GeminiAudioParseClient
                                                .parseBatch(List<req>)
                                                       │
                                                       ▼
                                                Gemini API (1회)
                                                       │
                                                       ▼
                                          id별 Map<Long, Resp>
                                                       │
                                                       ▼
                                            pending.get(id).complete(resp)
                                                       │
                                                       ▼
                                          각 요청 스레드의 .get()이 풀림
```

### 호출 흐름

1. 컨트롤러가 음성 파일을 받아 `NavigationParseService.parse()` 호출
2. Service가 `AtomicLong`으로 **고유 id 발급** → `AudioParseRequest(id, bytes, mimeType)` 생성
3. Service가 `processor.submit(req)` 호출 → **빈 `CompletableFuture` 즉시 반환**
4. Service는 `future.get(30s)`로 결과를 기다림 (현재 스레드 블로킹)
5. 그 사이 `@Scheduled` 메서드가 1초 주기로 깨어남:
   - 큐를 비워 모은 요청들을 `GeminiAudioParseClient.parseBatch(List<req>)`로 한 번에 호출
   - 응답을 id별로 분리해 각 `CompletableFuture.complete()`
6. Service의 `.get()`이 풀리고 응답을 컨트롤러로 반환

---

## 3. 핵심 결정사항

### D1. 배치 포맷 — 멀티파트 + id별 JSON 배열 응답

Gemini의 `contents.parts`에 **`inline_data`(오디오)를 N개 + `text`(프롬프트) 1개**를 넣어 1회 요청한다.
프롬프트는 "입력 순서대로 id 0,1,2... 를 매기고 JSON 배열로 응답하라"고 강제한다.

**Why:** 음성 바이너리는 분할할 수 없으므로 멀티파트가 유일한 묶음 방식이다. id별 매핑은 응답을 다시 요청 스레드로 분배하기 위해 필요.

**검증 결과 (2026-07-01 실측):**
- 3개 음성 묶음 호출 → 모든 id 정확히 반환, 누락 0건
- 응답 시간: 3개 묶음 ~8초 (단일 호출 ~3초 대비 선형 증가 아님)
- 모델: `gemini-2.5-flash`에서 안정. `gemini-2.5-flash-lite`는 503/품질 불안정 → **운영 모델을 `gemini-2.5-flash`로 변경 필요**

### D2. 큐 사이즈 = 배치 사이즈 = 10

큐 capacity 10, 한 배치당 최대 10건으로 동일하게 둔다.

**Why:**
- 실측 결과 10건 동시 처리는 안정적
- 큐와 배치를 분리하면 "30명 받아두고 10명씩 나눠 호출" 같은 시나리오를 만들 수 있지만,
  현재 트래픽 가정이 그 정도가 아니라 **과한 복잡도**
- 큐가 가득 차면 즉시 `IllegalStateException`으로 거절 → 백프레셔 명확

**Trade-off:** 동시 11번째 요청부터는 즉시 실패한다. 트래픽이 늘면 큐 사이즈와 배치 사이즈를 분리해야 한다.

### D3. 스케줄 주기 — `fixedDelay = 1초`

이전 배치 호출이 끝난 뒤 1초 후 다음 배치를 시작한다.

**Why:**
- Gemini 호출 자체가 5~10초 걸리므로 `fixedRate`보다 `fixedDelay`가 자연스러움 (이전 호출이 끝나길 기다림)
- 1초는 "요청이 들어오자마자 다음 사이클에 처리"를 보장하는 최소 단위
- 더 짧게 두면 빈 큐를 자주 들여다보는 노이즈만 늘어남

### D4. 타임아웃 — 30초

`CompletableFuture.orTimeout(30s)`로 요청별 데드라인을 설정.

**Why:**
- 큐 대기 ~1초 + Gemini 호출(10건 묶음) ~10~20초 + 여유 = 30초
- 타임아웃 발생 시 **해당 요청만** 실패시키고, 같은 배치의 다른 요청에는 영향 없음
  (Gemini 호출이 실제로 완료되면 그 결과는 다른 요청들에는 정상 분배됨)

### D5. CompletableFuture 운용 — 같은 인스턴스에 직접 적용

`orTimeout`과 `whenComplete`을 **체이닝으로 새 CF를 만들지 않고**, 원본 CF에 직접 적용한다.

```java
CompletableFuture<NavigationParseResponse> future = new CompletableFuture<>();
pending.put(req.id(), future);
queue.offer(req);

future.orTimeout(30, TimeUnit.SECONDS);
future.whenComplete((res, ex) -> pending.remove(req.id()));
return future;  // 원본을 그대로 반환
```

**Why (블로그 글과의 차이):**
- 블로그 글처럼 `future.orTimeout(...).whenComplete(...)`를 체이닝해서 반환하면, 호출자가 받는 CF와 `pending`에 저장된 CF가 **다른 인스턴스**가 된다.
- JDK 구현상 `orTimeout`이 타임아웃을 원본에도 전파하긴 하지만, **의도가 코드에 드러나지 않아 리뷰어가 누수로 오해하기 쉽다.**
- 같은 CF에 직접 적용하면 "pending에 들어간 그 CF가 스스로 타임아웃 + 제거"라는 게 명확.

### D6. 결과 분배 — 스케줄러 스레드에서 직접 `complete`

`processBatch()`에서 응답을 받은 뒤, **스케줄러 스레드가 그대로** 각 CF에 `complete()`를 호출한다.

**Why:**
- 현재 `Service.process()`는 단순히 `.get()`만 호출하고 추가 콜백을 달지 않음 → 스케줄러 스레드를 막을 다운스트림 작업이 없음
- 별도 Executor를 두면 코드 복잡도가 늘어남
- **이 한계는 인지하고 명시한다:** 만약 향후 `submit()`이 반환한 CF에 `.thenApply(slowWork)` 같은 무거운 콜백을 다는 호출자가 생기면, 그 작업이 스케줄러 스레드를 점유해 다음 배치가 밀린다. 이 경우 별도 `Executor`로 빼야 함.

### D7. 기존 동기 호출 전면 교체

`GeminiAudioParseClient.parse(byte[], String)`는 삭제하고 `parseBatch(List<AudioParseRequest>)`로 대체한다.
플래그/이중 경로 유지 없음.

**Why:** Simplicity First. 동기 경로를 남겨두면 두 경로를 모두 테스트해야 하고 일관성이 떨어진다.

---

## 4. 데이터 구조

### 4.1. AudioParseRequest (신규)

```java
package org.example.when2go.domain.trip.dto;

public record AudioParseRequest(
        Long id,            // 요청별 고유 id (응답 매핑 키)
        byte[] audioBytes,
        String mimeType
) {}
```

위치: `domain/trip/dto/`

### 4.2. NavigationParseResponse (기존, 변경 없음)

```java
public record NavigationParseResponse(
        String startLocation,
        String endLocation,
        String appointmentTime
) {}
```

### 4.3. 내부 파싱용 BatchItem (GeminiAudioParseClient 내부)

```java
record BatchItem(
        @JsonProperty("id") Long id,
        @JsonProperty("start_location") String startLocation,
        @JsonProperty("end_location") String endLocation,
        @JsonProperty("appointment_time") String appointmentTime
) {}
```

Gemini가 돌려준 배열의 각 객체. 클라이언트 내부 record로 둔다.

---

## 5. 컴포넌트 명세

### 5.1. `NavigationParseBatchProcessor` (신규)

위치: `domain/trip/service/NavigationParseBatchProcessor.java`

```java
@Component
public class NavigationParseBatchProcessor {

    private static final int CAPACITY = 10;
    private static final int BATCH_MAX = 10;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final GeminiAudioParseClient client;
    private final BlockingQueue<AudioParseRequest> queue = new LinkedBlockingQueue<>(CAPACITY);
    private final ConcurrentHashMap<Long, CompletableFuture<NavigationParseResponse>> pending =
            new ConcurrentHashMap<>();

    public CompletableFuture<NavigationParseResponse> submit(AudioParseRequest req) { ... }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.SECONDS)
    public void processBatch() { ... }
}
```

**submit() 흐름:**
1. 빈 CF 생성 → `pending.put(id, future)`
2. `queue.offer(req)` 시도. 실패 시 `pending.remove` + `completeExceptionally(IllegalStateException)`
3. `future.orTimeout(30s)` + `future.whenComplete((r,e) → pending.remove(id))`
4. 원본 future 반환

**processBatch() 흐름:**
1. `queue.drainTo(batch, BATCH_MAX)`. 비어있으면 즉시 return
2. `client.parseBatch(batch)` 호출 → `Map<Long, NavigationParseResponse>` 받음
3. 배치의 각 요청에 대해:
   - `pending.get(id)`가 null이면(=타임아웃으로 이미 제거됨) `log.warn` + 다음 항목
   - 응답이 있으면 `f.complete(resp)`
   - 응답이 없으면(=id 누락) `f.completeExceptionally(new IllegalStateException("응답 누락: id=" + id))`
4. 호출 자체가 예외로 끝나면 배치의 모든 CF에 `completeExceptionally(e)`

### 5.2. `GeminiAudioParseClient` (수정)

기존 `parse(byte[], String) → NavigationParseResponse` 메서드 **삭제**하고
`parseBatch(List<AudioParseRequest>) → Map<Long, NavigationParseResponse>` **추가**.

**프롬프트 변경:**
- 멀티 오디오 + id별 JSON 배열 응답 강제
- 기존의 추출 규칙(start_location/end_location/appointment_time)은 동일하게 유지
- "id는 입력 순서대로 0부터 부여" 명시 (실제 id 값은 클라이언트가 받은 list 순서)

**요청 body 구성:**
```
contents: [{
  parts: [
    {inline_data: {mime_type, data: base64(req[0])}},
    {inline_data: {mime_type, data: base64(req[1])}},
    ...
    {text: PROMPT}
  ]
}]
```

**응답 파싱:**
- candidates[0].content.parts[0].text → JSON 배열 (size N)
- 각 항목의 `id` 필드로 다시 원래 요청 id에 매핑 (인덱스 0,1,2... → req[0].id, req[1].id, ...)
- 응답 배열이 입력 개수와 다르거나 id가 누락되면 **누락된 id는 Map에 안 넣음** (Processor가 처리)
- 응답 JSON의 문자열 `"null"` (`flash-lite`에서 관찰됨) → null로 정규화

### 5.3. `NavigationParseService` (수정)

```java
@Service
@RequiredArgsConstructor
public class NavigationParseService {

    private final NavigationParseBatchProcessor processor;
    private static final AtomicLong ID_GEN = new AtomicLong(0);

    public NavigationParseResponse parse(MultipartFile audio) {
        // 입력 검증, mimeType 추출은 기존 로직 유지
        long id = ID_GEN.incrementAndGet();
        AudioParseRequest req = new AudioParseRequest(id, audioBytes, mimeType);

        try {
            return processor.submit(req).get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new DomainException(NavigationParseErrorCode.PARSE_FAILED, cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DomainException(NavigationParseErrorCode.PARSE_FAILED, e);
        }
    }
}
```

`MIME_MAP`, `resolveMimeType()`, 입력 검증은 기존 그대로.

---

## 6. 실패 모드와 대응

| 시나리오 | 발생 위치 | 동작 |
|---|---|---|
| 큐 가득 참 (11번째 동시 요청) | `submit()` | 즉시 `IllegalStateException` → `PARSE_FAILED` |
| Gemini 호출 자체 실패 (네트워크/5xx) | `processBatch()` | 배치 전체 `completeExceptionally(e)` → 모두 `PARSE_FAILED` |
| Gemini 응답에서 일부 id 누락 | `processBatch()` | **누락된 요청만** 실패. 나머지는 정상 분배 |
| 응답 JSON 구조 파싱 실패 | `parseBatch()` 내부 | 배치 전체 실패 (호출 자체 실패와 동일 경로) |
| 30초 타임아웃 | `submit()`의 `orTimeout` | 해당 요청만 `TimeoutException` → `PARSE_FAILED`. Gemini 호출이 늦게 와도 다른 요청은 영향 없음 |
| 타임아웃 후 늦게 도착한 응답 | `processBatch()` | `pending.get(id)`가 null → `log.warn` 후 무시 |
| 동시 요청 중 일부 인터럽트 | `submit().get()` | 해당 스레드만 `InterruptedException` → `PARSE_FAILED` |

---

## 7. 검증 / 테스트 범위

### 7.1. `NavigationParseBatchProcessor` 단위 테스트

| 케이스 | 검증 내용 |
|---|---|
| `큐_가득_차면_즉시_실패` | 10개 채운 뒤 11번째 submit → 즉시 `IllegalStateException` |
| `정상_배치_id별_분배` | 3개 submit → 1초 후 각 CF가 자기 id 결과만 받음 |
| `일부_id_누락` | 3개 중 2개만 응답에 있음 → 누락 1개만 실패, 나머지는 성공 |
| `호출_전체_실패` | client 모킹이 예외 throw → 배치 전체 실패 |
| `타임아웃` | client 모킹이 31초 이상 블로킹 → 해당 CF가 30초 후 `TimeoutException` |
| `타임아웃_후_지연_응답_무시` | 타임아웃 후 늦게 응답 도착 → 예외 없이 무시되고 `pending` 비어있음 |

`client`는 Mockito로 mock. `@Scheduled`는 직접 호출 (스케줄러 의존 안 함).

### 7.2. `GeminiAudioParseClient` 단위 테스트 (갱신)

기존 `toResponse()` 테스트를 `parseBatchToResponse()` 형태로 갱신:

| 케이스 | 검증 내용 |
|---|---|
| `정상_배열_응답_파싱` | id 0,1,2 모두 있는 JSON → 3개 요소 Map 반환 |
| `null_문자열_정규화` | `"null"` 문자열 → null 변환 |
| `일부_id_누락` | 응답 배열에 id 1만 있음 → Map 크기 1 |
| `응답_본문_비어있음` | 빈 응답 → `DomainException` |

WebClient 호출은 `MockWebServer` 사용.

### 7.3. `NavigationParseService` 단위 테스트 (갱신)

`processor`를 mock해서 기존 입력 검증/mimeType 추출 로직만 검증. 배치 동작은 Processor 테스트에서 커버.

### 7.4. 비범위

- 실제 Gemini API 호출 통합 테스트는 수동 검증으로 갈음 (Task #1에서 이미 실측 완료, 비용/불안정성 이슈)
- 동시성(여러 스레드 동시 submit) 부하 테스트는 이번 범위 외

---

## 8. 변경되는 파일

| 파일 | 변경 |
|---|---|
| `domain/trip/dto/AudioParseRequest.java` | **신규** |
| `domain/trip/service/NavigationParseBatchProcessor.java` | **신규** |
| `domain/trip/client/GeminiAudioParseClient.java` | `parse()` 삭제, `parseBatch()` 추가, 프롬프트 교체 |
| `domain/trip/service/NavigationParseService.java` | `client` → `processor` 의존성 교체, id 발급 추가 |
| `src/main/resources/application.yml` | `gemini.model` 기본값 `gemini-2.5-flash-lite` → `gemini-2.5-flash` |
| `test/.../GeminiAudioParseClientTest.java` | 갱신 |
| `test/.../NavigationParseServiceTest.java` | 갱신 |
| `test/.../NavigationParseBatchProcessorTest.java` | **신규** |

`@EnableScheduling`은 `When2goApplication`에 이미 활성화되어 있음 (변경 불필요).

---

## 9. 트레이드오프와 알려진 한계

1. **단일 인스턴스 한정** — 여러 서버 인스턴스를 띄우면 각 인스턴스가 별도 큐를 가지므로 묶음 효율이 떨어진다. 분산 환경에서 진정한 묶기를 원하면 Redis 큐 등으로 옮겨야 한다.
2. **스케줄러 스레드 1개 공유** — 다른 `@Scheduled` 작업이 같은 풀을 쓰면 영향을 받는다. 필요 시 `TaskScheduler` 분리.
3. **재시도 없음** — 503 같은 일시적 장애에도 그대로 실패시킨다. 운영 도입 전엔 짧은 재시도 정책이 필요할 수 있다.
4. **id가 단조증가** — `AtomicLong`이라 long 한도 전엔 안전하지만, 재시작 시 0부터 다시 시작. 같은 JVM 안에서 충돌만 안 나면 OK.
5. **응답 품질은 모델 의존** — `gemini-2.5-flash-lite`는 묶음 응답에서 일부 필드를 잘못 파싱하는 사례 관찰됨. 운영 모델을 `gemini-2.5-flash`로 변경 결정.
