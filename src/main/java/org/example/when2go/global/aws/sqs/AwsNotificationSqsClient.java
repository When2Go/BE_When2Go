package org.example.when2go.global.aws.sqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.example.when2go.domain.notification.dto.NotificationSqsBatchResult;
import org.example.when2go.domain.notification.dto.NotificationSqsPayload;
import org.example.when2go.domain.notification.client.NotificationSqsClient;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;

public class AwsNotificationSqsClient implements NotificationSqsClient {

    private final SqsClient sqsClient;
    private final String queueUrl;
    private final ObjectMapper objectMapper;

    public AwsNotificationSqsClient(SqsClient sqsClient, String queueUrl) {
        this.sqsClient = sqsClient;
        if (!StringUtils.hasText(queueUrl)) {
            throw new IllegalArgumentException("notification.sqs.queue-url must not be blank when SQS is enabled");
        }
        this.queueUrl = queueUrl;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public NotificationSqsBatchResult sendBatch(List<NotificationSqsPayload> payloads) {
        Map<String, NotificationSqsPayload> payloadByEntryId = payloads.stream()
                .collect(Collectors.toMap(NotificationSqsPayload::outboxId, Function.identity()));
        List<SendMessageBatchRequestEntry> entries = payloads.stream()
                .map(this::toEntry)
                .toList();

        SendMessageBatchResponse response = sqsClient.sendMessageBatch(SendMessageBatchRequest.builder()
                .queueUrl(queueUrl)
                .entries(entries)
                .build());

        List<Long> failedIds = response.failed().stream()
                .map(BatchResultErrorEntry::id)
                .map(Long::valueOf)
                .toList();

        List<Long> successIds = new ArrayList<>(payloadByEntryId.keySet().stream()
                .map(Long::valueOf)
                .toList());
        successIds.removeAll(failedIds);

        return new NotificationSqsBatchResult(successIds, failedIds);
    }

    private SendMessageBatchRequestEntry toEntry(NotificationSqsPayload payload) {
        try {
            return SendMessageBatchRequestEntry.builder()
                    .id(payload.outboxId())
                    .messageBody(objectMapper.writeValueAsString(payload))
                    .build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize notification SQS payload", e);
        }
    }
}
