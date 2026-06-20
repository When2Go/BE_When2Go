# 예약 삭제 API

## 1. 배경 + 목표

### 배경
사용자가 더 이상 필요 없는 예약을 제거할 수 있어야 한다.
예약은 Trip이 참조하는 부모 리소스이므로 (`trips.reservation_id`), 삭제 시 참조 무결성과 과거 Trip 데이터 보존을 동시에 고려해야 한다.

### 목표
- 본인 소유 예약을 안전하게 삭제한다.
- 이미 생성된 과거 Trip 기록은 유실되지 않는다.
- 삭제 후 예약은 재조회되지 않는다.

---

## 2. 범위 (Scope)

### 2.1 In Scope
- `DELETE /api/v1/reservations/{id}`
- 본인 소유 검증
- `Trip.reservation` FK 처리 전략 결정 및 적용 (아래 6항 참고)
- 단위 테스트

### 2.2 Out of Scope
- 과거 Trip 일괄 삭제 (보존 정책상 의도적으로 남김)
- 미래 발생 예정 알림 일괄 취소 (별도 이슈)
- soft delete 도입 (단순 hard delete로 시작)

---

## 3. API / 인터페이스 설계

### 3.1 엔드포인트
`DELETE /api/v1/reservations/{id}` — 리소스 삭제.

### 3.2 요청 / 응답
- 요청 바디 없음.
- 응답: `204 No Content`.
- 같은 id로 한 번 더 호출하면 404 (멱등하지 않음, 명시적 의도).

### 3.3 오류

| HTTP | 코드 | 발생 조건 |
|------|------|-----------|
| 403 | `RESERVATION_003 FORBIDDEN` | 본인 예약 아님 |
| 404 | `RESERVATION_002 RESERVATION_NOT_FOUND` | 해당 id 예약 없음 |

---

## 4. 데이터 모델 변경

### 4.1 엔티티 변경
- `Trip.reservation` 관계는 현재 `@ManyToOne(... optional 미지정, nullable 미지정)`. 과거 기록 보존을 위해 FK는 `nullable`이어야 한다.
- 마이그레이션 / DDL: `trips.reservation_id`를 `NULL 허용`으로 보장 (이미 그렇다면 변경 없음).
- 예약 삭제 시 연관 `Trip.reservation_id`를 `NULL`로 끊는다.

---

## 5. 처리 흐름

```text
[Client] DELETE /api/v1/reservations/{id}
  → ReservationController
  → ReservationService.delete(userId, reservationId)
      [TX] reservationRepository.findById(id) or RESERVATION_002
           if (reservation.user.id != userId) → RESERVATION_003
           tripRepository.detachReservation(reservationId)
              // UPDATE trips SET reservation_id = NULL WHERE reservation_id = ?
           reservationRepository.delete(reservation)
  → 204
```

트랜잭션: `ReservationService.delete`에 `@Transactional` 1개. detach + delete가 같은 TX 안에서 수행.

---

## 6. 결정 사항

| 항목 | 선택 | 대안 | 선택 근거 |
|------|------|------|-----------|
| 삭제 방식 | hard delete | soft delete (`deleted_at`) | 현재 운영 요구사항에 soft delete 필요 없음. 도입은 추후 |
| Trip FK 처리 | detach (set null) | CASCADE 삭제 / 삭제 금지 | 과거 Trip은 통계/리포트 자원이라 보존, 단 예약 참조는 끊음 |
| 권한 코드 | 별도 `RESERVATION_003 FORBIDDEN` | 404로 숨김 처리 | 본인 리소스 존재 여부와 권한 부재를 운영상 구분하기 위함 |
| 멱등성 | 비멱등 (두 번째 호출 404) | 두 번째 호출도 204 | 클라가 "정말 지워졌는지" 명시적으로 알 수 있도록 |
