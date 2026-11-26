package org.example.when2go.domain.notification.service.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.example.when2go.domain.notification.client.NotificationPushClient;
import org.example.when2go.domain.notification.dto.PushMessage;
import org.example.when2go.domain.notification.dto.PushSendResult;
import org.example.when2go.domain.notification.dto.PushSendResultType;
import org.example.when2go.domain.notification.entity.NotificationSchedule;
import org.example.when2go.domain.notification.entity.NotificationScheduleOutbox;
import org.example.when2go.domain.notification.entity.NotificationType;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.entity.Platform;
import org.example.when2go.global.config.notification.NotificationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationOutboxPublishServiceTest {

    private final NotificationOutboxClaimService claimService = mock(NotificationOutboxClaimService.class);
    private final NotificationOutboxStatusService statusService = mock(NotificationOutboxStatusService.class);
    private final NotificationPushClient pushClient = mock(NotificationPushClient.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<NotificationPushClient> pushClientProvider = mock(ObjectProvider.class);
    private final NotificationProperties properties = new NotificationProperties();
    private final NotificationOutboxPublishService publishService = new NotificationOutboxPublishService(
            claimService,
            statusService,
            pushClientProvider,
            properties,
            Runnable::run
    );

    @Test
    void publishPendingOutboxesMarksSentOnSuccess() {
        NotificationScheduleOutbox outbox = outbox(1L, 0);
        when(claimService.claimPendingOutboxes(500)).thenReturn(List.of(outbox));
        when(pushClientProvider.getIfAvailable()).thenReturn(pushClient);
        when(pushClient.send(any(PushMessage.class)))
                .thenReturn(PushSendResult.success("msg-1"));

        publishService.publishPendingOutboxes();

        verify(statusService).markSent(1L);
    }

    @Test
    void publishPendingOutboxesLeavesProcessingWhenSendFails() {
        NotificationScheduleOutbox outbox = outbox(2L, 0);
        when(claimService.claimPendingOutboxes(500)).thenReturn(List.of(outbox));
        when(pushClientProvider.getIfAvailable()).thenReturn(pushClient);
        when(pushClient.send(any(PushMessage.class)))
                .thenReturn(PushSendResult.failure(PushSendResultType.TRANSIENT_FAILURE, "UNAVAILABLE", "temporary"));

        publishService.publishPendingOutboxes();

        verify(statusService, never()).markSent(any());
        verify(statusService, never()).markRetryableFailure(any());
        verify(statusService, never()).markFailed(any());
    }

    @Test
    void publishPendingOutboxesLeavesProcessingWhenSendThrows() {
        NotificationScheduleOutbox outbox = outbox(3L, 0);
        when(claimService.claimPendingOutboxes(500)).thenReturn(List.of(outbox));
        when(pushClientProvider.getIfAvailable()).thenReturn(pushClient);
        when(pushClient.send(any(PushMessage.class))).thenThrow(new RuntimeException("boom"));

        publishService.publishPendingOutboxes();

        verify(statusService, never()).markSent(any());
        verify(statusService, never()).markRetryableFailure(any());
        verify(statusService, never()).markFailed(any());
    }

    @Test
    void publishPendingOutboxesSkipsWhenPushClientMissing() {
        NotificationScheduleOutbox outbox = outbox(4L, 0);
        when(claimService.claimPendingOutboxes(500)).thenReturn(List.of(outbox));
        when(pushClientProvider.getIfAvailable()).thenReturn(null);

        publishService.publishPendingOutboxes();

        verify(pushClient, never()).send(any());
        verify(statusService, never()).markSent(any());
    }

    @Test
    void publishPendingOutboxesSendsConcurrentlyAndIsolatesFailure() throws Exception {
        List<NotificationScheduleOutbox> outboxes = List.of(outbox(5L, 0), outbox(6L, 0), outbox(7L, 0));
        when(claimService.claimPendingOutboxes(500)).thenReturn(outboxes);
        when(pushClientProvider.getIfAvailable()).thenReturn(pushClient);
        // 세 발송이 동시에 진행 중이어야만 latch가 풀린다. 순차 실행이면 타임아웃으로 실패한다.
        CountDownLatch allInFlight = new CountDownLatch(3);
        when(pushClient.send(any(PushMessage.class))).thenAnswer(invocation -> {
            allInFlight.countDown();
            if (!allInFlight.await(2, TimeUnit.SECONDS)) {
                return PushSendResult.failure(PushSendResultType.TRANSIENT_FAILURE, "TIMEOUT", "not concurrent");
            }
            if (invocation.<PushMessage>getArgument(0).title().equals("boom")) {
                throw new RuntimeException("boom");
            }
            return PushSendResult.success("msg");
        });
        ReflectionTestUtils.setField(outboxes.get(1), "title", "boom");

        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            new NotificationOutboxPublishService(
                    claimService,
                    statusService,
                    pushClientProvider,
                    properties,
                    virtualExecutor
            ).publishPendingOutboxes();
        }

        verify(statusService).markSent(5L);
        verify(statusService, never()).markSent(6L);
        verify(statusService).markSent(7L);
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
