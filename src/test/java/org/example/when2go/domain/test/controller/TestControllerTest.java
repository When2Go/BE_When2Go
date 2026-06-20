package org.example.when2go.domain.test.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.example.when2go.domain.notification.client.NotificationPushClient;
import org.example.when2go.domain.notification.dto.PushMessage;
import org.example.when2go.domain.notification.dto.PushSendResult;
import org.example.when2go.domain.notification.dto.PushSendResultType;
import org.example.when2go.domain.test.dto.FcmPushTestRequest;
import org.example.when2go.domain.test.dto.FcmPushTestResponse;
import org.example.when2go.domain.test.error.TestErrorCode;
import org.example.when2go.global.error.DomainException;
import org.example.when2go.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class TestControllerTest {

    @Test
    void sendFcmDelegatesToPushClient() {
        @SuppressWarnings("unchecked")
        ObjectProvider<NotificationPushClient> provider = mock(ObjectProvider.class);
        NotificationPushClient pushClient = mock(NotificationPushClient.class);
        when(provider.getIfAvailable()).thenReturn(pushClient);
        when(pushClient.send(any(PushMessage.class))).thenReturn(PushSendResult.success("message-id"));

        TestController controller = new TestController(provider);
        ApiResponse<FcmPushTestResponse> response = controller.sendFcm(new FcmPushTestRequest(
                "token",
                "title",
                "body",
                Map.of("tripId", "1")
        ));

        assertThat(response.getData().type()).isEqualTo(PushSendResultType.SUCCESS);
        assertThat(response.getData().messageId()).isEqualTo("message-id");

        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushClient).send(captor.capture());
        assertThat(captor.getValue().token()).isEqualTo("token");
        assertThat(captor.getValue().data()).containsEntry("tripId", "1");
    }

    @Test
    void sendFcmFailsWhenPushClientIsDisabled() {
        @SuppressWarnings("unchecked")
        ObjectProvider<NotificationPushClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        TestController controller = new TestController(provider);

        assertThatThrownBy(() -> controller.sendFcm(new FcmPushTestRequest("token", "title", "body", null)))
                .isInstanceOfSatisfying(DomainException.class, e ->
                        assertThat(e.getErrorCode()).isEqualTo(TestErrorCode.FCM_PUSH_CLIENT_DISABLED));
    }
}
