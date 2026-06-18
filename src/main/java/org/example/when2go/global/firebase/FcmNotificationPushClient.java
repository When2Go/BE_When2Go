package org.example.when2go.global.firebase;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.notification.client.NotificationPushClient;
import org.example.when2go.domain.notification.dto.PushMessage;
import org.example.when2go.domain.notification.dto.PushSendResult;
import org.example.when2go.domain.notification.dto.PushSendResultType;

@Slf4j
@RequiredArgsConstructor
public class FcmNotificationPushClient implements NotificationPushClient {

    private final FirebaseMessageSender firebaseMessageSender;

    @Override
    public PushSendResult send(PushMessage message) {
        try {
            String messageId = firebaseMessageSender.send(toFirebaseMessage(message));
            return PushSendResult.success(messageId);
        } catch (FirebaseMessagingException e) {
            PushSendResultType resultType = FcmErrorClassifier.classify(
                    e.getErrorCode(),
                    e.getMessagingErrorCode(),
                    e.getMessage()
            );
            log.warn("event=notification.fcm_send_failed resultType={} errorCode={} messagingErrorCode={}",
                    resultType,
                    e.getErrorCode(),
                    e.getMessagingErrorCode(),
                    e);
            return PushSendResult.failure(resultType, errorCode(e), e.getMessage());
        } catch (RuntimeException e) {
            log.warn("event=notification.fcm_send_unexpected_failed errorType={}", e.getClass().getSimpleName(), e);
            return PushSendResult.failure(
                    PushSendResultType.PERMANENT_FAILURE,
                    e.getClass().getSimpleName(),
                    e.getMessage()
            );
        }
    }

    private Message toFirebaseMessage(PushMessage message) {
        return Message.builder()
                .setToken(message.token())
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putAllData(message.data())
                .build();
    }

    private String errorCode(FirebaseMessagingException e) {
        if (e.getMessagingErrorCode() != null) {
            return e.getMessagingErrorCode().name();
        }
        if (e.getErrorCode() != null) {
            return e.getErrorCode().name();
        }
        return null;
    }
}
