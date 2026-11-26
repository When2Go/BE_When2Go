package org.example.when2go.domain.notification.service.outbox;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.notification.client.NotificationPushClient;
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
    private final Executor notificationPushExecutor;

    public void publishPendingOutboxes() {
        List<NotificationScheduleOutbox> outboxes = notificationOutboxClaimService.claimPendingOutboxes(
                notificationProperties.getOutbox().getClaimSize()
        );
        if (outboxes.isEmpty()) {
            return;
        }

        NotificationPushClient pushClient = notificationPushClientProvider.getIfAvailable();
        if (pushClient == null) {
            log.warn("event=notification.push_client_unavailable outboxCount={}", outboxes.size());
            return;
        }

        // 발송 코드는 블로킹 그대로 두고, outbox마다 가상 스레드에서 동시에 보낸 뒤 전부 끝날 때까지 기다린다.
        CompletableFuture<?>[] sends = outboxes.stream()
                .map(outbox -> CompletableFuture.runAsync(() -> sendOne(pushClient, outbox), notificationPushExecutor))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(sends).join();
    }

    private void sendOne(NotificationPushClient pushClient, NotificationScheduleOutbox outbox) {
        try {
            PushSendResult result = pushClient.send(new PushMessage(
                    outbox.getUser().getFcmToken(),
                    outbox.getTitle(),
                    outbox.getBody(),
                    Map.of(
                            "tripId", String.valueOf(outbox.getTrip().getId()),
                            "type", outbox.getType().name()
                    )
            ));
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
}
