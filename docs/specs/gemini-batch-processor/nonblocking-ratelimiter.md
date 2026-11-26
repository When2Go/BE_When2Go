# Gemini 배치 호출 논블로킹 전환 + Resilience4j RateLimiter

**작성일:** 2026-11-26
**이슈:** #91
**기준 브랜치:** `release` (#88 배치 처리, #90 DeferredResult 반영 상태)

---

## 1. 기존 구조의 병목

| 문제 | 원인 | 영향 |
|---|---|---|
| 배치가 순차 실행 | `processBatch()` 안에서 `WebClient...block()` | 호출 간격 = Gemini 응답시간 + 1s. 15s 응답이면 16s마다 1회 → 이론 처리량 10건/16s ≈ 37.5건/분 |
| 스케줄러 스레드 점유 | Spring Boot 기본 스케줄러 풀 = 1 스레드 | 블로킹 동안 `NotificationScheduler`, `TripRecalcScheduler`, `ReservationTripScheduler`도 밀림 |
| HTTP Timeout 없음 | `block()`에 제한 없음, WebClient에도 없음 | Gemini가 응답하지 않으면 스케줄러 스레드가 무기한 정지 → 모든 `@Scheduled` 정지 |
| RPM 보장 없음 | `fixedDelay`는 실행 간격이지 호출량 제한이 아님 | 응답이 빨라지면(예: 2s) 분당 20회 호출 → RPM 15 초과 |

기존 "분당 최대 150건"은 RPM 15 × 배치 10의 **이론 상한**이며, 기존 구현이 달성한 지속 처리량이 아니다.

## 2. 변경 후 흐름

```
[Tomcat 스레드]  submit(req) → queue.offer (가득 차면 즉시 503) → DeferredResult 반환, 스레드 반납
                                     │
[scheduling-1, 1s fixedDelay]        ▼
   1) queue.isEmpty()           → 비었으면 반환 (허가권 소비 X)
   2) rateLimiter.acquirePermission() (timeout 0) → 실패면 반환 (큐 그대로)
   3) queue.drainTo(≤10) → 완료(Timeout/취소)된 요청 제외
   4) Mono.fromCallable(Base64+JSON 직렬화)   ← 이 스레드에서 실행
        .flatMap(webClient.post()...timeout(25s))
        .subscribe(onNext, onError)           ← 등록만 하고 즉시 반환
                                     │
[reactor-http-nio (Event Loop)]      ▼  응답 도착
   toResponseMap(소형 JSON 파싱) → id별 future.complete / completeExceptionally
     → NavigationParseService.whenComplete → DeferredResult.setResult → Tomcat에 dispatch
```

### 스레드별 역할

| 스레드 | 하는 일 | 하지 않는 일 |
|---|---|---|
| Tomcat 워커 | 파일 검증, id 발급, `submit`, DeferredResult 반환 | Gemini 대기 (#90에서 분리) |
| `scheduling-1` | 큐 확인, 허가 획득, drain, 요청 바디 직렬화, 구독 | HTTP 응답 대기 |
| Reactor Netty Event Loop | 요청 바이트 전송, 응답 수신, 응답 JSON 파싱(수 KB), Future 완료 | 블로킹 I/O, 수 MB 직렬화 |
| `CompletableFutureDelayScheduler` | `orTimeout(30s)` 만료 시 Future 예외 완료 | - |
| Reactor `parallel` | `Mono.timeout(25s)` 만료 시 구독 취소 → HTTP 연결 정리 | - |

### 책임 분리

- **스케줄링(언제 확인할지)**: `@Scheduled(fixedDelay=1s)`
- **호출 정책(얼마나 호출할지)**: Resilience4j `RateLimiter` (`limitForPeriod=1`, `limitRefreshPeriod=4s`, `timeoutDuration=0`)
- **호출 수명(얼마나 기다릴지)**: `Mono.timeout(25s)`

## 3. 설계 결정

### 허가 획득 → drain 순서

허가 없이 미리 꺼내 두면 큐 용량(10)이 사실상 늘어나 Bounded Queue의 Backpressure(가득 차면 503)가 무력화된다.
허가를 먼저 얻고 꺼내므로, 허가를 못 받는 동안 요청은 큐에 남아 용량을 차지하고 초과 유입은 즉시 거절된다.

### 허가권 낭비 방지

- 빈 큐: `isEmpty()` 선확인으로 허가권을 쓰지 않는다.
- 만료 요청: Future가 예외 완료(Timeout/취소)되면 `whenComplete`에서 `queue.remove(req)` → 큐에 남지 않는다.
- 남는 경합: `isEmpty()` 확인 ~ drain 사이에 남은 요청이 전부 만료되면 허가권 1개가 소비된다(빈 배치는 호출하지 않음). 큐 대기 상한(~5s)이 Future Timeout(30s)보다 훨씬 짧아 실제로는 거의 발생하지 않는다.

### HTTP Timeout = 25s

- 큐 용량(10) = 배치 최대(10)이므로 큐에 있는 요청은 다음 허가 시점에 모두 나간다 → 큐 대기 상한 ≈ 허가 주기 4s + tick 1s = 5s
- Future Timeout 30s − 큐 대기 5s = **25s**. HTTP 호출이 Future보다 먼저 끝나도록 해, Future만 끝나고 HTTP는 계속 도는 상황을 막는다.
- HTTP Timeout은 `TimeoutException`으로 전달되고, 기존 Service 매핑에 의해 `PARSE_TIMEOUT(504)`이 된다.
- 공유 `geminiWebClient` 빈(근처 추천에서도 사용)은 건드리지 않고 이 호출의 Mono에만 적용했다.

### Semaphore를 도입하지 않은 근거

호출 시작은 RateLimiter가 주기당 1회로 묶고, 호출 수명은 25s로 묶는다.
따라서 동시 진행 HTTP 호출 ≤ ⌈25 / 4⌉ + 1 ≈ 8, pending Future ≤ 8 × 10 + 큐 10 = 90건으로 상한이 생긴다.
메모리(배치당 오디오 ≤ 10건)와 Reactor Netty 커넥션 풀(기본 수십 개) 모두 이 범위에서 여유가 있어 별도 동시 실행 제한은 불필요하다.
RateLimiter 주기를 줄이거나 Timeout을 늘리면 이 상한도 같이 커지므로 함께 조정해야 한다.

### RateLimiter 경계 문제 (Fixed Window)

Resilience4j `AtomicRateLimiter`는 고정 주기(cycle)마다 허가권을 리필한다. 주기 N의 끝과 N+1의 시작에서 허가를 받으면 두 호출이 4s보다 가깝게 붙는다(스케줄러 tick이 1s라 최소 ≈1s).

- 임의 60s 구간이 걸치는 주기 수는 최대 `floor(60/T) + 1` (60/T가 정수면 `60/T + 1`)
- T = 4s → 최악 **16회/슬라이딩 60s** (RPM 15 초과 가능), 분 단위 고정 창 기준으로도 경계 정렬에 따라 16회 가능
- 슬라이딩 60s 15회 이하를 엄밀히 보장하려면 T > 4s 필요 (예: 4.1s → 최악 15회, 4.5s → 최악 14회)
- 기본값은 요구사항대로 4s로 두고, `GEMINI_AUDIO_PARSE_LIMIT_REFRESH_PERIOD` 환경변수로 조정 가능하게 했다. Gemini 측 RPM 집계 방식(슬라이딩/고정 창)이 확인되지 않았고, 429는 배치 단위 502로 이어지므로 운영에서는 4.5s 정도의 보수적 값을 권장한다.

### 완료 경합

- `CompletableFuture`의 `complete`/`completeExceptionally`는 CAS로 **최초 1회만 반영**된다. 응답(Event Loop)과 `orTimeout`(타이머 스레드)이 동시에 시도해도 하나만 이기고, `whenComplete` 콜백(=DeferredResult 세팅, pending 정리)도 1회만 실행된다.
- 응답 처리 시 `pending.get(id)`가 null(이미 Timeout으로 제거)이면 응답을 버린다.
- 배치 단위로 `batch` 리스트를 콜백 클로저에 묶어 두므로, 여러 배치의 응답 순서가 뒤바뀌어도 각 응답은 자기 배치의 id로만 분배된다.

## 4. 테스트

| 항목 | 테스트 |
|---|---|
| 허가 성공 → 배치 실행 | `허가를_얻으면_배치를_호출하고_id별로_결과를_분배한다` |
| 허가 실패 → 큐에서 꺼내지 않음 | `허가를_얻지_못하면_큐에서_꺼내지_않고_호출하지_않는다` |
| 빈 큐 → 호출/허가 소비 없음 | `빈_큐에서는_허가권을_소비하지_않고_호출하지_않는다` |
| 큐 포화 → 거절 정책 유지 | `큐가_가득_차면_11번째_요청은_즉시_실패한다`, `허가가_없어_큐가_차_있으면_새_요청은_거절된다` |
| 응답 지연 → 스케줄러 비대기 | `Gemini_응답이_지연돼도_스케줄러는_응답을_기다리지_않고_반환한다` (`Mono.never()` + 1s preemptive timeout) |
| 동시 진행 | `이전_배치_응답_전에_다음_배치가_호출되어_동시에_진행된다`, `두_배치의_HTTP_호출이_동시에_진행될_수_있다`(실제 HTTP) |
| 응답 순서 역전 | `배치_응답_순서가_역전돼도_올바른_Future에_전달된다` |
| API 실패 | `Gemini_호출이_실패하면_배치_전체가_예외_완료되고_pending이_정리된다`, `서버_5xx면_PARSE_FAILED_DomainException으로_끝난다` |
| HTTP Timeout | `HTTP_Timeout을_넘기면_TimeoutException으로_끝나고_진행중인_HTTP_교환이_취소된다`(실제 HTTP, 교환 cancel 확인), `HTTP_Timeout이면_배치_Future가_TimeoutException으로_완료되고_정리된다`, `HTTP_Timeout은_PARSE_TIMEOUT_DomainException으로_변환된다` |
| 만료 요청 제외 | `이미_만료된_요청은_큐에서_빠지고_Gemini_호출_대상에서_제외된다`, `만료된_요청만_남으면_허가권을_소비하지_않고_호출하지_않는다`, `만료된_요청이_빠지면_큐_용량이_반환된다` |
| pending 정리 | 성공/실패/Timeout/취소 각 테스트에서 `pendingCount()==0` 확인 |
| 응답/Timeout 경합 | `응답_대기_중_Timeout된_요청은_뒤늦은_응답으로_덮어쓰지_않는다`, `정상_응답과_Timeout이_동시에_도착해도_Future는_한_번만_완료된다`(200회 반복, 2스레드 동시 시작) |

응답 시점은 `Sinks.One`으로 직접 제어해 `Thread.sleep` 없이 검증한다. HTTP 경로는 JDK 내장 `HttpServer`를 Mock Gemini로 사용한다.

## 5. 성능 측정

### 조건

- 하네스: `NavigationParseBatchLoadTest` (`WHEN2GO_PERF=true`일 때만 실행)
- Mock Gemini: JDK `HttpServer`, 지정 지연 후 요청 개수만큼 결과 배열 응답
- 실제 `WebClient`(Reactor Netty) 사용, 스케줄러는 Spring 기본과 동일한 단일 스레드 `fixedDelay(1s)`
- 유입: 고정 2건/s(120건/분), 120초. 이후 잔여 요청이 끝날 때까지 대기
- 기존: `origin/release`(616b25f)를 worktree로 체크아웃해 동일 하네스(생성자만 기존 시그니처)로 측정
- 개선: RateLimiter 1/4s, HTTP Timeout 25s
- 두 구조를 같은 머신에서 동시에 실행(Mock 지연이 지배적이라 CPU 간섭은 미미)
- "분당 사용자 처리량"은 측정구간(120s) 안에 성공 완료된 건수 기준. 구간 끝에 진행 중이던 요청은 빠진다.

### 결과

| 지표 | 5s 기존 | 5s 개선 | 10s 기존 | 10s 개선 | 15s 기존 | 15s 개선 |
|---|---|---|---|---|---|---|
| 유입 / 성공 | 241 / 200 | 240 / **240** | 240 / 112 | 240 / **240** | 241 / 67 | 240 / **240** |
| 거절(503) | 41 | **0** | 128 | **0** | 158 | **0** |
| Timeout(504) | 0 | 0 | 0 | 0 | 16 | **0** |
| Gemini 호출 분당 | 10.0 | 15.0 | 5.5 | 15.0 | 4.0 | 15.0 |
| 슬라이딩 60s 최대 호출 | 10 | **16** | 6 | **16** | 4 | **16** |
| 최소 호출 간격 | 6008ms | 2853ms | 11009ms | 2994ms | 16010ms | 3002ms |
| 사용자 처리량(건/분) | 91.0 | 113.0 | 46.0 | 109.0 | 25.5 | 105.0 |
| 동시 진행 HTTP 최대 | 1 | 2 | 1 | 3 | 1 | 4 |
| Scheduler 실행시간 avg | 5055ms | 5.6ms* | 10013ms | 0.3ms | 15015ms | 0.3ms |
| 큐 대기 p50 / p95 | 3574 / 5783ms | 1970 / 3774ms | 8245 / 10658ms | 2004 / 3821ms | 12680 / 14641ms | 1979 / 3824ms |
| 전체 응답 p50 / p95 | 8581 / 10800ms | 6986 / 8828ms | 18247 / 20666ms | 12017 / 13837ms | 27693 / 29648ms | 16987 / 18832ms |

\* 첫 호출 1회가 636ms(WebClient/Netty 초기화). p95는 3ms.

### 해석

- 기존 구조의 호출 간격은 정확히 `응답시간 + 1s`(6.0/11.0/16.0s)였다. 호출 횟수가 응답시간에 반비례하고, 큐(10)가 금방 차서 유입의 17~66%가 거절됐다. 15s에서는 큐 대기(최대 14.7s) + 응답 15s가 Future Timeout 30s에 걸려 504도 발생했다.
- 개선 구조는 응답시간과 무관하게 호출이 RateLimiter 주기(분당 15회)로 고정되고, 응답시간만큼 HTTP 호출이 겹쳐 진행된다(동시 2~4개). 120건/분 유입을 거절·Timeout 없이 모두 처리했다.
- 큐 대기 상한은 응답시간과 무관하게 ≈4s로, HTTP Timeout 25s 산정 근거(큐 대기 ≤ 5s)와 일치한다.
- **슬라이딩 60s 최대 16회, 최소 간격 ~2.9s** — 4s 고정 주기의 경계 효과가 실측으로 확인됐다. 엄격한 RPM 15가 필요하면 주기를 늘려야 한다(아래 4.5s 측정).

### 보수적 설정 비교 (RateLimiter 주기 4.5s, 응답 5s, 180s)

| 지표 | 4s (기본) | 4.5s |
|---|---|---|
| 유입 / 성공 / 거절 | 240 / 240 / 0 | 360 / 360 / 0 |
| Gemini 호출 분당 | 15.0 | 13.3 |
| 슬라이딩 60s 최대 호출 | 16 | **14** |
| 최소 호출 간격 | 2853ms | 2837ms |
| 큐 대기 p95 / max | 3774 / 4008ms | 4520 / 5018ms |

- 4.5s는 슬라이딩 60s 기준 RPM 15를 지켰다(계산값 `floor(60/4.5)+1 = 14`와 일치). 이론 처리량은 133건/분으로 줄지만 이번 유입(120건/분)은 모두 처리했다.
- 최소 호출 간격은 주기와 무관하게 ~2.8s로, 고정 주기 방식에서는 "최소 간격"이 보장되지 않는다는 점은 그대로다.
- 주기를 늘리면 큐 대기 상한도 `주기 + 1s`로 늘어난다. 4.5s면 HTTP Timeout은 30 − 5.5 = 24.5s 이하가 맞으므로, 주기를 바꿀 때 `GEMINI_AUDIO_PARSE_HTTP_TIMEOUT`(예: 24s)도 함께 조정한다.

## 6. 남은 문제

- **RPM 경계**: 기본 4s는 슬라이딩 60s 최대 16회(실측). Gemini의 집계 방식 확인 후 4.5s 등으로 조정 필요.
- **취소 전파 없음**: 배치 내 모든 Future가 먼저 끝나도(클라이언트 이탈 등) HTTP 호출은 25s Timeout까지 유지된다. 현재 수치에서는 큐 대기가 짧아 거의 발생하지 않으며, 상한은 Timeout으로 묶인다.
- **클라이언트 이탈 미감지**: DeferredResult의 연결 종료를 Future 취소로 연결하지 않았다. 처리 비용은 Timeout으로 제한된다.
- **단일 인스턴스 기준**: RateLimiter·큐가 JVM 로컬이므로 인스턴스가 늘면 RPM도 배수로 늘어난다(분산 Rate Limiter는 범위 외).
- **429 처리**: Gemini 429도 다른 4xx/5xx처럼 배치 전체 `PARSE_FAILED(502)`. Retry/Circuit Breaker는 범위 외.
- **Event Loop 로그**: 실패 시 `log.error`가 Event Loop에서 실행된다. 콘솔 appender가 동기 I/O라 대량 실패 시 지연 요인이 될 수 있다(현재 수준에선 미미).
