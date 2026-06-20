# 예약 목록 조회 API

## 1. 배경 + 목표

### 배경
사용자가 자신이 등록한 예약을 한눈에 확인할 수 있어야 한다.
홈/예약 관리 화면에서 호출되는 가장 빈도 높은 API이므로, 응답 페이로드를 최소화하고 정렬 규칙을 명확히 한다.

### 목표
- 본인 소유 예약 목록을 일정한 정렬 순서로 반환한다.
- ONCE / REPEAT 타입을 한 응답에 함께 담되, 클라가 구분/필터링하기 쉬운 형태로 내려준다.

---

## 2. 범위 (Scope)

### 2.1 In Scope
- `GET /api/v1/reservations`
- `ReservationListResponse` (요약 DTO 리스트)
- 본인 예약만 반환
- 정렬 규칙 적용
- 단위 테스트 (Repository / Service)

### 2.2 Out of Scope
- 페이지네이션 (현재 예약 수는 1인당 소수 가정 → 전체 반환)
- 필터링 파라미터 (`type`, `routeOption` 등)
- 예약 상세 조회 API — 필요 시 별도 이슈

---

## 3. API / 인터페이스 설계

### 3.1 엔드포인트
`GET /api/v1/reservations`

### 3.2 요청 / 응답

요청 파라미터: 없음 (인증 사용자 기준).

응답 (200 OK):
```json
{
  "items": [
    {
      "id": 12,
      "nickname": "출근",
      "originName": "집",
      "destName": "회사",
      "arrivalTime": "09:00:00",
      "reservationType": "REPEAT",
      "repeatDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"],
      "reservationDate": null,
      "routeOption": "TRANSIT"
    },
    {
      "id": 15,
      "nickname": "공항",
      "originName": "집",
      "destName": "ICN",
      "arrivalTime": "06:30:00",
      "reservationType": "ONCE",
      "repeatDays": null,
      "reservationDate": "2026-06-10T06:30:00",
      "routeOption": "DRIVING"
    }
  ]
}
```

응답 DTO는 좌표(lat/lng)를 포함하지 않는다 — 목록 화면에는 불필요.

### 3.3 오류
정상 흐름만 존재. 예약이 없으면 `{ "items": [] }`, 200.

---

## 4. 데이터 모델 변경
없음.

---

## 5. 처리 흐름

```text
[Client] GET /api/v1/reservations
  → ReservationController
  → ReservationService.findAllByUser(userId)
      [TX READ_ONLY] reservationRepository.findAllByUserIdOrderBy...(userId)
      → List<ReservationListItem>
  → 응답
```

트랜잭션: `@Transactional(readOnly = true)`.

Repository 메서드:
```java
List<Reservation> findAllByUserIdOrderByReservationTypeAscArrivalTimeAsc(Long userId);
```

---

## 6. 결정 사항

| 항목 | 선택 | 대안 | 선택 근거 |
|------|------|------|-----------|
| 정렬 | `reservationType ASC, arrivalTime ASC` | 생성일 desc | REPEAT(일상)을 위에, ONCE(이벤트)를 아래에. 같은 타입 내에서는 도착 시각 순 |
| 페이지네이션 | 없음 | offset/cursor | 1인당 예약 수가 적다는 가정. 초과 시점에 도입 |
| 필터링 | 없음 | type/routeOption 쿼리 파라미터 | 화면 요구사항에 아직 없음 — YAGNI |
| 응답 포맷 | `{ items: [...] }` 래핑 | 배열 그대로 | 향후 페이지네이션 메타 추가 시 호환성 확보 |
| 좌표 노출 | 제외 | 포함 | 목록 화면 미사용, 페이로드 절감 |
