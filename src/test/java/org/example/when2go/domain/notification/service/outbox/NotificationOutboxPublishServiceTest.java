package org.example.when2go.domain.notification.service.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.example.when2go.domain.notification.client.NotificationSqsClient;
import org.example.when2go.domain.notification.dto.NotificationSqsBatchResult;
import org.example.when2go.domain.notification.dto.NotificationSqsPayload;
import org.example.when2go.domain.notification.entity.NotificationSchedule;
import org.example.when2go.domain.notification.entity.NotificationScheduleOutbox;
import org.example.when2go.domain.notification.entity.NotificationType;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.entity.Platform;
import org.example.when2go.global.config.notification.NotificationProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationOutboxPublishServiceTest {

    private final NotificationOutboxClaimService notificationOutboxClaimService =
            org.mockito.Mockito.mock(NotificationOutboxClaimService.class);
    private final NotificationOutboxStatusService notificationOutboxStatusService =
            org.mockito.Mockito.mock(NotificationOutboxStatusService.class);
    private final NotificationSqsClient notificationSqsClient =
            org.mockito.Mockito.mock(NotificationSqsClient.class);
    private final NotificationProperties notificationProperties = new NotificationProperties();
    private final NotificationOutboxPublishService notificationOutboxPublishService =
            new NotificationOutboxPublishService(
                    notificationOutboxClaimService,
                    notificationOutboxStatusService,
                    notificationSqsClient,
                    notificationProperties
            );

    // SQS 발행 성공/실패 결과에 따라 outbox 상태를 나누고, Lambda 발송에 필요한 payload 값을 포함하는지 확인한다.
    @Test
    void publishPendingOutboxesMarksSuccessAndSplitsFailuresByRetryCount() {
        NotificationScheduleOutbox success = outbox(1L, 0);
        NotificationScheduleOutbox retryable = outbox(2L, 0);
        NotificationScheduleOutbox finalFailure = outbox(3L, 3);
        when(notificationOutboxClaimService.claimPendingOutboxes(500))
                .thenReturn(List.of(success, retryable, finalFailure));
        when(notificationSqsClient.sendBatch(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new NotificationSqsBatchResult(List.of(1L), List.of(2L, 3L)));

        notificationOutboxPublishService.publishPendingOutboxes();

        verify(notificationOutboxStatusService).markPublished(List.of(1L));
        verify(notificationOutboxStatusService).markRetryableFailure(List.of(2L));
        verify(notificationOutboxStatusService).markFailed(List.of(3L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NotificationSqsPayload>> payloadCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationSqsClient).sendBatch(payloadCaptor.capture());

        NotificationSqsPayload payload = payloadCaptor.getValue().get(0);
        assertThat(payload.outboxId()).isEqualTo("1");
        assertThat(payload.fcmToken()).isEqualTo("token");
        assertThat(payload.data().tripId()).isEqualTo("21");
        assertThat(payload.data().type()).isEqualTo("DEPART_NOW");
    }

    private NotificationScheduleOutbox outbox(Long id, int retryCount) {
        AppUser user = AppUser.builder()
                .deviceId("device-id-" + id)
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
