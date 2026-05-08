package org.example.when2go.global.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.example.when2go.domain.notification.event.NotificationScheduleScanRequestedEvent;
import org.example.when2go.domain.notification.service.recovery.NotificationProcessingRecoveryService;
import org.example.when2go.global.config.notification.NotificationProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class NotificationSchedulerTest {

    private final ApplicationEventPublisher eventPublisher =
            org.mockito.Mockito.mock(ApplicationEventPublisher.class);
    private final NotificationProcessingRecoveryService notificationProcessingRecoveryService =
            org.mockito.Mockito.mock(NotificationProcessingRecoveryService.class);
    private final NotificationProperties notificationProperties = new NotificationProperties();
    private final NotificationScheduler notificationScheduler =
            new NotificationScheduler(
                    eventPublisher,
                    notificationProcessingRecoveryService,
                    notificationProperties
            );

    // due schedule scheduler가 직접 처리하지 않고 scan 요청 이벤트만 발행하는지 확인한다.
    @Test
    void processDueSchedulesPublishesScanRequestedEvent() {
        notificationProperties.getSchedule().setClaimSize(123);

        notificationScheduler.processDueSchedules();

        ArgumentCaptor<NotificationScheduleScanRequestedEvent> eventCaptor =
                ArgumentCaptor.forClass(NotificationScheduleScanRequestedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().limit()).isEqualTo(123);
    }
}
