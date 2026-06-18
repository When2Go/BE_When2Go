package org.example.when2go.domain.notification.listener;

import static org.mockito.Mockito.verify;

import org.example.when2go.domain.notification.event.NotificationScheduleScanRequestedEvent;
import org.example.when2go.domain.notification.service.schedule.NotificationScheduleDrainService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("release 단순화로 비활성화 - outbox 흐름 제거됨")
class NotificationScheduleScanEventListenerTest {

    private final NotificationScheduleDrainService notificationScheduleDrainService =
            org.mockito.Mockito.mock(NotificationScheduleDrainService.class);
    private final NotificationScheduleScanEventListener listener =
            new NotificationScheduleScanEventListener(notificationScheduleDrainService);

    // scan 이벤트를 받으면 processor의 drain 처리로 위임하는지 확인한다.
    @Test
    void handleDelegatesDrainToProcessor() {
        listener.handle(new NotificationScheduleScanRequestedEvent(500));

        verify(notificationScheduleDrainService).drainDueSchedules(500);
    }

    // processor 예외는 async listener 밖으로 전파하지 않는지 확인한다.
    @Test
    void handleDoesNotPropagateProcessorException() {
        org.mockito.Mockito.doThrow(new RuntimeException("failed"))
                .when(notificationScheduleDrainService)
                .drainDueSchedules(500);

        listener.handle(new NotificationScheduleScanRequestedEvent(500));

        verify(notificationScheduleDrainService).drainDueSchedules(500);
    }
}
