# when2go (지금 나가?)

> 도착 목표 시간과 실시간 대중교통 상황을 기반으로 **"언제 출발해야 하는지"** 를 계산해 단계별 푸시 알림으로 안내하는 서비스의 백엔드 서버.


---

## 🏗️ 아키텍처

<p align="center">
  <img alt="when2go 아키텍처 다이어그램" src="https://github.com/user-attachments/assets/fdef2c16-aaf8-4da9-853d-23b48312eb8b" />
</p>

---

## 🚀 주요 기술적 도전 (Challenges)

### 1. SKIP LOCKED 동시성 제어 — 멀티 워커 알림 중복 발송 0건 + 처리 시간 32% 단축

**문제 정의**

Outbox 패턴 기반 알림 발송의 동시성 안전성을 검증하기 위해, 워커 수를 의도적으로 늘린 극한 조건(**워커 15개 · outbox 5,000 row**)으로 부하 테스트를 진행했습니다. 그 결과 폴링 시점이 겹친 워커들이 동일 outbox row 를 중복 조회해, 5,000 건의 알림이 총 **17,640건 발송(약 12,640건 중복)** 되는 문제를 확인했습니다.


**해결 방법**

- **✔️ `SELECT ... FOR UPDATE` 로 중복 발송 제거**
  outbox 조회 쿼리에 `FOR UPDATE` 를 적용해, 워커가 잡은 row 에 배타 락을 걸고 상태 변경 후 커밋 시점에 해제하도록 했습니다. 동일 부하 테스트에서 중복 발송이 **0 건**으로 줄었지만, 뒤 워커들이 락 해제까지 대기하는 구조라 워커를 늘려도 처리량이 늘지 않는 문제가 남았습니다.

- **✔️ `SKIP LOCKED` 추가로 워커 확장성 확보**
  `SKIP LOCKED` 를 추가해 락이 잡힌 row 를 대기 없이 건너뛰고 다음 row 를 가져가도록 변경했습니다. 워커들이 서로 다른 row 를 병렬로 나눠 갖는 구조가 되어, **정확성(중복 0건)과 처리량**을 동시에 확보했습니다.

**개선 효과 (워커 15개 · outbox 5,000 row)**

  | 지표 | Before (락 없음) | `FOR UPDATE` | `FOR UPDATE SKIP LOCKED` |
  | --- | ---: | ---: | ---: |
  | 실제 발송 건수 | 17,640건 | 5,000건 | 5,000건 |
  | 중복 발송 | **약 12,640건** | **0건** | **0건** |
  | 처리 시간 | — | 2,223 ms | **1,505 ms (-32%)** |



### 2. DeferredResult 도입으로 톰캣 스레드 격리

- **문제:** 배치 도입 후에도 컨트롤러가 `CompletableFuture.get()` 으로 결과를 블로킹 대기하면서, 요청당 톰캣 요청 스레드가 최대 **~10초** 잡힘. 톰캣 기본 스레드 풀(200)이 순식간에 고갈되어 파싱과 무관한 API 요청까지 대기.
- **해결:** 파싱 컨트롤러 반환 타입을 `DeferredResult<ApiResponse<...>>` 로 전환. 요청 스레드는 `DeferredResult` 만 넘기고 즉시 풀로 복귀하고, 배치 스케줄러가 결과를 만든 시점에 `whenComplete` 콜백에서 `deferred.setResult(...)` 를 호출.
- **성과 (동일 10건 동시 요청 기준):**

  | 지표 | Before (`.get()` 블로킹) | After (`DeferredResult`) | 개선 |
  | --- | ---: | ---: | ---: |
  | 요청당 톰캣 스레드 점유 시간 | ~10초 | 수 ms | **1000배+** |
  | 톰캣 활성 스레드 피크 (`tomcat.threads.busy`) | 200 근접 (풀 고갈) | 10 이하 | — |
  | 파싱 배치 지연이 다른 API에 미치는 영향 | 응답 지연 · 스레드 풀 고갈 | 격리됨 | — |




---

## 🛠️ 기술 스택

### Backend
![Java](https://img.shields.io/badge/Java-21-007396?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-6DB33F?style=flat&logo=springboot&logoColor=white)
![Spring Data JPA](https://img.shields.io/badge/Spring%20Data%20JPA-59666C?style=flat)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=flat&logo=gradle&logoColor=white)

### Database / Messaging
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat&logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=flat&logo=redis&logoColor=white)

### External API
![Google Routes](https://img.shields.io/badge/Google%20Routes%20API-4285F4?style=flat&logo=googlemaps&logoColor=white)
![Google Gemini](https://img.shields.io/badge/Google%20Gemini-8E75B2?style=flat&logo=google&logoColor=white)
![Firebase Cloud Messaging](https://img.shields.io/badge/FCM-FFCA28?style=flat&logo=firebase&logoColor=black)

### Observability
![Micrometer](https://img.shields.io/badge/Micrometer-117A65?style=flat)
![Prometheus](https://img.shields.io/badge/Prometheus-E6522C?style=flat&logo=prometheus&logoColor=white)
![Grafana](https://img.shields.io/badge/Grafana-F46800?style=flat&logo=grafana&logoColor=white)


### API Docs
![springdoc-openapi](https://img.shields.io/badge/springdoc--openapi-Swagger%20UI-85EA2D?style=flat&logo=swagger&logoColor=black)

---

## 📁 디렉토리 구조

```
src/main/java/org/example/when2go
├── When2goApplication.java
├── domain
│   ├── user           # 디바이스 사용자
│   ├── route          # Google Routes 경로 탐색
│   ├── trip           # 트립 CRUD + 다단계 재계산 + 자연어 파싱 (Gemini 배치)
│   ├── reservation    # 반복 예약(즐겨찾기)
│   ├── notification   # 알림 스케줄 / 아웃박스 / FCM
│   └── test           # 데모/부하 테스트용 컨트롤러
└── global
    ├── common         # BaseEntity 등 공통
    ├── config         # 프로파일/외부 API 설정
    ├── error          # 전역 예외 처리
    ├── firebase       # FCM 어댑터
    ├── response       # 공통 응답 래퍼 (ApiResponse)
    └── scheduler      # 트립 재계산 / 반복 예약 / 알림 스케줄러
docs/
├── portfolio/         # 기술적 도전 상세 설계 문서
├── features/          # 기능별 설계 문서
└── ADR/               # 아키텍처 결정 기록
docker/                # WireMock 매핑, k6 스크립트
grafana/               # 대시보드 프로비저닝
scripts/git-hooks/     # Spotless / 시크릿 스캔 / 테스트 훅
```

---

