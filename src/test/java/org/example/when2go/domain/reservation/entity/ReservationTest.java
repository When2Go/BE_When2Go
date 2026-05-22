package org.example.when2go.domain.reservation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.entity.Platform;
import org.junit.jupiter.api.Test;

class ReservationTest {

    // 단건 예약 필수값과 예약일을 모두 전달하면 예약 엔티티가 정상 생성되는지 확인한다.
    @Test
    void builderCreatesOnceReservationWithRequiredValues() {
        AppUser user = AppUser.builder()
                .deviceId("device-id")
                .platform(Platform.IOS)
                .build();
        LocalDateTime reservationDate = LocalDateTime.of(2026, 5, 8, 9, 0);

        Reservation reservation = Reservation.builder()
                .user(user)
                .originName("home")
                .originLat(37.1)
                .originLng(127.1)
                .destName("office")
                .destLat(37.2)
                .destLng(127.2)
                .routeOption(RouteOption.TRANSIT)
                .arrivalTime(LocalTime.of(9, 0))
                .reservationType(ReservationType.ONCE)
                .reservationDate(reservationDate)
                .build();

        assertThat(reservation.getUser()).isEqualTo(user);
        assertThat(reservation.getOriginName()).isEqualTo("home");
        assertThat(reservation.getRouteOption()).isEqualTo(RouteOption.TRANSIT);
        assertThat(reservation.getReservationType()).isEqualTo(ReservationType.ONCE);
        assertThat(reservation.getReservationDate()).isEqualTo(reservationDate);
        assertThat(reservation.getRepeatDays()).isNull();
    }

    // 반복 예약 필수값과 반복 요일을 모두 전달하면 예약 엔티티가 정상 생성되는지 확인한다.
    @Test
    void builderCreatesRepeatReservationWithRepeatDays() {
        AppUser user = AppUser.builder()
                .deviceId("device-id")
                .platform(Platform.IOS)
                .build();
        Set<DayOfWeek> repeatDays = Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);

        Reservation reservation = Reservation.builder()
                .user(user)
                .originName("home")
                .originLat(37.1)
                .originLng(127.1)
                .destName("office")
                .destLat(37.2)
                .destLng(127.2)
                .routeOption(RouteOption.DRIVE)
                .arrivalTime(LocalTime.of(9, 0))
                .reservationType(ReservationType.REPEAT)
                .repeatDays(repeatDays)
                .build();

        assertThat(reservation.getReservationType()).isEqualTo(ReservationType.REPEAT);
        assertThat(reservation.getRepeatDays()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
        assertThat(reservation.getReservationDate()).isNull();
    }

    // 필수값이 null이면 예약 엔티티 생성 시점에 NPE가 발생하는지 확인한다.
    @Test
    void builderThrowsNullPointerExceptionWhenRequiredValueIsNull() {
        AppUser user = AppUser.builder()
                .deviceId("device-id")
                .platform(Platform.IOS)
                .build();

        assertThatNullPointerException()
                .isThrownBy(() -> Reservation.builder()
                        .user(user)
                        .originName("home")
                        .originLat(37.1)
                        .originLng(127.1)
                        .destName("office")
                        .destLat(37.2)
                        .destLng(127.2)
                        .routeOption(null)
                        .arrivalTime(LocalTime.of(9, 0))
                        .reservationType(ReservationType.ONCE)
                        .reservationDate(LocalDateTime.of(2026, 5, 8, 9, 0))
                        .build())
                .withMessage("routeOption must not be null");
    }

    // 단건 예약에 예약일이 없으면 예약 엔티티 생성 시점에 NPE가 발생하는지 확인한다.
    @Test
    void builderThrowsNullPointerExceptionWhenOnceReservationDateIsNull() {
        AppUser user = AppUser.builder()
                .deviceId("device-id")
                .platform(Platform.IOS)
                .build();

        assertThatNullPointerException()
                .isThrownBy(() -> Reservation.builder()
                        .user(user)
                        .originName("home")
                        .originLat(37.1)
                        .originLng(127.1)
                        .destName("office")
                        .destLat(37.2)
                        .destLng(127.2)
                        .routeOption(RouteOption.DRIVE)
                        .arrivalTime(LocalTime.of(9, 0))
                        .reservationType(ReservationType.ONCE)
                        .build())
                .withMessage("reservationDate must not be null");
    }

    // 반복 예약에 반복 요일이 없으면 예약 엔티티 생성 시점에 NPE가 발생하는지 확인한다.
    @Test
    void builderThrowsNullPointerExceptionWhenRepeatDaysIsNull() {
        AppUser user = AppUser.builder()
                .deviceId("device-id")
                .platform(Platform.IOS)
                .build();

        assertThatNullPointerException()
                .isThrownBy(() -> Reservation.builder()
                        .user(user)
                        .originName("home")
                        .originLat(37.1)
                        .originLng(127.1)
                        .destName("office")
                        .destLat(37.2)
                        .destLng(127.2)
                        .routeOption(RouteOption.DRIVE)
                        .arrivalTime(LocalTime.of(9, 0))
                        .reservationType(ReservationType.REPEAT)
                        .build())
                .withMessage("repeatDays must not be null");
    }

    // 예약 타입과 맞지 않는 스케줄 필드 조합이면 잘못된 인자로 판단하는지 확인한다.
    @Test
    void builderThrowsIllegalArgumentExceptionWhenScheduleFieldsConflictWithReservationType() {
        AppUser user = AppUser.builder()
                .deviceId("device-id")
                .platform(Platform.IOS)
                .build();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Reservation.builder()
                        .user(user)
                        .originName("home")
                        .originLat(37.1)
                        .originLng(127.1)
                        .destName("office")
                        .destLat(37.2)
                        .destLng(127.2)
                        .routeOption(RouteOption.DRIVE)
                        .arrivalTime(LocalTime.of(9, 0))
                        .reservationType(ReservationType.REPEAT)
                        .repeatDays(Set.of(DayOfWeek.MONDAY))
                        .reservationDate(LocalDateTime.of(2026, 5, 8, 9, 0))
                        .build())
                .withMessage("reservationDate must be null for REPEAT reservation");
    }
}
