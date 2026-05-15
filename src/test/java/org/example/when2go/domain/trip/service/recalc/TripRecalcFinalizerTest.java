package org.example.when2go.domain.trip.service.recalc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.example.when2go.domain.notification.service.schedule.NotificationScheduleCreateService;
import org.example.when2go.domain.route.dto.RouteSearchResult;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.enums.TripRecalcPhase;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.enums.Platform;
import org.junit.jupiter.api.Test;

class TripRecalcFinalizerTest {

    private final TripRepository tripRepository = org.mockito.Mockito.mock(TripRepository.class);
    private final TripRecalcPhasePolicy phasePolicy = new TripRecalcPhasePolicy();
    private final NotificationScheduleCreateService notificationScheduleCreateService =
            org.mockito.Mockito.mock(NotificationScheduleCreateService.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-05-12T00:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private final TripRecalcFinalizer finalizer = new TripRecalcFinalizer(
            tripRepository,
            phasePolicy,
            notificationScheduleCreateService,
            clock
    );

    // Phase 3 trip은 재계산 결과로 DONE 처리되고 출발 알림 생성이 호출되는지 확인한다.
    @Test
    void finalizeMarksTripDoneAndCreatesSchedulesWhenPhase3() {
        Trip trip = trip(TripRecalcPhase.PHASE_3, LocalDateTime.of(2026, 5, 12, 10, 0));
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        finalizer.finalizeRecalc(1L, new RouteSearchResult(List.of(new RouteSearchResult.Route("2400s"))));

        assertThat(trip.getFinalDepartureTime()).isEqualTo(LocalDateTime.of(2026, 5, 12, 9, 10));
        assertThat(trip.getRecalcPhase()).isEqualTo(TripRecalcPhase.DONE);
        assertThat(trip.getNextRecalcAt()).isNull();
        verify(notificationScheduleCreateService).createDepartureSchedules(trip);
    }

    // DONE이 아닌 중간 phase는 다음 재계산 시각만 갱신하고 알림은 생성하지 않는지 확인한다.
    @Test
    void finalizeSchedulesNextRecalcWhenNotDone() {
        Trip trip = trip(TripRecalcPhase.INITIAL, LocalDateTime.of(2026, 5, 12, 11, 0));
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        finalizer.finalizeRecalc(1L, new RouteSearchResult(List.of(new RouteSearchResult.Route("2400s"))));

        assertThat(trip.getFinalDepartureTime()).isNull();
        assertThat(trip.getRecalcPhase()).isEqualTo(TripRecalcPhase.PHASE_1);
        assertThat(trip.getNextRecalcAt()).isEqualTo(LocalDateTime.of(2026, 5, 12, 9, 10));
        verify(notificationScheduleCreateService, never()).createDepartureSchedules(trip);
    }

    private Trip trip(TripRecalcPhase recalcPhase, LocalDateTime arrivalTime) {
        AppUser user = AppUser.builder()
                .deviceId("device-id")
                .fcmToken("token")
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
                .arrivalTime(arrivalTime)
                .routeOption(RouteOption.TRANSIT)
                .bufferMinutes(10)
                .recalcPhase(recalcPhase)
                .nextRecalcAt(LocalDateTime.of(2026, 5, 12, 9, 0))
                .build();
    }
}
