package org.example.when2go.global.scheduler;

import lombok.RequiredArgsConstructor;
import org.example.when2go.global.config.notification.NotificationProperties;
import org.example.when2go.domain.notification.service.recovery.NotificationProcessingRecoveryService;
import org.example.when2go.domain.notification.service.outbox.NotificationScheduleOutboxPublisher;
import org.example.when2go.domain.notification.service.schedule.NotificationScheduleProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final NotificationScheduleProcessor notificationScheduleProcessor;
    private final NotificationProcessingRecoveryService notificationProcessingRecoveryService;
    private final NotificationProperties notificationProperties;

    @Scheduled(fixedDelayString = "${notification.schedule.fixed-delay-millis:60000}")
    public void processDueSchedules() {
        notificationScheduleProcessor.processDueSchedules(notificationProperties.getSchedule().getClaimSize());
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
