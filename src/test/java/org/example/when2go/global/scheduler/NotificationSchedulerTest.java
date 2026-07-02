package org.example.when2go.global.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.example.when2go.domain.notification.event.NotificationScheduleScanRequestedEvent;
import org.example.when2go.domain.notification.service.outbox.NotificationOutboxRecoveryService;
import org.example.when2go.global.config.notification.NotificationProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class NotificationSchedulerTest {

    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final NotificationOutboxRecoveryService notificationOutboxRecoveryService =
            mock(NotificationOutboxRecoveryService.class);
    private final NotificationProperties notificationProperties = new NotificationProperties();
    private final NotificationScheduler scheduler = new NotificationScheduler(
            eventPublisher,
            notificationOutboxRecoveryService,
            notificationProperties
    );

    @Test
    void processDueSchedulesPublishesScanEventWithConfiguredClaimSize() {
        notificationProperties.getSchedule().setClaimSize(123);

        scheduler.processDueSchedules();

        ArgumentCaptor<NotificationScheduleScanRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(NotificationScheduleScanRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(eventCaptor.getValue().limit()).isEqualTo(123);
    }

    @Test
    void recoverStuckOutboxesDelegatesToRecoveryService() {
        scheduler.recoverStuckOutboxes();

        verify(notificationOutboxRecoveryService).recoverStuckOutboxes();
    }
}
