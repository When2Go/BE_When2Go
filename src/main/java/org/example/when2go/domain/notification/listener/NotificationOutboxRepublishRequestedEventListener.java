package org.example.when2go.domain.notification.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.notification.event.NotificationOutboxRepublishRequestedEvent;
import org.example.when2go.domain.notification.service.outbox.NotificationOutboxPublishService;
import org.example.when2go.global.config.notification.NotificationAsyncConfig;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationOutboxRepublishRequestedEventListener {

    private final NotificationOutboxPublishService notificationOutboxPublishService;

    @Async(NotificationAsyncConfig.OUTBOX_PUBLISHER_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(NotificationOutboxRepublishRequestedEvent event) {
        if (event.recoveredCount() <= 0) {
            return;
        }
        try {
            notificationOutboxPublishService.publishPendingOutboxes();
        } catch (RuntimeException e) {
            log.warn(
                    "event=notification.outbox_republish_listener_failed recoveredCount={}",
                    event.recoveredCount(),
                    e
            );
        }
    }
}
