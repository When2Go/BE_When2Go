package org.example.when2go.domain.notification.listener;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.example.when2go.domain.notification.event.NotificationOutboxesCreatedEvent;
import org.example.when2go.domain.notification.service.outbox.NotificationOutboxPublishService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("release 단순화로 비활성화 - outbox 흐름 제거됨")
class NotificationOutboxCreatedEventListenerTest {

    private final NotificationOutboxPublishService notificationOutboxPublishService =
            org.mockito.Mockito.mock(NotificationOutboxPublishService.class);
    private final NotificationOutboxCreatedEventListener listener =
            new NotificationOutboxCreatedEventListener(notificationOutboxPublishService);

    // outbox가 새로 생성된 경우 publish service를 즉시 깨우는지 확인한다.
    @Test
    void handlePublishesPendingOutboxesWhenOutboxesWereCreated() {
        listener.handle(new NotificationOutboxesCreatedEvent(1, 0, 0));

        verify(notificationOutboxPublishService).publishPendingOutboxes();
    }

    // skip만 발생한 경우 불필요하게 publish service를 실행하지 않는지 확인한다.
    @Test
    void handleDoesNotPublishWhenNoOutboxWasCreated() {
        listener.handle(new NotificationOutboxesCreatedEvent(0, 1, 1));

        verify(notificationOutboxPublishService, never()).publishPendingOutboxes();
    }
}
