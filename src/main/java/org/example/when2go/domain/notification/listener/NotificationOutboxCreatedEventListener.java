package org.example.when2go.domain.notification.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.notification.event.NotificationOutboxesCreatedEvent;
import org.example.when2go.domain.notification.service.outbox.NotificationOutboxPublishService;
import org.example.when2go.global.config.notification.NotificationAsyncConfig;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationOutboxCreatedEventListener {

    private final NotificationOutboxPublishService notificationOutboxPublishService;

    @Async(NotificationAsyncConfig.OUTBOX_PUBLISHER_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NotificationOutboxesCreatedEvent event) {
        if (event.createdCount() <= 0) {
            return;
        }

        try {
            // PENDING outbox를 FCM으로 발송
            notificationOutboxPublishService.publishPendingOutboxes();
        } catch (RuntimeException e) {
            log.warn("event=notification.outbox_created_listener_failed createdCount={}", event.createdCount(), e);
        }
    }
}
