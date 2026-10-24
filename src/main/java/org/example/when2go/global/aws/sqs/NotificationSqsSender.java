package org.example.when2go.global.aws.sqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.example.when2go.domain.notification.client.NotificationQueueClient;
import org.example.when2go.domain.notification.dto.PushMessage;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

// outbox 한 건을 SQS 메인 큐에 넣는다. 실제 FCM 발송은 notification-lambda가 담당.
public class NotificationSqsSender implements NotificationQueueClient {

    private final SqsClient sqsClient;
    private final String queueUrl;
    private final ObjectMapper objectMapper;

    public NotificationSqsSender(SqsClient sqsClient, String queueUrl, ObjectMapper objectMapper) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
        this.objectMapper = objectMapper;
    }

    @Override
    public String enqueue(Long outboxId, PushMessage message) {
        return sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(toJson(new NotificationSqsMessage(
                        outboxId,
                        message.token(),
                        message.title(),
                        message.body(),
                        message.data()
                )))
                .build()).messageId();
    }

    private String toJson(NotificationSqsMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize notification SQS message", e);
        }
    }

    public record NotificationSqsMessage(
            Long outboxId,
            String token,
            String title,
            String body,
            Map<String, String> data
    ) {
    }
}
