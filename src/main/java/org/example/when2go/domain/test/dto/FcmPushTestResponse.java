package org.example.when2go.domain.test.dto;

import org.example.when2go.domain.notification.dto.PushSendResult;
import org.example.when2go.domain.notification.dto.PushSendResultType;

public record FcmPushTestResponse(
        PushSendResultType type,
        boolean success,
        String messageId,
        String errorCode,
        String errorMessage
) {

    public static FcmPushTestResponse from(PushSendResult result) {
        return new FcmPushTestResponse(
                result.type(),
                result.success(),
                result.messageId(),
                result.errorCode(),
                result.errorMessage()
        );
    }
}
