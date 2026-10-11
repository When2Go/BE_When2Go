package org.example.when2go.domain.notification.service.outbox;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.notification.client.NotificationPushClient;
import org.example.when2go.domain.notification.client.NotificationQueueClient;
import org.example.when2go.domain.notification.dto.PushMessage;
import org.example.when2go.domain.notification.dto.PushSendResult;
import org.example.when2go.domain.notification.entity.NotificationScheduleOutbox;
import org.example.when2go.global.config.notification.NotificationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationOutboxPublishService {

    private final NotificationOutboxClaimService notificationOutboxClaimService;
    private final NotificationOutboxStatusService notificationOutboxStatusService;
    private final ObjectProvider<NotificationPushClient> notificationPushClientProvider;
    private final NotificationProperties notificationProperties;
    private final ObjectProvider<NotificationQueueClient> notificationQueueClientProvider;

    public void publishPendingOutboxes() {
        List<NotificationScheduleOutbox> outboxes = notificationOutboxClaimService.claimPendingOutboxes(
                notificationProperties.getOutbox().getClaimSize()
        );
        if (outboxes.isEmpty()) {
            return;
        }

        // SQS가 켜져 있으면 큐에 넣는 것까지만 하고, FCM 발송은 큐 폴러가 담당
        NotificationQueueClient queueClient = notificationQueueClientProvider.getIfAvailable();
        if (queueClient != null) {
            for (NotificationScheduleOutbox outbox : outboxes) {
                enqueueOne(queueClient, outbox);
            }
            return;
        }

        NotificationPushClient pushClient = notificationPushClientProvider.getIfAvailable();
        if (pushClient == null) {
            log.warn("event=notification.push_client_unavailable outboxCount={}", outboxes.size());
            return;
        }

        for (NotificationScheduleOutbox outbox : outboxes) {
            sendOne(pushClient, outbox);
        }
    }

    private void enqueueOne(NotificationQueueClient queueClient, NotificationScheduleOutbox outbox) {
        try {
            String sqsMessageId = queueClient.enqueue(outbox.getId(), toPushMessage(outbox));
            notificationOutboxStatusService.markSent(outbox.getId());
            log.info("event=notification.enqueued outboxId={} sqsMessageId={}", outbox.getId(), sqsMessageId);
        } catch (RuntimeException e) {
            log.warn("event=notification.enqueue_failed outboxId={}", outbox.getId(), e);
        }
    }

    private void sendOne(NotificationPushClient pushClient, NotificationScheduleOutbox outbox) {
        try {
            PushSendResult result = pushClient.send(toPushMessage(outbox));
            if (result.success()) {
                notificationOutboxStatusService.markSent(outbox.getId());
                log.info(
                        "event=notification.sent outboxId={} type={} messageId={}",
                        outbox.getId(),
                        outbox.getType(),
                        result.messageId()
                );
            } else {
                log.warn(
                        "event=notification.send_failed outboxId={} type={} errorCode={} errorMessage={}",
                        outbox.getId(),
                        outbox.getType(),
                        result.errorCode(),
                        result.errorMessage()
                );
            }
        } catch (RuntimeException e) {
            log.warn("event=notification.send_exception outboxId={}", outbox.getId(), e);
        }
    }

    private PushMessage toPushMessage(NotificationScheduleOutbox outbox) {
        return new PushMessage(
                outbox.getUser().getFcmToken(),
                outbox.getTitle(),
                outbox.getBody(),
                Map.of(
                        "tripId", String.valueOf(outbox.getTrip().getId()),
                        "type", outbox.getType().name()
                )
        );
    }
}
