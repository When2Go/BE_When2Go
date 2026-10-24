package org.example.when2go.lambda.notification;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.ErrorCode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

// SQS 알림 큐 → FCM 발송 Lambda. SQS 이벤트 소스 매핑(ReportBatchItemFailures)으로 호출된다.
// batchItemFailures에 담긴 메시지만 큐에 남아 재시도되고, 나머지는 Lambda가 자동 삭제한다.
// (maxReceiveCount 초과 시 DLQ로 이동)
public class NotificationSqsHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // 스케줄러의 NotificationSqsSender.NotificationSqsMessage와 같은 JSON 형태
    public record QueueMessage(Long outboxId, String token, String title, String body, Map<String, String> data) {
    }

    @FunctionalInterface
    interface Sender {
        String send(QueueMessage message) throws FirebaseMessagingException;
    }

    private final Sender sender;

    // Lambda 실행 환경이 처음 뜰 때 한 번만 호출된다.
    public NotificationSqsHandler() {
        FirebaseMessaging firebaseMessaging = initFirebase();
        this.sender = message -> firebaseMessaging.send(toFirebaseMessage(message));
    }

    NotificationSqsHandler(Sender sender) {
        this.sender = sender;
    }

    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<SQSBatchResponse.BatchItemFailure> failures = new ArrayList<>();
        for (SQSMessage message : event.getRecords()) {
            if (!handle(message)) {
                failures.add(new SQSBatchResponse.BatchItemFailure(message.getMessageId()));
            }
        }
        return new SQSBatchResponse(failures);
    }

    // true: 처리 끝 (큐에서 삭제), false: 재시도
    private boolean handle(SQSMessage message) {
        String sqsId = message.getMessageId();
        String receiveCount = message.getAttributes() == null
                ? null
                : message.getAttributes().get("ApproximateReceiveCount");

        QueueMessage payload;
        try {
            payload = OBJECT_MAPPER.readValue(message.getBody(), QueueMessage.class);
        } catch (Exception e) {
            // 재시도해도 안 고쳐지지만 DLQ에 남겨서 원인을 확인할 수 있게 한다.
            log("event=notification.sqs_parse_failed sqsMessageId=%s receiveCount=%s", sqsId, receiveCount);
            return false;
        }

        try {
            String messageId = sender.send(payload);
            log("event=notification.sqs_sent outboxId=%s sqsMessageId=%s receiveCount=%s messageId=%s",
                    payload.outboxId(), sqsId, receiveCount, messageId);
            return true;
        } catch (FirebaseMessagingException e) {
            boolean retry = isTransient(e.getErrorCode(), e.getMessagingErrorCode());
            // 토큰 만료, 잘못된 요청 등은 재시도해도 실패하므로 버린다.
            log("event=notification.sqs_send_%s outboxId=%s sqsMessageId=%s receiveCount=%s"
                            + " errorCode=%s messagingErrorCode=%s error=%s",
                    retry ? "retry" : "dropped", payload.outboxId(), sqsId, receiveCount,
                    e.getErrorCode(), e.getMessagingErrorCode(), e.getMessage());
            return !retry;
        } catch (RuntimeException e) {
            log("event=notification.sqs_send_exception outboxId=%s sqsMessageId=%s receiveCount=%s error=%s",
                    payload.outboxId(), sqsId, receiveCount, e);
            return false;
        }
    }

    // 잠시 뒤 다시 보내면 성공할 수 있는 에러 (스케줄러 FcmErrorClassifier의 TRANSIENT_FAILURE와 같은 기준)
    static boolean isTransient(ErrorCode errorCode, MessagingErrorCode messagingErrorCode) {
        if (messagingErrorCode != null) {
            return switch (messagingErrorCode) {
                case INTERNAL, QUOTA_EXCEEDED, UNAVAILABLE -> true;
                default -> false;
            };
        }
        if (errorCode != null) {
            return switch (errorCode) {
                case INTERNAL, UNAVAILABLE, DEADLINE_EXCEEDED, RESOURCE_EXHAUSTED -> true;
                default -> false;
            };
        }
        return false;
    }

    private static Message toFirebaseMessage(QueueMessage message) {
        return Message.builder()
                .setToken(message.token())
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putAllData(message.data() == null ? Map.of() : message.data())
                .build();
    }

    // 환경 변수 FCM_CREDENTIALS_JSON(원본 JSON) 또는 FCM_CREDENTIALS_BASE64 중 하나가 필요하다.
    private static FirebaseMessaging initFirebase() {
        byte[] credentials = readCredentials(
                System.getenv("FCM_CREDENTIALS_JSON"),
                System.getenv("FCM_CREDENTIALS_BASE64")
        );
        try {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(credentials)))
                    .build();
            return FirebaseMessaging.getInstance(FirebaseApp.initializeApp(options));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize Firebase credentials", e);
        }
    }

    static byte[] readCredentials(String json, String base64) {
        if (json != null && !json.isBlank()) {
            return json.getBytes(StandardCharsets.UTF_8);
        }
        if (base64 != null && !base64.isBlank()) {
            return Base64.getDecoder().decode(base64.trim());
        }
        throw new IllegalStateException("FCM_CREDENTIALS_JSON or FCM_CREDENTIALS_BASE64 is required");
    }

    // Lambda는 stdout을 CloudWatch Logs로 보낸다.
    private static void log(String format, Object... args) {
        System.out.println(String.format(format, args));
    }
}
