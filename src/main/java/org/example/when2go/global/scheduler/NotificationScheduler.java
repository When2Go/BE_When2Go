package org.example.when2go.global.scheduler;

import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.notification.event.NotificationScheduleScanRequestedEvent;
import org.example.when2go.domain.notification.service.recovery.NotificationProcessingRecoveryService;
import org.example.when2go.domain.notification.service.outbox.NotificationScheduleOutboxPublisher;
import org.example.when2go.global.config.notification.NotificationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final ApplicationEventPublisher eventPublisher;
    private final NotificationProcessingRecoveryService notificationProcessingRecoveryService;
    private final NotificationProperties notificationProperties;

    // 아웃박스 row 생성
    @Scheduled(fixedDelayString = "${notification.schedule.fixed-delay-millis:60000}")
    public void processDueSchedules() {
        eventPublisher.publishEvent(new NotificationScheduleScanRequestedEvent(
                notificationProperties.getSchedule().getClaimSize()
        ));
    }

    @Scheduled(fixedDelayString = "${notification.outbox.recovery-fixed-delay-millis:60000}")
    public void recoverStuckProcessingRows() {
        notificationProcessingRecoveryService.recoverStuckProcessingRows();
    }

    @Component
    @RequiredArgsConstructor
    @ConditionalOnProperty(prefix = "notification.sqs", name = "enabled", havingValue = "true")
    static class OutboxPublisherScheduler {

        private final NotificationScheduleOutboxPublisher notificationScheduleOutboxPublisher;

        @Scheduled(fixedDelayString = "${notification.outbox.fixed-delay-millis:60000}")
        public void publishPendingOutboxes() {
            notificationScheduleOutboxPublisher.publishPendingOutboxes();
        }
    }
}
