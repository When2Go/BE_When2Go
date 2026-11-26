# 알림 outbox 발송 가상 스레드(Virtual Thread) 전환 + 10만 건 실험

**작성일:** 2026-11-26
**이슈:** #93
**기준 브랜치:** `release` (#89 outbox 복원 반영 상태)

---

## 1. 배경

FCM 호출 1건은 수백 ms 동안 **응답을 기다리기만 하는 블로킹 I/O**다.
"출퇴근 시간대처럼 알림 10만 건을 한 번에 보내야 한다면?"이라는 질문에서 출발했다.

비동기로 처리해도 결국 스레드 10만 개가 블로킹으로 대기해야 한다. 논블로킹(WebFlux/Reactor)으로 다시 짜면 해결되지만, FCM 호출부터 상태 반영까지 리액티브 체인으로 바꿔야 한다.
가상 스레드는 **블로킹 코드를 그대로 둔 채** 대기 중인 스레드가 OS 스레드를 붙잡지 않게 해 준다. 이 차이를 실제 발송 경로에서 측정했다.

## 2. 기존 구조의 한계

```
NotificationOutboxCreatedEventListener / RepublishRequestedEventListener
  @Async(notificationOutboxPublisherExecutor: 플랫폼 스레드 1개)
  └─ NotificationOutboxPublishService.publishPendingOutboxes()
       1) claim 500건 (FOR UPDATE SKIP LOCKED → PROCESSING)
       2) for each outbox → pushClient.send() → markSent   ← 순차
```

| 한계 | 결과 (FCM 200ms 가정) |
|---|---|
| 1배치 500건을 순차 발송 | 1배치 ≈ 100초, 처리량 5건/s |
| 10만 건 | 200배치 × 104초 ≈ **5.8시간** |
| 동시성을 올리려면 | 플랫폼 스레드를 늘려야 함. 플랫폼 스레드는 OS 스레드와 1:1이라 비용·개수 한도가 있음 |

## 3. 변경한 설계

```
publishPendingOutboxes()
  1) claim 500건                         ── 변경 없음
  2) outbox마다 가상 스레드 1개로 sendOne  ── 발송 + markSent (블로킹 코드 그대로)
  3) 전부 끝날 때까지 대기 (allOf.join)
```

- `NotificationAsyncConfig`: `notificationPushExecutor` 빈 추가. `SimpleAsyncTaskExecutor#setVirtualThreads(true)`로 작업마다 가상 스레드를 만든다.
- `NotificationOutboxPublishService`: `for` 루프를 `CompletableFuture.runAsync(..., notificationPushExecutor)` 팬아웃으로 바꿨다. `sendOne()`은 손대지 않았다.
- **전부 끝날 때까지 기다리는 이유:** 기존과 같이 "메서드가 반환되면 claim한 배치 처리가 끝났다"는 의미를 유지한다. publisher 실행기(스레드 1개)가 다음 배치를 겹쳐 claim하지 않는다.
- 실패/예외 처리는 기존 그대로다. `sendOne()`이 예외를 잡으므로 한 건의 실패가 다른 건에 영향을 주지 않는다. 실패 건은 PROCESSING으로 남아 recovery 대상이 된다.
- 동시 실행 수의 상한은 `notification.outbox.claim-size`(기본 500)다. 설정값은 바꾸지 않았다.

## 4. 실험 환경

- Apple M4 (10코어), 16GB, macOS, JDK 21.0.7, Spring Boot 4.0.6
- FCM 호출 지연: 200ms로 가정 (`Thread.sleep`)
- 측정 코드 (`VTHREAD_EXPERIMENT=true`일 때만 실행, 일반 `./gradlew test`에서는 skip)
  - `PushExecutorComparisonIT`: 실행기만 비교 (DB 없음)
  - `NotificationOutboxPublishVirtualThreadIT`: 실제 `publishPendingOutboxes()` + H2(MySQL 모드)

## 5. 결과

### 실험 1 — 실행기만 비교 (작업 = `Thread.sleep(200ms)`, 10만 건)

| 실행기 | 소요 시간 | 처리량 | 동시 실행 최대 | 플랫폼 스레드 최대 |
|---|---:|---:|---:|---:|
| 플랫폼 풀 2개 | 약 10,186s (500건 측정 후 환산) | 10/s | 2 | 14 |
| 플랫폼 풀 200개 | 101.7s | 983/s | 200 | 212 |
| 작업마다 플랫폼 스레드 | 8.3s | 12,034/s | 3,864 | **3,892** |
| **작업마다 가상 스레드** | **0.63s** | **158,479/s** | **100,000** | **23** |

가상 스레드 10만 개가 동시에 대기하는 동안 플랫폼 스레드는 23개였다(JVM 기본 스레드 + 캐리어 10개).

### 실험 1-b — 실제 소켓 I/O (로컬 HTTP 서버, 응답 200ms 지연, 1만 건)

`sleep`이 아닌 실제 소켓 블로킹에서도 같은지 확인하려고 `HttpURLConnection`으로 호출했다.

| 실행기 | 소요 시간 | 동시 실행 최대 | 플랫폼 스레드 최대 |
|---|---:|---:|---:|
| 플랫폼 풀 200개 | 10.5s | 200 | 224 |
| 가상 스레드 | 2.3s ~ 7.9s (반복마다 다름) | 6,431 ~ 10,000 | **28 ~ 48** |

- 소켓 read 수천 건을 동시에 기다리는 동안에도 플랫폼 스레드는 수십 개를 넘지 않았다.
- 가상 스레드 쪽은 일부 회차에서 실패가 났다(`BindException: Can't assign requested address`, `ConnectException: Operation timed out`, 1만 건 중 최대 약 1,100건). 한 머신에서 TCP 연결을 수천 개 동시에 열어 **로컬 OS 소켓 자원**(ephemeral port, `somaxconn` 128)이 바닥난 것이다. 가상 스레드 문제가 아니며, 이번 범위에서 튜닝하지 않았다.
- 참고: 실제 FCM 경로는 이와 다르다. firebase-admin 9.x는 Apache HttpClient 5 기반 HTTP/2(`ApacheHttp2Transport`)를 쓰고, 소수의 HTTP/2 연결을 여러 요청이 나눠 쓴다.

### 실험 2 — 실제 outbox 발송 경로로 10만 건 (H2, 가짜 FCM 200ms)

PENDING outbox 10만 건을 넣고, PENDING이 없어질 때까지 `publishPendingOutboxes()`를 반복 호출했다.
claim(FOR UPDATE SKIP LOCKED) → 발송 → `markSent` 트랜잭션은 운영 코드 그대로다. 발송 실행기와 claim 크기만 바꿨다.

| 실행기 | claim 크기 | 배치 수 | 10만 건 소요 | 처리량 | 동시 발송 최대 | 플랫폼 스레드 최대 |
|---|---:|---:|---:|---:|---:|---:|
| 순차 (기존) | 500 | 1 (측정) | 약 20,798s ≈ **5.8시간** (환산) | 5/s | 1 | 17 |
| 플랫폼 풀 200개 | 500 | 200 | 139.5s | 717/s | 200 | 216 |
| **가상 스레드 (운영 설정)** | **500** | 200 | **56.0s** | **1,786/s** | 500 | **27** |
| 플랫폼 풀 200개 | 5,000 | 20 | 110.8s | 902/s | 200 | 227 |
| 가상 스레드 | 5,000 | 20 | **12.5s** | **7,973/s** | 5,000 | **27** |

## 6. 해석

**운영 설정(claim 500) 그대로 5.8시간 → 56초**
- 기존: 배치 1개 = 500 × 200ms ≈ 100초.
- 변경 후: 배치 1개 ≈ 280ms. FCM 대기(200ms)를 500건이 한 번에 기다리고, 나머지 약 80ms는 claim과 `markSent`다.
- 플랫폼 스레드는 27개로, 기존(17개)과 큰 차이가 없다.

**같은 동시성을 플랫폼 풀로 내면?**
- 풀 200개는 500건을 200 → 200 → 100 세 번에 나눠 보내 배치당 약 700ms가 걸린다.
- claim을 5,000으로 올려도 동시성이 200에 묶여 있어 거의 빨라지지 않는다(139.5s → 110.8s).
- 가상 스레드는 claim 크기가 곧 동시성이라 claim 5,000에서 12.5초까지 줄었다. **스레드 수를 늘리지 않고 동시성만 올라간다.**

**병목이 FCM 대기에서 DB로 옮겨 간다**
- claim 5,000 가상 스레드: 배치당 약 627ms 중 FCM 대기는 200ms뿐이다. 나머지는 claim 5,000건과 건별 `markSent` 트랜잭션 5,000개(커넥션 풀 기본 10개를 나눠 씀)다.
- 즉 가상 스레드로 I/O 대기는 사라졌고, 이제 DB 처리가 전체 시간을 결정한다.

**왜 이런 차이가 나는가**
- 플랫폼 스레드는 블로킹되는 동안 OS 스레드를 그대로 붙잡는다. 동시성이 곧 스레드 수이고, 스레드 수에는 비용과 한도가 있다.
- 가상 스레드는 블로킹 지점(`sleep`, 소켓 read 등)에서 캐리어 스레드(ForkJoinPool, 코어 수만큼)에서 내려간다(unmount). 대기 중에는 힙에 있는 작은 스택 조각일 뿐이다.

**"작업마다 플랫폼 스레드"는 왜 안 되는가 (실험 1)**
- 동시 실행 3,864개로 macOS 프로세스당 스레드 한도(4,096) 바로 아래까지 갔다.
- OS 스레드 생성 비용 때문에 처리량이 가상 스레드의 1/13 수준(12k/s)에 그쳤다.

**코드를 거의 그대로 두고 효과를 얻었다**
- 바뀐 것은 실행기 빈 1개와 발송 루프 5줄이다.
- `pushClient.send()`와 `markSent()`는 지금도 평범한 블로킹 호출이다.

**메모리**
- 가상 스레드 10만 개를 쓸 때(실험 1) 힙 사용이 최대 약 170~180MB 늘었다. 가상 스레드 스택이 힙에 저장되기 때문이다.

## 7. 남은 과제 (이번 범위 밖)

- **하위 시스템 보호:** 가상 스레드는 스레드 수로 생기던 동시성 상한을 없앤다. 지금은 claim 크기가 상한 역할을 한다. claim 크기를 올리려면 FCM 쿼터에 맞춘 동시 실행 제한(세마포어 등)을 함께 정해야 한다.
- **DB 부하:** 동시성이 올라가면 건별 `markSent` 트랜잭션이 커넥션 풀을 두고 경쟁한다. 성공 id를 모아 일괄 UPDATE하는 방식을 고려할 수 있다. 다만 발송 후 반영 전 장애 시 재발송 범위가 넓어지는 트레이드오프가 있다.
- **JDK 버전:** JDK 21에서는 `synchronized` 블록 안에서 블로킹하면 캐리어 스레드가 묶이는 pinning이 생긴다(JDK 24, JEP 491에서 해소). 실제 FCM SDK 경로에서 pinning이 생기는지는 `-Djdk.tracePinnedThreads`로 확인해야 한다.
- **트리거 주기:** 실험은 `publishPendingOutboxes()`를 연속 호출했다. 운영에서는 outbox 생성/복구 이벤트마다 1배치씩 호출되므로 실제 소요 시간은 이벤트 발생 주기에도 좌우된다.

## 8. 재현

```bash
VTHREAD_EXPERIMENT=true ./gradlew test --tests "*PushExecutorComparisonIT"
VTHREAD_EXPERIMENT=true ./gradlew test --tests "*NotificationOutboxPublishVirtualThreadIT"
# 결과: build/test-results/test/TEST-*.xml 의 [VTHREAD] / [VTHREAD-OUTBOX] 로그
```
