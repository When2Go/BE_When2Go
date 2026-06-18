package org.example.when2go.domain.notification.client;

import org.example.when2go.domain.notification.dto.PushMessage;
import org.example.when2go.domain.notification.dto.PushSendResult;

public interface NotificationPushClient {

    PushSendResult send(PushMessage message);
}
