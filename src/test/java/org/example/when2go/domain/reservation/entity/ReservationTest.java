package org.example.when2go.domain.reservation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.entity.Platform;
import org.junit.jupiter.api.Test;

class ReservationTest {

    // 반복 예약 필수값과 반복 요일을 모두 전달하면 예약 엔티티가 정상 생성되는지 확인한다.
    @Test
    void builderCreatesReservationWithRepeatDays() {
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
                .repeatDays(repeatDays)
                .build();

        assertThat(reservation.getUser()).isEqualTo(user);
        assertThat(reservation.getOriginName()).isEqualTo("home");
        assertThat(reservation.getRouteOption()).isEqualTo(RouteOption.DRIVE);
        assertThat(reservation.getRepeatDays()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY);
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
                        .repeatDays(Set.of(DayOfWeek.MONDAY))
                        .build())
                .withMessage("routeOption must not be null");
    }

    // 반복 요일이 null이면 예약 엔티티 생성 시점에 NPE가 발생하는지 확인한다.
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
                        .build())
                .withMessage("repeatDays must not be null");
    }

    // 반복 요일이 비어있으면 예약 엔티티 생성 시점에 IllegalArgumentException이 발생하는지 확인한다.
    @Test
    void builderThrowsIllegalArgumentExceptionWhenRepeatDaysIsEmpty() {
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
                        .repeatDays(Set.of())
                        .build())
                .withMessage("repeatDays must not be empty");
    }
}
