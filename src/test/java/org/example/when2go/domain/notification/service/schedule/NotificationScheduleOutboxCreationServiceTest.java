package org.example.when2go.domain.notification.service.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.example.when2go.domain.notification.dto.NotificationMessage;
import org.example.when2go.domain.notification.entity.NotificationSchedule;
import org.example.when2go.domain.notification.entity.NotificationScheduleOutbox;
import org.example.when2go.domain.notification.enums.NotificationScheduleStatus;
import org.example.when2go.domain.notification.enums.NotificationType;
import org.example.when2go.domain.notification.event.NotificationOutboxesCreatedEvent;
import org.example.when2go.domain.notification.repository.NotificationScheduleOutboxRepository;
import org.example.when2go.domain.notification.repository.NotificationScheduleRepository;
import org.example.when2go.domain.notification.service.message.NotificationMessageBuilder;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.enums.Platform;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationScheduleOutboxCreationServiceTest {

    private final NotificationScheduleRepository notificationScheduleRepository =
            org.mockito.Mockito.mock(NotificationScheduleRepository.class);
    private final NotificationScheduleOutboxRepository notificationScheduleOutboxRepository =
            org.mockito.Mockito.mock(NotificationScheduleOutboxRepository.class);
    private final NotificationMessageBuilder notificationMessageBuilder =
            org.mockito.Mockito.mock(NotificationMessageBuilder.class);
    private final ApplicationEventPublisher eventPublisher =
            org.mockito.Mockito.mock(ApplicationEventPublisher.class);
    private final NotificationScheduleOutboxCreationService service =
            new NotificationScheduleOutboxCreationService(
                    notificationScheduleRepository,
                    notificationScheduleOutboxRepository,
                    notificationMessageBuilder,
                    eventPublisher
            );

    // 토큰이 있는 PROCESSING 스케줄은 outbox로 생성되고 DONE 처리되는지 확인한다.
    @Test
    void createOutboxesCreatesOutboxAndMarksScheduleDone() {
        NotificationSchedule schedule = schedule(1L, "token");
        when(notificationScheduleRepository.findAllByIdInAndStatusWithUserAndTrip(
                List.of(1L),
                NotificationScheduleStatus.PROCESSING
        )).thenReturn(List.of(schedule));
        when(notificationScheduleOutboxRepository.findDedupKeysIn(List.of("notification:1"))).thenReturn(List.of());
        when(notificationMessageBuilder.build(schedule)).thenReturn(new NotificationMessage("title", "body"));

        NotificationScheduleOutboxCreationResult result = service.createOutboxes(List.of(1L));

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.createdCount()).isEqualTo(1);
        assertThat(result.skippedMissingTokenCount()).isZero();
        assertThat(result.skippedDuplicateCount()).isZero();
        assertThat(schedule.getStatus()).isEqualTo(NotificationScheduleStatus.DONE);

        ArgumentCaptor<NotificationScheduleOutbox> outboxCaptor =
                ArgumentCaptor.forClass(NotificationScheduleOutbox.class);
        verify(notificationScheduleOutboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getDedupKey()).isEqualTo("notification:1");

        ArgumentCaptor<NotificationOutboxesCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(NotificationOutboxesCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().createdCount()).isEqualTo(1);
    }

    // FCM 토큰이 없으면 outbox를 만들지 않고 스케줄만 DONE 처리하는지 확인한다.
    @Test
    void createOutboxesSkipsOutboxWhenTokenIsMissing() {
        NotificationSchedule schedule = schedule(1L, null);
        when(notificationScheduleRepository.findAllByIdInAndStatusWithUserAndTrip(
                List.of(1L),
                NotificationScheduleStatus.PROCESSING
        )).thenReturn(List.of(schedule));
        when(notificationScheduleOutboxRepository.findDedupKeysIn(List.of("notification:1"))).thenReturn(List.of());

        NotificationScheduleOutboxCreationResult result = service.createOutboxes(List.of(1L));

        assertThat(result.createdCount()).isZero();
        assertThat(result.skippedMissingTokenCount()).isEqualTo(1);
        assertThat(schedule.getStatus()).isEqualTo(NotificationScheduleStatus.DONE);
        verify(notificationScheduleOutboxRepository, never()).save(any());
        verify(notificationMessageBuilder, never()).build(any());
    }

    // 기존 dedup key가 있으면 중복 outbox를 만들지 않고 skip count를 남기는지 확인한다.
    @Test
    void createOutboxesSkipsDuplicateDedupKeyWithBulkLookup() {
        NotificationSchedule schedule = schedule(1L, "token");
        when(notificationScheduleRepository.findAllByIdInAndStatusWithUserAndTrip(
                List.of(1L),
                NotificationScheduleStatus.PROCESSING
        )).thenReturn(List.of(schedule));
        when(notificationScheduleOutboxRepository.findDedupKeysIn(List.of("notification:1")))
                .thenReturn(List.of("notification:1"));

        NotificationScheduleOutboxCreationResult result = service.createOutboxes(List.of(1L));

        assertThat(result.createdCount()).isZero();
        assertThat(result.skippedDuplicateCount()).isEqualTo(1);
        assertThat(schedule.getStatus()).isEqualTo(NotificationScheduleStatus.DONE);
        verify(notificationScheduleOutboxRepository, never()).save(any());
        verify(notificationMessageBuilder, never()).build(any());
    }

    // PROCESSING 상태가 아닌 id는 outbox 생성 대상에서 제외되는지 확인한다.
    @Test
    void createOutboxesOnlyProcessesProcessingSchedules() {
        NotificationSchedule schedule = schedule(1L, "token");
        when(notificationScheduleRepository.findAllByIdInAndStatusWithUserAndTrip(
                List.of(1L, 2L),
                NotificationScheduleStatus.PROCESSING
        )).thenReturn(List.of(schedule));
        when(notificationScheduleOutboxRepository.findDedupKeysIn(List.of("notification:1"))).thenReturn(List.of());
        when(notificationMessageBuilder.build(schedule)).thenReturn(new NotificationMessage("title", "body"));

        NotificationScheduleOutboxCreationResult result = service.createOutboxes(List.of(1L, 2L));

        assertThat(result.processedCount()).isEqualTo(1);
        assertThat(result.createdCount()).isEqualTo(1);
        verify(notificationScheduleOutboxRepository).save(any());
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
                .routeOption(RouteOption.TRANSIT)
                .bufferMinutes(10)
                .finalDepartureTime(LocalDateTime.of(2026, 5, 7, 9, 0))
                .build();
        ReflectionTestUtils.setField(trip, "id", 20L);

        NotificationSchedule schedule = NotificationSchedule.builder()
                .trip(trip)
                .user(user)
                .type(NotificationType.DEPART_NOW)
                .scheduledAt(LocalDateTime.of(2026, 5, 7, 9, 0))
                .status(NotificationScheduleStatus.PROCESSING)
                .processingStartedAt(LocalDateTime.of(2026, 5, 7, 8, 59))
                .build();
        ReflectionTestUtils.setField(schedule, "id", id);
        return schedule;
    }
}
