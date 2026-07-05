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

- **문제:** 워커 15개 · outbox 5,000 row 부하 테스트에서 폴링이 겹친 워커들이 동일 row 를 중복 조회, **17,640건 발송(약 12,640건 중복)** 발생.
- **해결:** outbox 조회 쿼리에 `SELECT ... FOR UPDATE SKIP LOCKED` 적용. `FOR UPDATE` 로 중복을 막고, `SKIP LOCKED` 로 락 걸린 row 는 건너뛰어 워커 간 병렬 처리 유지.
- **개선 효과:** 중복 발송 **약 12,640건 → 0건**, 처리 시간 `FOR UPDATE` 단독 대비 **2,223 ms → 1,505 ms (-32%)**.



### 2. DeferredResult 도입 — 외부 API 대기 병목이 무관한 API로 전파되는 문제 격리

- **문제:** Gemini 파싱 API 가 요청당 `CompletableFuture.get()` 으로 10초 블로킹되면서 톰캣 워커 풀(20)이 포화. Gemini 와 무관한 여정 조회 API 까지 스레드를 받지 못해 **p95 12ms → 4.14s** 로 병목이 전파됨을 k6 부하테스트로 확인.
- **해결:** 컨트롤러 반환 타입을 `DeferredResult<ApiResponse<...>>` 로 전환. 요청 스레드는 등록 후 즉시 반환하고, 배치 콜백 `whenComplete` 에서 `setResult` / `setErrorResult` 로 비동기 응답. 타임아웃은 배치 `orTimeout(30s)` + 여유 2s = **32s**.
- **개선 효과:**
  - 여정 조회 API **p95 4.14s → 18.53ms** (부하 없을 때 12ms 수준)
  - 여정 조회 API 처리 중 **스레드 블로킹 peak 20 → 1**
  - Gemini 응답 자체를 빠르게 만든 게 아니라, 그 영향이 **다른 API 로 번지지 않도록 격리**한 개선




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

