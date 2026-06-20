# 예약 생성 API

## 1. 배경 + 목표

### 배경
사용자가 출/도착지, 경로 옵션, 도착 시각을 기반으로 예약(Reservation)을 등록해야 한다.
예약은 단발성(ONCE)과 요일 반복(REPEAT) 두 종류를 모두 지원한다.
이미 `Reservation` 엔티티는 정의되어 있으나 생성 API/서비스가 없는 상태다.

### 목표
- 사용자가 예약을 새로 생성할 수 있는 API를 제공한다.
- ONCE / REPEAT 타입에 따른 필드 정합성을 컨트롤러·DTO·엔티티 3중으로 검증한다.
- 생성된 예약의 식별자를 응답해 후속 흐름(Trip 생성 등)에서 활용할 수 있게 한다.

---

## 2. 범위 (Scope)

### 2.1 In Scope
- `POST /api/v1/reservations`
- `ReservationCreateRequest` DTO + Bean Validation
- `ReservationService.create(...)`
- `ReservationRepository` (JPA) 신규 생성
- 단위 테스트 (서비스 레벨)

### 2.2 Out of Scope
- 예약 생성에 따른 Trip 자동 생성 (별도 이슈)
- 알림 스케줄 등록 (별도 이슈)
- 위치 검색 / autocomplete (route 도메인 책임)

---

## 3. API / 인터페이스 설계

### 3.1 엔드포인트
`POST /api/v1/reservations` — 리소스 생성이므로 POST.

### 3.2 요청 / 응답

요청 DTO `ReservationCreateRequest`:

| 필드 | 타입 | 검증 | 비고 |
|------|------|------|------|
| `nickname` | String | `@Size(max=30)` | optional |
| `originName` | String | `@NotBlank` | |
| `originLat` | Double | `@NotNull` | |
| `originLng` | Double | `@NotNull` | |
| `destName` | String | `@NotBlank` | |
| `destLat` | Double | `@NotNull` | |
| `destLng` | Double | `@NotNull` | |
| `routeOption` | RouteOption | `@NotNull` | enum |
| `arrivalTime` | LocalTime | `@NotNull` | |
| `reservationType` | ReservationType | `@NotNull` | ONCE / REPEAT |
| `repeatDays` | Set\<DayOfWeek\> | REPEAT일 때 비어있지 않음 | |
| `reservationDate` | LocalDateTime | ONCE일 때 not null | |

요청 예시:
```json
{
  "nickname": "출근",
  "originName": "집",
  "originLat": 37.5,
  "originLng": 127.0,
  "destName": "회사",
  "destLat": 37.55,
  "destLng": 127.05,
  "routeOption": "TRANSIT",
  "arrivalTime": "09:00:00",
  "reservationType": "REPEAT",
  "repeatDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]
}
```

응답 (201 Created):
```json
{ "id": 123 }
```

### 3.3 오류

| HTTP | 코드 | 발생 조건 |
|------|------|-----------|
| 400 | `GLOBAL_001 VALIDATION_FAILED` | Bean Validation 실패 |
| 400 | `RESERVATION_001 INVALID_SCHEDULE_FIELDS` | ONCE인데 `reservationDate` 없음 / REPEAT인데 `repeatDays` 비어있음 / 타입과 필드 불일치 |
| 404 | `USER_001 USER_NOT_FOUND` | 인증 사용자 미존재 |

---

## 4. 데이터 모델 변경
없음 (기존 `reservations` 테이블 그대로 사용).

---

## 5. 처리 흐름

```text
[Client] POST /api/v1/reservations
  → ReservationController
      @Valid 로 DTO 검증
  → ReservationService.create(userId, request)
      [TX] AppUser 조회 (없으면 USER_001)
           Reservation.builder()...build()  // 엔티티 내부 validateScheduleFields 재검증
           reservationRepository.save(entity)
           return entity.getId()
  → 응답 { id }
```

트랜잭션: `ReservationService.create`에 `@Transactional` 1개.

---

## 6. 결정 사항
- 인증 사용자 식별 방법은 기존 `UserController` 패턴(`@AuthenticationPrincipal` 또는 헤더)을 그대로 따른다.
- 검증은 DTO(@Valid) + 엔티티 생성자(Objects.requireNonNull / validateScheduleFields) 이중으로 둔다. 엔티티 검증은 도메인 invariant 보호용.
- 생성 응답은 `{ id }` 최소 형태. 상세는 별도 조회 API가 처리.
