package org.example.when2go.domain.trip.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.LocalDateTime;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.trip.enums.TripStatus;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.enums.Platform;
import org.junit.jupiter.api.Test;

class TripTest {

    // 필수값을 모두 전달하면 이동 엔티티가 정상 생성되고 기본 상태가 적용되는지 확인한다.
    @Test
    void builderCreatesTripWithRequiredValuesAndDefaultStatus() {
        AppUser user = AppUser.builder()
                .deviceId("device-id")
                .platform(Platform.IOS)
                .build();
        LocalDateTime arrivalTime = LocalDateTime.of(2026, 5, 7, 9, 0);

        Trip trip = Trip.builder()
                .user(user)
                .originName("home")
                .destName("office")
                .originLat(37.1)
                .originLng(127.1)
                .destLat(37.2)
                .destLng(127.2)
                .arrivalTime(arrivalTime)
                .routeOption(RouteOption.OPTIMAL)
                .bufferMinutes(10)
                .build();

        assertThat(trip.getUser()).isEqualTo(user);
        assertThat(trip.getArrivalTime()).isEqualTo(arrivalTime);
        assertThat(trip.getRouteOption()).isEqualTo(RouteOption.OPTIMAL);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.PENDING);
    }

    // 필수값이 null이면 이동 엔티티 생성 시점에 NPE가 발생하는지 확인한다.
    @Test
    void builderThrowsNullPointerExceptionWhenRequiredValueIsNull() {
        AppUser user = AppUser.builder()
                .deviceId("device-id")
                .platform(Platform.IOS)
                .build();

        assertThatNullPointerException()
                .isThrownBy(() -> Trip.builder()
                        .user(user)
                        .originName("home")
                        .destName("office")
                        .originLat(37.1)
                        .originLng(127.1)
                        .destLat(37.2)
                        .destLng(127.2)
                        .arrivalTime(LocalDateTime.of(2026, 5, 7, 9, 0))
                        .routeOption(RouteOption.OPTIMAL)
                        .bufferMinutes(null)
                        .build())
                .withMessage("bufferMinutes must not be null");
    }
}
