package org.example.when2go.domain.notification.client;

import org.example.when2go.domain.notification.dto.PushMessage;

public interface NotificationQueueClient {

    String enqueue(Long outboxId, PushMessage message);
}
