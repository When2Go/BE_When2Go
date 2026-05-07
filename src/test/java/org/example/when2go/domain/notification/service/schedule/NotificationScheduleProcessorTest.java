package org.example.when2go.domain.notification.service.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.example.when2go.domain.notification.dto.NotificationMessage;
import org.example.when2go.domain.notification.entity.NotificationSchedule;
import org.example.when2go.domain.notification.enums.NotificationScheduleStatus;
import org.example.when2go.domain.notification.enums.NotificationType;
import org.example.when2go.domain.notification.repository.NotificationScheduleOutboxRepository;
import org.example.when2go.domain.notification.repository.NotificationScheduleRepository;
import org.example.when2go.domain.notification.service.message.NotificationMessageBuilder;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.enums.Platform;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationScheduleProcessorTest {

    private final NotificationScheduleRepository notificationScheduleRepository =
            org.mockito.Mockito.mock(NotificationScheduleRepository.class);
    private final NotificationScheduleOutboxRepository notificationScheduleOutboxRepository =
            org.mockito.Mockito.mock(NotificationScheduleOutboxRepository.class);
    private final NotificationMessageBuilder notificationMessageBuilder =
            org.mockito.Mockito.mock(NotificationMessageBuilder.class);
    private final NotificationScheduleProcessor notificationScheduleProcessor =
            new NotificationScheduleProcessor(
                    notificationScheduleRepository,
                    notificationScheduleOutboxRepository,
                    notificationMessageBuilder
            );

    @Test
    void processDueSchedulesCreatesOutboxAndMarksScheduleDone() {
        NotificationSchedule schedule = schedule("token");
        ReflectionTestUtils.setField(schedule, "id", 1L);
        when(notificationScheduleRepository.claimDueScheduleIds(500)).thenReturn(List.of(1L));
        when(notificationScheduleRepository.findAllByIdInWithUserAndTrip(List.of(1L))).thenReturn(List.of(schedule));
        when(notificationMessageBuilder.build(schedule)).thenReturn(new NotificationMessage("title", "body"));

        int processedCount = notificationScheduleProcessor.processDueSchedules(500);

        assertThat(processedCount).isEqualTo(1);
        assertThat(schedule.getStatus()).isEqualTo(NotificationScheduleStatus.DONE);
        verify(notificationScheduleOutboxRepository).save(any());
    }

    @Test
    void processDueSchedulesSkipsOutboxWhenTokenIsMissing() {
        NotificationSchedule schedule = schedule(null);
        ReflectionTestUtils.setField(schedule, "id", 1L);
        when(notificationScheduleRepository.claimDueScheduleIds(500)).thenReturn(List.of(1L));
        when(notificationScheduleRepository.findAllByIdInWithUserAndTrip(List.of(1L))).thenReturn(List.of(schedule));

        notificationScheduleProcessor.processDueSchedules(500);

        assertThat(schedule.getStatus()).isEqualTo(NotificationScheduleStatus.DONE);
        verify(notificationScheduleOutboxRepository, never()).save(any());
        verify(notificationMessageBuilder, never()).build(any());
    }

    @Test
    void processDueSchedulesMarksDoneWhenDedupKeyAlreadyExists() {
        NotificationSchedule schedule = schedule("token");
        ReflectionTestUtils.setField(schedule, "id", 1L);
        when(notificationScheduleRepository.claimDueScheduleIds(500)).thenReturn(List.of(1L));
        when(notificationScheduleRepository.findAllByIdInWithUserAndTrip(List.of(1L))).thenReturn(List.of(schedule));
        when(notificationScheduleOutboxRepository.existsByDedupKey("notification:1")).thenReturn(true);

        notificationScheduleProcessor.processDueSchedules(500);

        assertThat(schedule.getStatus()).isEqualTo(NotificationScheduleStatus.DONE);
        verify(notificationScheduleOutboxRepository, never()).save(any());
        verify(notificationMessageBuilder, never()).build(any());
    }

    @Test
    void processDueSchedulesReturnsZeroWhenNothingClaimed() {
        when(notificationScheduleRepository.claimDueScheduleIds(500)).thenReturn(List.of());

        int processedCount = notificationScheduleProcessor.processDueSchedules(500);

        assertThat(processedCount).isZero();
        verify(notificationScheduleRepository, never()).updateProcessing(anyCollection(), eq(NotificationScheduleStatus.PROCESSING), any());
    }

    private NotificationSchedule schedule(String fcmToken) {
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
                .routeOption(RouteOption.OPTIMAL)
                .bufferMinutes(10)
                .finalDepartureTime(LocalDateTime.of(2026, 5, 7, 9, 0))
                .build();
        ReflectionTestUtils.setField(trip, "id", 20L);

        return NotificationSchedule.builder()
                .trip(trip)
                .user(user)
                .type(NotificationType.DEPART_NOW)
                .scheduledAt(LocalDateTime.of(2026, 5, 7, 9, 0))
                .build();
    }
}
