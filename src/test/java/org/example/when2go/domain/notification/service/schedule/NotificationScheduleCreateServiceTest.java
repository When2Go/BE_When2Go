package org.example.when2go.domain.notification.service.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.example.when2go.domain.notification.entity.NotificationSchedule;
import org.example.when2go.domain.notification.entity.NotificationType;
import org.example.when2go.domain.notification.repository.NotificationScheduleRepository;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.entity.Platform;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationScheduleCreateServiceTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 5, 7, 8, 30);

    private final NotificationScheduleRepository notificationScheduleRepository =
            org.mockito.Mockito.mock(NotificationScheduleRepository.class);
    private final Clock clock = Clock.fixed(
            FIXED_NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC
    );
    private final NotificationScheduleCreateService notificationScheduleCreateService =
            new NotificationScheduleCreateService(notificationScheduleRepository, clock);

    @Test
    void createDepartureSchedulesCreatesTenMinuteAndNowSchedules() {
        when(notificationScheduleRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        Trip trip = trip(LocalDateTime.of(2026, 5, 7, 9, 0));

        notificationScheduleCreateService.createDepartureSchedules(trip);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationSchedule>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(notificationScheduleRepository).saveAll(captor.capture());
        List<NotificationSchedule> schedules = captor.getValue();

        assertThat(schedules).hasSize(2);
        assertThat(schedules)
                .extracting(NotificationSchedule::getType)
                .containsExactly(NotificationType.DEPART_10MIN, NotificationType.DEPART_NOW);
        assertThat(schedules)
                .extracting(NotificationSchedule::getScheduledAt)
                .containsExactly(
                        LocalDateTime.of(2026, 5, 7, 8, 50),
                        LocalDateTime.of(2026, 5, 7, 9, 0)
                );
    }

    @Test
    void createDepartureSchedulesCreatesImmediateLateWhenDepartureAlreadyPassed() {
        when(notificationScheduleRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // FIXED_NOW = 08:30 인데 출발 시각이 08:00 (이미 30분 지남)
        Trip trip = trip(LocalDateTime.of(2026, 5, 7, 8, 0));

        notificationScheduleCreateService.createDepartureSchedules(trip);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationSchedule>> captor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(notificationScheduleRepository).saveAll(captor.capture());
        List<NotificationSchedule> schedules = captor.getValue();

        assertThat(schedules).hasSize(1);
        assertThat(schedules.get(0).getType()).isEqualTo(NotificationType.IMMEDIATE_LATE);
        assertThat(schedules.get(0).getScheduledAt()).isEqualTo(FIXED_NOW);
    }

    private Trip trip(LocalDateTime finalDepartureTime) {
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
                .arrivalTime(LocalDateTime.of(2026, 5, 7, 10, 0))
                .routeOption(RouteOption.TRANSIT)
                .bufferMinutes(10)
                .finalDepartureTime(finalDepartureTime)
                .nextRecalcAt(LocalDateTime.of(2026, 5, 7, 8, 0))
                .build();
    }
}
