package org.example.when2go.lambda.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.MessagingErrorCode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.example.when2go.lambda.notification.NotificationSqsHandler.QueueMessage;
import org.junit.jupiter.api.Test;

class NotificationSqsHandlerTest {

    private final List<QueueMessage> sent = new ArrayList<>();

    private final NotificationSqsHandler handler = new NotificationSqsHandler(message -> {
        sent.add(message);
        if ("boom".equals(message.token())) {
            throw new IllegalStateException("unexpected");
        }
        return "fcm-" + message.outboxId();
    });

    @Test
    void successfulMessagesAreNotReportedAsFailures() {
        SQSBatchResponse response = handle(
                sqsMessage("m1", json(1L, "token-1")),
                sqsMessage("m2", json(2L, "token-2"))
        );

        assertThat(failedIds(response)).isEmpty();
        assertThat(sent).extracting(QueueMessage::outboxId).containsExactly(1L, 2L);
    }

    @Test
    void readsPayloadFields() {
        handle(sqsMessage("m1",
                "{\"outboxId\":7,\"token\":\"t\",\"title\":\"출발\",\"body\":\"지금 출발하세요\","
                        + "\"data\":{\"tripId\":\"3\",\"type\":\"DEPARTURE\"},\"unknown\":1}"));

        assertThat(sent).containsExactly(new QueueMessage(
                7L, "t", "출발", "지금 출발하세요", Map.of("tripId", "3", "type", "DEPARTURE")));
    }

    @Test
    void unparsableBodyIsRetriedSoItEndsUpInDlq() {
        SQSBatchResponse response = handle(
                sqsMessage("bad", "not-json"),
                sqsMessage("ok", json(1L, "token-1"))
        );

        assertThat(failedIds(response)).containsExactly("bad");
        assertThat(sent).hasSize(1);
    }

    @Test
    void unexpectedExceptionIsRetriedWithoutStoppingTheBatch() {
        SQSBatchResponse response = handle(
                sqsMessage("boom", json(1L, "boom")),
                sqsMessage("ok", json(2L, "token-2"))
        );

        assertThat(failedIds(response)).containsExactly("boom");
        assertThat(sent).hasSize(2);
    }

    @Test
    void onlyTemporaryFcmErrorsAreRetried() {
        assertThat(NotificationSqsHandler.isTransient(null, MessagingErrorCode.UNAVAILABLE)).isEqualTo(true);
        assertThat(NotificationSqsHandler.isTransient(null, MessagingErrorCode.QUOTA_EXCEEDED)).isEqualTo(true);
        assertThat(NotificationSqsHandler.isTransient(ErrorCode.DEADLINE_EXCEEDED, null)).isEqualTo(true);

        assertThat(NotificationSqsHandler.isTransient(null, MessagingErrorCode.UNREGISTERED)).isEqualTo(false);
        assertThat(NotificationSqsHandler.isTransient(ErrorCode.INVALID_ARGUMENT, MessagingErrorCode.INVALID_ARGUMENT))
                .isEqualTo(false);
        assertThat(NotificationSqsHandler.isTransient(null, MessagingErrorCode.SENDER_ID_MISMATCH)).isEqualTo(false);
        assertThat(NotificationSqsHandler.isTransient(null, null)).isEqualTo(false);
    }

    @Test
    void readsJsonOrBase64Credentials() {
        assertThat(new String(NotificationSqsHandler.readCredentials("{\"a\":1}", null))).isEqualTo("{\"a\":1}");
        assertThat(new String(NotificationSqsHandler.readCredentials("", "eyJhIjoxfQ=="))).isEqualTo("{\"a\":1}");
    }

    private SQSBatchResponse handle(SQSMessage... messages) {
        SQSEvent event = new SQSEvent();
        event.setRecords(Arrays.asList(messages));
        return handler.handleRequest(event, null);
    }

    private static List<String> failedIds(SQSBatchResponse response) {
        return response.getBatchItemFailures().stream()
                .map(SQSBatchResponse.BatchItemFailure::getItemIdentifier)
                .toList();
    }

    private static SQSMessage sqsMessage(String id, String body) {
        SQSMessage message = new SQSMessage();
        message.setMessageId(id);
        message.setBody(body);
        message.setAttributes(Map.of("ApproximateReceiveCount", "1"));
        return message;
    }

    private static String json(Long outboxId, String token) {
        return "{\"outboxId\":" + outboxId + ",\"token\":\"" + token
                + "\",\"title\":\"제목\",\"body\":\"본문\",\"data\":{\"tripId\":\"1\"}}";
    }
}
