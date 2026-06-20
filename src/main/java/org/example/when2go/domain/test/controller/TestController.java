package org.example.when2go.domain.test.controller;

import jakarta.validation.Valid;
import java.util.Map;
import org.example.when2go.domain.notification.client.NotificationPushClient;
import org.example.when2go.domain.notification.dto.PushMessage;
import org.example.when2go.domain.notification.dto.PushSendResult;
import org.example.when2go.domain.test.dto.FcmPushTestRequest;
import org.example.when2go.domain.test.dto.FcmPushTestResponse;
import org.example.when2go.domain.test.error.TestErrorCode;
import org.example.when2go.global.error.DomainException;
import org.example.when2go.global.response.ApiResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!prod")
@RequestMapping("/api/test")
public class TestController {

    private final ObjectProvider<NotificationPushClient> notificationPushClientProvider;

    public TestController(ObjectProvider<NotificationPushClient> notificationPushClientProvider) {
        this.notificationPushClientProvider = notificationPushClientProvider;
    }

    /**
     * 성공 응답 예시 (데이터 없음)
     *
     * 응답:
     * {"success": true, "code": "OK", "message": "요청이 성공했습니다."}
     */
    @GetMapping("/success")
    public ApiResponse<Void> success() {

        return ApiResponse.success();
    }

    /**
     * 성공 응답 예시 (데이터 있음)
     *
     * 응답:
     * {"success": true, "code": "OK", "message": "요청이 성공했습니다.", "data": "Hello, Attium!"}
     */
    @GetMapping("/success/data")
    public ApiResponse<String> successWithData() {

        return ApiResponse.success("Hello, Attium!");
    }

    /**
     * 실패 응답 예시
     *
     * 응답:
     * {"success": false, "code": "TEST_001", "message": "테스트를 찾을 수 없습니다."}
     */
    @GetMapping("/error")
    public ApiResponse<Void> error() {

        throw new DomainException(TestErrorCode.TEST_NOT_FOUND);
    }

    /**
     * 성공 시: {"success": true, "code": "OK", "message": "요청이 성공했습니다.", "data": 1}
     * 실패 시: {"success": false, "code": "TEST_001", "message": "테스트를 찾을 수 없습니다."}
     */
    @GetMapping("/{id}")
    public ApiResponse<Long> findById(@PathVariable Long id) {
        if (id <= 0) {
            throw new DomainException(TestErrorCode.TEST_NOT_FOUND);
        }
        return ApiResponse.success(id);
    }

    @PostMapping("/fcm/send")
    public ApiResponse<FcmPushTestResponse> sendFcm(@Valid @RequestBody FcmPushTestRequest request) {
        NotificationPushClient notificationPushClient = notificationPushClientProvider.getIfAvailable();
        if (notificationPushClient == null) {
            throw new DomainException(TestErrorCode.FCM_PUSH_CLIENT_DISABLED);
        }

        PushSendResult result = notificationPushClient.send(new PushMessage(
                request.token(),
                request.title(),
                request.body(),
                request.data() == null ? Map.of() : request.data()
        ));

        return ApiResponse.success(FcmPushTestResponse.from(result));
    }
}
