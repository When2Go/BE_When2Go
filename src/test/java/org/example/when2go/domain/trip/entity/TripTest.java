package org.example.when2go.domain.trip.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.LocalDateTime;
import org.example.when2go.domain.route.enums.RouteOption;
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
                .routeOption(RouteOption.TRANSIT)
                .bufferMinutes(10)
                .build();

        assertThat(trip.getUser()).isEqualTo(user);
        assertThat(trip.getArrivalTime()).isEqualTo(arrivalTime);
        assertThat(trip.getRouteOption()).isEqualTo(RouteOption.TRANSIT);
        assertThat(trip.getStatus()).isEqualTo(TripStatus.PENDING);
        assertThat(trip.getRecalcPhase()).isEqualTo(TripRecalcPhase.INITIAL);
        assertThat(trip.getNextRecalcAt()).isNull();
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
                        .routeOption(RouteOption.TRANSIT)
                        .bufferMinutes(null)
                        .build())
                .withMessage("bufferMinutes must not be null");
    }

    // 재계산 결과를 적용하면 다음 phase와 다음 계산 시각이 갱신되는지 확인한다.
    @Test
    void applyRecalcResultUpdatesPhaseAndNextRecalcAt() {
        Trip trip = validTrip();
        LocalDateTime nextRecalcAt = LocalDateTime.of(2026, 5, 7, 8, 30);

        trip.applyRecalcResult(TripRecalcPhase.PHASE_2, nextRecalcAt);

        assertThat(trip.getRecalcPhase()).isEqualTo(TripRecalcPhase.PHASE_2);
        assertThat(trip.getNextRecalcAt()).isEqualTo(nextRecalcAt);
    }

    // trip 확정 시 출발 시각, 재계산 완료 상태, 일정 상태가 함께 갱신되는지 확인한다.
    @Test
    void markFinalizedUpdatesDepartureTimeAndDonePhase() {
        Trip trip = validTrip();
        trip.applyRecalcResult(
                TripRecalcPhase.PHASE_3,
                LocalDateTime.of(2026, 5, 7, 8, 40)
        );
        LocalDateTime finalDepartureTime = LocalDateTime.of(2026, 5, 7, 8, 50);

        trip.markFinalized(finalDepartureTime);

        assertThat(trip.getFinalDepartureTime()).isEqualTo(finalDepartureTime);
        assertThat(trip.getRecalcPhase()).isEqualTo(TripRecalcPhase.DONE);
        assertThat(trip.getNextRecalcAt()).isNull();
        assertThat(trip.getStatus()).isEqualTo(TripStatus.SCHEDULED);
    }

    private Trip validTrip() {
        AppUser user = AppUser.builder()
                .deviceId("device-id")
                .platform(Platform.IOS)
                .build();

        return Trip.builder()
                .user(user)
                .originName("home")
                .destName("office")
                .originLat(37.1)
                .originLng(127.1)
                .destLat(37.2)
                .destLng(127.2)
                .arrivalTime(LocalDateTime.of(2026, 5, 7, 9, 0))
                .routeOption(RouteOption.TRANSIT)
                .bufferMinutes(10)
                .build();
    }
}
