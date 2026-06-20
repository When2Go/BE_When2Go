# 예약 수정 API

## 1. 배경 + 목표

### 배경
사용자가 등록한 예약의 출/도착지, 도착 시각, 반복 요일 등을 변경할 수 있어야 한다.
타입(ONCE/REPEAT) 자체 변경도 허용하되, 스케줄 필드 정합성이 깨지지 않게 보장한다.
예약 변경은 향후 Trip 재계산에도 영향을 주므로, 변경 책임은 예약 도메인에 한정한다.

### 목표
- 본인 소유 예약을 부분/전체 필드 단위로 수정한다.
- 수정 후에도 ONCE / REPEAT 별 필드 invariant가 유지된다.
- 권한 없는 사용자가 타인의 예약을 수정할 수 없다.

---

## 2. 범위 (Scope)

### 2.1 In Scope
- `PUT /api/v1/reservations/{id}` — 전체 교체 의미의 갱신
- `ReservationUpdateRequest` DTO + Bean Validation
- `Reservation` 엔티티에 도메인 메서드 `update(...)` 추가 (필드 직접 setter 금지)
- 본인 소유 검증
- 단위 테스트

### 2.2 Out of Scope
- 부분 패치(PATCH) — 이번 PR에선 PUT만
- 예약 수정 시 연관 Trip 재계산 트리거 (별도 이슈)
- 알림 스케줄 재발행 (별도 이슈)

---

## 3. API / 인터페이스 설계

### 3.1 엔드포인트
`PUT /api/v1/reservations/{id}` — 클라이언트가 예약 전체 상태를 보내고 서버는 전체 교체. 멱등성 확보.

### 3.2 요청 / 응답

요청 DTO `ReservationUpdateRequest`: `ReservationCreateRequest`와 동일 필드 (nickname, origin/dest, routeOption, arrivalTime, reservationType, repeatDays, reservationDate).

응답: `204 No Content`.

### 3.3 오류

| HTTP | 코드 | 발생 조건 |
|------|------|-----------|
| 400 | `GLOBAL_001 VALIDATION_FAILED` | Bean Validation 실패 |
| 400 | `RESERVATION_001 INVALID_SCHEDULE_FIELDS` | 타입-필드 정합성 위반 |
| 403 | `RESERVATION_003 FORBIDDEN` | 본인 예약 아님 |
| 404 | `RESERVATION_002 RESERVATION_NOT_FOUND` | 해당 id 예약 없음 |

---

## 4. 데이터 모델 변경
없음.

---

## 5. 처리 흐름

```text
[Client] PUT /api/v1/reservations/{id}
  → ReservationController @Valid 로 DTO 검증
  → ReservationService.update(userId, reservationId, request)
      [TX] reservationRepository.findById(id) or RESERVATION_002
           if (reservation.user.id != userId) → RESERVATION_003
           reservation.update(...)  // 도메인 메서드 안에서 validateScheduleFields 재실행
           // JPA dirty checking
  → 204
```

트랜잭션: `ReservationService.update`에 `@Transactional` 1개.

---

## 6. 결정 사항

| 항목 | 선택 | 대안 | 선택 근거 |
|------|------|------|-----------|
| HTTP 메서드 | PUT (전체 교체) | PATCH (부분 갱신) | 멱등성 확보, 클라이언트가 폼 전체를 보내는 화면 구조와 일치 |
| 변경 진입점 | 엔티티 `update(...)` 메서드 | 서비스에서 setter 직접 호출 | invariant(validateScheduleFields) 재사용, 도메인에 책임 집중 |
| 소유자 검증 | 서비스 레이어에서 `userId` 비교 | 쿼리에서 `findByIdAndUserId` | 404와 403을 명확히 구분하기 위해 서비스에서 두 단계로 처리 |
