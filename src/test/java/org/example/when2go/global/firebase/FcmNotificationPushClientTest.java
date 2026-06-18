package org.example.when2go.global.firebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.Message;
import java.util.Map;
import org.example.when2go.domain.notification.dto.PushMessage;
import org.example.when2go.domain.notification.dto.PushSendResult;
import org.example.when2go.domain.notification.dto.PushSendResultType;
import org.junit.jupiter.api.Test;

class FcmNotificationPushClientTest {

    private final FirebaseMessageSender firebaseMessageSender =
            org.mockito.Mockito.mock(FirebaseMessageSender.class);
    private final FcmNotificationPushClient client = new FcmNotificationPushClient(firebaseMessageSender);

    @Test
    void sendReturnsSuccessWithMessageId() throws Exception {
        when(firebaseMessageSender.send(any(Message.class))).thenReturn("message-id");

        PushSendResult result = client.send(new PushMessage(
                "token",
                "title",
                "body",
                Map.of("tripId", "1", "type", "DEPART_NOW")
        ));

        assertThat(result.type()).isEqualTo(PushSendResultType.SUCCESS);
        assertThat(result.messageId()).isEqualTo("message-id");
        assertThat(result.success()).isTrue();
    }

    @Test
    void sendConvertsUnexpectedRuntimeExceptionToPermanentFailure() throws Exception {
        when(firebaseMessageSender.send(any(Message.class))).thenThrow(new IllegalStateException("boom"));

        PushSendResult result = client.send(new PushMessage(
                "token",
                "title",
                "body",
                Map.of()
        ));

        assertThat(result.type()).isEqualTo(PushSendResultType.PERMANENT_FAILURE);
        assertThat(result.errorCode()).isEqualTo("IllegalStateException");
        assertThat(result.success()).isFalse();
    }
}
