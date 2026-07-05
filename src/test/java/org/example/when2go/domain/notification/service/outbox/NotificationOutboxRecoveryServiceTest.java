package org.example.when2go.domain.notification.service.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.example.when2go.domain.notification.entity.NotificationSchedule;
import org.example.when2go.domain.notification.entity.NotificationScheduleOutbox;
import org.example.when2go.domain.notification.entity.NotificationType;
import org.example.when2go.domain.notification.event.NotificationOutboxRepublishRequestedEvent;
import org.example.when2go.domain.notification.repository.NotificationScheduleOutboxRepository;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.entity.Platform;
import org.example.when2go.global.config.notification.NotificationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationOutboxRecoveryServiceTest {

    private final NotificationScheduleOutboxRepository repository = mock(NotificationScheduleOutboxRepository.class);
    private final NotificationOutboxStatusService statusService = mock(NotificationOutboxStatusService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final NotificationProperties properties = new NotificationProperties();
    private final Clock clock = Clock.fixed(
            LocalDateTime.of(2026, 7, 3, 12, 0).atZone(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault()
    );
    private final NotificationOutboxRecoveryService recoveryService = new NotificationOutboxRecoveryService(
            repository,
            statusService,
            properties,
            eventPublisher,
            clock
    );

    @Test
    void recoverStuckProcessingWhenBelowMaxRetry() {
        NotificationScheduleOutbox outbox = outbox(1L, 1);
        when(repository.claimStuckProcessingIds(any(LocalDateTime.class), anyInt())).thenReturn(List.of(1L));
        when(repository.claimStalePendingIds(any(LocalDateTime.class), anyInt())).thenReturn(List.of());
        when(repository.findAllByIdIn(List.of(1L))).thenReturn(List.of(outbox));

        recoveryService.recoverStuckOutboxes();

        verify(statusService).markRetryableFailure(1L);
        verify(eventPublisher).publishEvent(any(NotificationOutboxRepublishRequestedEvent.class));
    }

    @Test
    void recoverStalePendingWhenBelowMaxRetry() {
        NotificationScheduleOutbox outbox = outbox(2L, 0);
        when(repository.claimStuckProcessingIds(any(LocalDateTime.class), anyInt())).thenReturn(List.of());
        when(repository.claimStalePendingIds(any(LocalDateTime.class), anyInt())).thenReturn(List.of(2L));
        when(repository.findAllByIdIn(List.of(2L))).thenReturn(List.of(outbox));

        recoveryService.recoverStuckOutboxes();

        verify(statusService).markRetryableFailure(2L);
        verify(eventPublisher).publishEvent(any(NotificationOutboxRepublishRequestedEvent.class));
    }

    @Test
    void markFailedWhenExceedsMaxRetry() {
        NotificationScheduleOutbox outbox = outbox(3L, 3);
        when(repository.claimStuckProcessingIds(any(LocalDateTime.class), anyInt())).thenReturn(List.of(3L));
        when(repository.claimStalePendingIds(any(LocalDateTime.class), anyInt())).thenReturn(List.of());
        when(repository.findAllByIdIn(List.of(3L))).thenReturn(List.of(outbox));

        recoveryService.recoverStuckOutboxes();

        verify(statusService).markFailed(3L);
        verify(eventPublisher, never()).publishEvent(any(NotificationOutboxRepublishRequestedEvent.class));
    }

    @Test
    void skipWhenNoTargets() {
        when(repository.claimStuckProcessingIds(any(LocalDateTime.class), anyInt())).thenReturn(List.of());
        when(repository.claimStalePendingIds(any(LocalDateTime.class), anyInt())).thenReturn(List.of());

        recoveryService.recoverStuckOutboxes();

        verify(statusService, never()).markRetryableFailure(any());
        verify(statusService, never()).markFailed(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    private NotificationScheduleOutbox outbox(Long id, int retryCount) {
        AppUser user = AppUser.builder()
                .deviceId("device-" + id)
                .fcmToken("token")
                .platform(Platform.IOS)
                .build();
        ReflectionTestUtils.setField(user, "id", 10L + id);

        Trip trip = Trip.builder()
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
                .finalDepartureTime(LocalDateTime.of(2026, 5, 7, 9, 0))
                .nextRecalcAt(LocalDateTime.of(2026, 5, 7, 8, 0))
                .build();
        ReflectionTestUtils.setField(trip, "id", 20L + id);

        NotificationSchedule schedule = NotificationSchedule.builder()
                .trip(trip)
                .user(user)
                .type(NotificationType.DEPART_NOW)
                .scheduledAt(LocalDateTime.of(2026, 5, 7, 9, 0))
                .build();
        ReflectionTestUtils.setField(schedule, "id", 30L + id);

        NotificationScheduleOutbox outbox = NotificationScheduleOutbox.builder()
                .schedule(schedule)
                .user(user)
                .trip(trip)
                .type(NotificationType.DEPART_NOW)
                .title("title")
                .body("body")
                .retryCount(retryCount)
                .dedupKey("notification:" + schedule.getId())
                .build();
        ReflectionTestUtils.setField(outbox, "id", id);
        return outbox;
    }
}
