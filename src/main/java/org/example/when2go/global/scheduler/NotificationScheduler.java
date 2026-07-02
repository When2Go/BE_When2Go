package org.example.when2go.global.scheduler;

import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.notification.event.NotificationScheduleScanRequestedEvent;
import org.example.when2go.domain.notification.service.outbox.NotificationOutboxRecoveryService;
import org.example.when2go.global.config.notification.NotificationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final ApplicationEventPublisher eventPublisher;
    private final NotificationOutboxRecoveryService notificationOutboxRecoveryService;
    private final NotificationProperties notificationProperties;

    @Scheduled(fixedDelayString = "${notification.schedule.fixed-delay-millis:60000}")
    public void processDueSchedules() {
        eventPublisher.publishEvent(new NotificationScheduleScanRequestedEvent(
                notificationProperties.getSchedule().getClaimSize()
        ));
    }

    @Scheduled(fixedDelayString = "${notification.outbox.recovery-fixed-delay-millis:60000}")
    public void recoverStuckOutboxes() {
        notificationOutboxRecoveryService.recoverStuckOutboxes();
    }
}
