package org.example.when2go.domain.notification.client;

import java.util.List;
import org.example.when2go.domain.notification.dto.NotificationSqsBatchResult;
import org.example.when2go.domain.notification.dto.NotificationSqsPayload;

public interface NotificationSqsClient {

    NotificationSqsBatchResult sendBatch(List<NotificationSqsPayload> payloads);
}
