package org.example.when2go.domain.notification.listener;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.example.when2go.domain.notification.event.NotificationOutboxRepublishRequestedEvent;
import org.example.when2go.domain.notification.service.outbox.NotificationOutboxPublishService;
import org.junit.jupiter.api.Test;

class NotificationOutboxRepublishRequestedEventListenerTest {

    private final NotificationOutboxPublishService publishService = mock(NotificationOutboxPublishService.class);
    private final NotificationOutboxRepublishRequestedEventListener listener =
            new NotificationOutboxRepublishRequestedEventListener(publishService);

    @Test
    void handleInvokesPublishWhenRecoveredCountPositive() {
        listener.handle(new NotificationOutboxRepublishRequestedEvent(3));

        verify(publishService).publishPendingOutboxes();
    }

    @Test
    void handleSkipsWhenRecoveredCountZero() {
        listener.handle(new NotificationOutboxRepublishRequestedEvent(0));

        verify(publishService, never()).publishPendingOutboxes();
    }

    @Test
    void handleSwallowsExceptionFromPublish() {
        doThrow(new RuntimeException("boom")).when(publishService).publishPendingOutboxes();

        // 예외를 삼켜야 하므로 assertion 없이 정상 리턴을 검증
        listener.handle(new NotificationOutboxRepublishRequestedEvent(1));

        verify(publishService).publishPendingOutboxes();
    }
}
