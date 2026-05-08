package org.example.when2go.domain.notification.listener;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.example.when2go.domain.notification.event.NotificationOutboxesCreatedEvent;
import org.example.when2go.domain.notification.service.outbox.NotificationScheduleOutboxPublisher;
import org.junit.jupiter.api.Test;

class NotificationOutboxCreatedEventListenerTest {

    private final NotificationScheduleOutboxPublisher publisher =
            org.mockito.Mockito.mock(NotificationScheduleOutboxPublisher.class);
    private final NotificationOutboxCreatedEventListener listener =
            new NotificationOutboxCreatedEventListener(publisher);

    // outbox가 새로 생성된 경우 publisher를 즉시 깨우는지 확인한다.
    @Test
    void handlePublishesPendingOutboxesWhenOutboxesWereCreated() {
        listener.handle(new NotificationOutboxesCreatedEvent(1, 0, 0));

        verify(publisher).publishPendingOutboxes();
    }

    // skip만 발생한 경우 불필요하게 publisher를 실행하지 않는지 확인한다.
    @Test
    void handleDoesNotPublishWhenNoOutboxWasCreated() {
        listener.handle(new NotificationOutboxesCreatedEvent(0, 1, 1));

        verify(publisher, never()).publishPendingOutboxes();
    }
}
