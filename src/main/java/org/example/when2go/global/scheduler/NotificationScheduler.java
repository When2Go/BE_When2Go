package org.example.when2go.global.scheduler;

import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.notification.event.NotificationScheduleScanRequestedEvent;
import org.example.when2go.domain.notification.service.outbox.NotificationOutboxRecoveryService;
import org.example.when2go.domain.notification.service.outbox.NotificationOutboxPublishService;
import org.example.when2go.global.config.notification.NotificationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private final ApplicationEventPublisher eventPublisher;
    private final NotificationOutboxRecoveryService notificationOutboxRecoveryService;
    private final NotificationProperties notificationProperties;

    // 일정 시간이 된 알림 스케쥴을 찾아 아웃박스 row 생성 및 SQS에 publish
    @Scheduled(fixedDelayString = "${notification.schedule.fixed-delay-millis:60000}")
    public void processDueSchedules() {
        eventPublisher.publishEvent(new NotificationScheduleScanRequestedEvent(
                notificationProperties.getSchedule().getClaimSize()
        ));
    }

    // PROCESSING 상태로 멈춰있는 outbox row를 다시 PENDING으로 되돌린다
    @Scheduled(fixedDelayString = "${notification.outbox.recovery-fixed-delay-millis:60000}")
    public void recoverStuckProcessingRows() {
        notificationOutboxRecoveryService.recoverStuckProcessingRows();
    }

    @Component
    @RequiredArgsConstructor
    // 내부 클래스인 이유: 해당 스케쥴러의 Bean 등록을 위함 / 로컬 환경에서 SQS 의존성 없이 실행하기 위함
    @ConditionalOnProperty(prefix = "notification.sqs", name = "enabled", havingValue = "true")
    // outbox 테이블에서 PENDING 상태인 row를 claim해서 SQS로 발행하는 역할
    static class OutboxPublisherScheduler {

        private final NotificationOutboxPublishService notificationOutboxPublishService;

        @Scheduled(fixedDelayString = "${notification.outbox.fixed-delay-millis:60000}")
        public void publishPendingOutboxes() {
            notificationOutboxPublishService.publishPendingOutboxes();
        }
    }
}
