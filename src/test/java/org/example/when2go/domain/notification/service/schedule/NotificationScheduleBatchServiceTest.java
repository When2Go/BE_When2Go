package org.example.when2go.domain.notification.service.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.example.when2go.domain.notification.entity.NotificationSchedule;
import org.example.when2go.domain.notification.entity.NotificationScheduleOutbox;
import org.example.when2go.domain.notification.entity.NotificationScheduleStatus;
import org.example.when2go.domain.notification.entity.NotificationType;
import org.example.when2go.domain.notification.event.NotificationOutboxesCreatedEvent;
import org.example.when2go.domain.notification.repository.NotificationScheduleOutboxRepository;
import org.example.when2go.domain.notification.repository.NotificationScheduleRepository;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.entity.Platform;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationScheduleBatchServiceTest {

    private final NotificationScheduleRepository notificationScheduleRepository =
            org.mockito.Mockito.mock(NotificationScheduleRepository.class);
    private final NotificationScheduleOutboxRepository notificationScheduleOutboxRepository =
            org.mockito.Mockito.mock(NotificationScheduleOutboxRepository.class);
    private final ApplicationEventPublisher eventPublisher =
            org.mockito.Mockito.mock(ApplicationEventPublisher.class);
    private final NotificationScheduleBatchService service =
            new NotificationScheduleBatchService(
                    notificationScheduleRepository,
                    notificationScheduleOutboxRepository,
                    eventPublisher
            );

    // claim 결과가 비면 outbox/DONE 처리 없이 0건으로 종료한다.
    @Test
    void processReturnsZeroWhenNothingClaimed() {
        when(notificationScheduleRepository.claimDueScheduleIds(10)).thenReturn(List.of());

        int processedCount = service.processDueScheduleBatch(10);

        assertThat(processedCount).isZero();
        verify(notificationScheduleOutboxRepository, never()).save(any());
        verify(notificationScheduleRepository, never()).updateStatus(any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // 토큰이 있는 schedule은 outbox 생성 후 단일 bulk update로 DONE 처리된다.
    @Test
    void processCreatesOutboxAndMarksScheduleDoneInSingleBulkUpdate() {
        NotificationSchedule schedule = schedule(1L, "token");
        when(notificationScheduleRepository.claimDueScheduleIds(10)).thenReturn(List.of(1L));
        when(notificationScheduleRepository.findAllByIdInWithUserAndTrip(List.of(1L)))
                .thenReturn(List.of(schedule));
        when(notificationScheduleOutboxRepository.findDedupKeysIn(List.of("notification:1")))
                .thenReturn(List.of());

        int processedCount = service.processDueScheduleBatch(10);

        assertThat(processedCount).isEqualTo(1);

        ArgumentCaptor<NotificationScheduleOutbox> outboxCaptor =
                ArgumentCaptor.forClass(NotificationScheduleOutbox.class);
        verify(notificationScheduleOutboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getDedupKey()).isEqualTo("notification:1");

        verify(notificationScheduleRepository).updateStatus(List.of(1L), NotificationScheduleStatus.DONE);

        ArgumentCaptor<NotificationOutboxesCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(NotificationOutboxesCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().createdCount()).isEqualTo(1);
    }

    // FCM 토큰이 없으면 outbox 없이 같은 bulk update에 포함된다.
    @Test
    void processSkipsOutboxAndStillMarksDoneWhenTokenMissing() {
        NotificationSchedule schedule = schedule(1L, null);
        when(notificationScheduleRepository.claimDueScheduleIds(10)).thenReturn(List.of(1L));
        when(notificationScheduleRepository.findAllByIdInWithUserAndTrip(List.of(1L)))
                .thenReturn(List.of(schedule));
        when(notificationScheduleOutboxRepository.findDedupKeysIn(List.of("notification:1")))
                .thenReturn(List.of());

        service.processDueScheduleBatch(10);

        verify(notificationScheduleOutboxRepository, never()).save(any());
        verify(notificationScheduleRepository).updateStatus(List.of(1L), NotificationScheduleStatus.DONE);

        ArgumentCaptor<NotificationOutboxesCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(NotificationOutboxesCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().createdCount()).isZero();
        assertThat(eventCaptor.getValue().skippedMissingTokenCount()).isEqualTo(1);
    }

    // 이미 dedup key가 있는 schedule은 outbox 없이 같은 bulk update에 포함된다.
    @Test
    void processSkipsOutboxAndStillMarksDoneWhenDedupExists() {
        NotificationSchedule schedule = schedule(1L, "token");
        when(notificationScheduleRepository.claimDueScheduleIds(10)).thenReturn(List.of(1L));
        when(notificationScheduleRepository.findAllByIdInWithUserAndTrip(List.of(1L)))
                .thenReturn(List.of(schedule));
        when(notificationScheduleOutboxRepository.findDedupKeysIn(List.of("notification:1")))
                .thenReturn(List.of("notification:1"));

        service.processDueScheduleBatch(10);

        verify(notificationScheduleOutboxRepository, never()).save(any());
        verify(notificationScheduleRepository).updateStatus(List.of(1L), NotificationScheduleStatus.DONE);

        ArgumentCaptor<NotificationOutboxesCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(NotificationOutboxesCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().createdCount()).isZero();
        assertThat(eventCaptor.getValue().skippedDuplicateCount()).isEqualTo(1);
    }

    // 한 batch 안의 여러 schedule을 단일 bulk update로 DONE 처리한다.
    @Test
    void processMarksAllSchedulesDoneInSingleBulkUpdate() {
        NotificationSchedule scheduleWithToken = schedule(1L, "token");
        NotificationSchedule scheduleWithoutToken = schedule(2L, null);
        when(notificationScheduleRepository.claimDueScheduleIds(10)).thenReturn(List.of(1L, 2L));
        when(notificationScheduleRepository.findAllByIdInWithUserAndTrip(List.of(1L, 2L)))
                .thenReturn(List.of(scheduleWithToken, scheduleWithoutToken));
        when(notificationScheduleOutboxRepository.findDedupKeysIn(List.of("notification:1", "notification:2")))
                .thenReturn(List.of());

        int processedCount = service.processDueScheduleBatch(10);

        assertThat(processedCount).isEqualTo(2);
        verify(notificationScheduleRepository).updateStatus(List.of(1L, 2L), NotificationScheduleStatus.DONE);

        ArgumentCaptor<NotificationOutboxesCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(NotificationOutboxesCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().createdCount()).isEqualTo(1);
        assertThat(eventCaptor.getValue().skippedMissingTokenCount()).isEqualTo(1);
    }

    // outbox save 중 예외가 발생하면 그대로 전파되어 batch 전체가 rollback될 수 있게 한다.
    @Test
    void processPropagatesOutboxSaveFailure() {
        NotificationSchedule schedule = schedule(1L, "token");
        when(notificationScheduleRepository.claimDueScheduleIds(10)).thenReturn(List.of(1L));
        when(notificationScheduleRepository.findAllByIdInWithUserAndTrip(List.of(1L)))
                .thenReturn(List.of(schedule));
        when(notificationScheduleOutboxRepository.findDedupKeysIn(List.of("notification:1")))
                .thenReturn(List.of());
        RuntimeException exception = new RuntimeException("db down");
        when(notificationScheduleOutboxRepository.save(any())).thenThrow(exception);

        assertThatThrownBy(() -> service.processDueScheduleBatch(10))
                .isSameAs(exception);

        verify(notificationScheduleRepository, never()).updateStatus(any(), eq(NotificationScheduleStatus.DONE));
        verify(eventPublisher, never()).publishEvent(any());
    }

    private NotificationSchedule schedule(Long id, String fcmToken) {
        AppUser user = AppUser.builder()
                .deviceId("device-id")
                .fcmToken(fcmToken)
                .platform(Platform.IOS)
                .build();
        ReflectionTestUtils.setField(user, "id", 10L);

        Trip trip = Trip.builder()
                .user(user)
                .originName("home")
                .destName("office")
                .originLat(37.1)
                .originLng(127.1)
                .destLat(37.2)
                .destLng(127.2)
                .arrivalTime(LocalDateTime.of(2026, 5, 7, 10, 0))
                .routeOption(RouteOption.DRIVE)
                .bufferMinutes(10)
                .finalDepartureTime(LocalDateTime.of(2026, 5, 7, 9, 0))
                .build();
        ReflectionTestUtils.setField(trip, "id", 20L);

        NotificationSchedule schedule = NotificationSchedule.builder()
                .trip(trip)
                .user(user)
                .type(NotificationType.DEPART_NOW)
                .scheduledAt(LocalDateTime.of(2026, 5, 7, 9, 0))
                .build();
        ReflectionTestUtils.setField(schedule, "id", id);
        return schedule;
    }
}
