package org.example.when2go.global.firebase;

import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.MessagingErrorCode;
import java.util.Locale;
import org.example.when2go.domain.notification.dto.PushSendResultType;

final class FcmErrorClassifier {

    private FcmErrorClassifier() {}

    static PushSendResultType classify(
            ErrorCode errorCode,
            MessagingErrorCode messagingErrorCode,
            String message
    ) {
        if (messagingErrorCode != null) {
            return classifyMessagingError(messagingErrorCode, message);
        }
        if (errorCode != null) {
            return classifyError(errorCode, message);
        }
        return PushSendResultType.PERMANENT_FAILURE;
    }

    private static PushSendResultType classifyMessagingError(
            MessagingErrorCode messagingErrorCode,
            String message
    ) {
        return switch (messagingErrorCode) {
            case UNREGISTERED -> PushSendResultType.INVALID_TOKEN;
            case INTERNAL, QUOTA_EXCEEDED, UNAVAILABLE -> PushSendResultType.TRANSIENT_FAILURE;
            case INVALID_ARGUMENT -> hasTokenKeyword(message)
                    ? PushSendResultType.INVALID_TOKEN
                    : PushSendResultType.PERMANENT_FAILURE;
            case SENDER_ID_MISMATCH, THIRD_PARTY_AUTH_ERROR -> PushSendResultType.PERMANENT_FAILURE;
            default -> PushSendResultType.PERMANENT_FAILURE;
        };
    }

    private static PushSendResultType classifyError(ErrorCode errorCode, String message) {
        return switch (errorCode) {
            case INTERNAL, UNAVAILABLE, DEADLINE_EXCEEDED, RESOURCE_EXHAUSTED ->
                    PushSendResultType.TRANSIENT_FAILURE;
            case INVALID_ARGUMENT -> hasTokenKeyword(message)
                    ? PushSendResultType.INVALID_TOKEN
                    : PushSendResultType.PERMANENT_FAILURE;
            default -> PushSendResultType.PERMANENT_FAILURE;
        };
    }

    private static boolean hasTokenKeyword(String message) {
        if (message == null) {
            return false;
        }
        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        return normalizedMessage.contains("registration token")
                || normalizedMessage.contains("fcm token")
                || normalizedMessage.contains("token");
    }
}
