package org.example.when2go.global.firebase;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.MessagingErrorCode;
import org.example.when2go.domain.notification.dto.PushSendResultType;
import org.junit.jupiter.api.Test;

class FcmErrorClassifierTest {

    @Test
    void classifyUnregisteredAsInvalidToken() {
        PushSendResultType resultType = FcmErrorClassifier.classify(
                null,
                MessagingErrorCode.UNREGISTERED,
                "Requested entity was not found"
        );

        assertThat(resultType).isEqualTo(PushSendResultType.INVALID_TOKEN);
    }

    @Test
    void classifyInvalidArgumentWithTokenKeywordAsInvalidToken() {
        PushSendResultType resultType = FcmErrorClassifier.classify(
                ErrorCode.INVALID_ARGUMENT,
                MessagingErrorCode.INVALID_ARGUMENT,
                "The registration token is not a valid FCM registration token"
        );

        assertThat(resultType).isEqualTo(PushSendResultType.INVALID_TOKEN);
    }

    @Test
    void classifyUnavailableAsTransientFailure() {
        PushSendResultType resultType = FcmErrorClassifier.classify(
                ErrorCode.UNAVAILABLE,
                null,
                "Service unavailable"
        );

        assertThat(resultType).isEqualTo(PushSendResultType.TRANSIENT_FAILURE);
    }

    @Test
    void classifyMessagingUnavailableAsTransientFailure() {
        PushSendResultType resultType = FcmErrorClassifier.classify(
                null,
                MessagingErrorCode.UNAVAILABLE,
                "Service unavailable"
        );

        assertThat(resultType).isEqualTo(PushSendResultType.TRANSIENT_FAILURE);
    }

    @Test
    void classifySenderMismatchAsPermanentFailure() {
        PushSendResultType resultType = FcmErrorClassifier.classify(
                null,
                MessagingErrorCode.SENDER_ID_MISMATCH,
                "SenderId mismatch"
        );

        assertThat(resultType).isEqualTo(PushSendResultType.PERMANENT_FAILURE);
    }
}
