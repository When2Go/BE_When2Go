package org.example.when2go.domain.notification.dto;

public record PushSendResult(
        PushSendResultType type,
        String messageId,
        String errorCode,
        String errorMessage
) {

    public static PushSendResult success(String messageId) {
        return new PushSendResult(PushSendResultType.SUCCESS, messageId, null, null);
    }

    public static PushSendResult failure(
            PushSendResultType type,
            String errorCode,
            String errorMessage
    ) {
        if (type == PushSendResultType.SUCCESS) {
            throw new IllegalArgumentException("failure type must not be SUCCESS");
        }
        return new PushSendResult(type, null, errorCode, errorMessage);
    }

    public boolean success() {
        return type == PushSendResultType.SUCCESS;
    }
}
