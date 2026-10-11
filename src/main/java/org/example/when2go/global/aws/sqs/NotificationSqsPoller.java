package org.example.when2go.global.aws.sqs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.notification.client.NotificationPushClient;
import org.example.when2go.domain.notification.dto.PushMessage;
import org.example.when2go.domain.notification.dto.PushSendResult;
import org.example.when2go.global.aws.sqs.NotificationSqsSender.NotificationSqsMessage;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

// SQS 메인 큐를 long polling 해서 FCM으로 발송한다.
// 성공한 메시지만 삭제하고, 실패한 메시지는 남겨둬서 visibility timeout 후 재수신 → maxReceiveCount 초과 시 DLQ로 이동.
@Slf4j
public class NotificationSqsPoller {

    private static final int MAX_MESSAGES = 10;
    private static final int WAIT_TIME_SECONDS = 10;

    private final SqsClient sqsClient;
    private final String queueUrl;
    private final NotificationPushClient pushClient;
    private final ObjectMapper objectMapper;

    public NotificationSqsPoller(
            SqsClient sqsClient,
            String queueUrl,
            NotificationPushClient pushClient,
            ObjectMapper objectMapper
    ) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
        this.pushClient = pushClient;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 1000)
    public void poll() {
        sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(MAX_MESSAGES)
                        .waitTimeSeconds(WAIT_TIME_SECONDS)
                        .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)
                        .build())
                .messages()
                .forEach(this::handle);
    }

    private void handle(Message message) {
        String receiveCount = message.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
        NotificationSqsMessage payload;
        try {
            payload = objectMapper.readValue(message.body(), NotificationSqsMessage.class);
        } catch (JsonProcessingException e) {
            log.warn("event=notification.sqs_parse_failed sqsMessageId={} receiveCount={}",
                    message.messageId(), receiveCount, e);
            return;
        }

        PushSendResult result = pushClient.send(new PushMessage(
                payload.token(),
                payload.title(),
                payload.body(),
                payload.data()
        ));
        if (!result.success()) {
            log.warn("event=notification.sqs_send_failed outboxId={} sqsMessageId={} receiveCount={} errorCode={}",
                    payload.outboxId(), message.messageId(), receiveCount, result.errorCode());
            return;
        }

        sqsClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build());
        log.info("event=notification.sqs_sent outboxId={} sqsMessageId={} receiveCount={} messageId={}",
                payload.outboxId(), message.messageId(), receiveCount, result.messageId());
    }
}
