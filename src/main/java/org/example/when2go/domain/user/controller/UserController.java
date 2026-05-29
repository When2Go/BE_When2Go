package org.example.when2go.domain.user.controller;

import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.user.controller.docs.UserControllerApi;
import org.example.when2go.domain.user.dto.FcmTokenUpdateRequest;
import org.example.when2go.domain.user.dto.FcmTokenUpdateResponse;
import org.example.when2go.domain.user.dto.UserStatusResponse;
import org.example.when2go.domain.user.dto.UserRegisterRequest;
import org.example.when2go.domain.user.dto.UserResponse;
import org.example.when2go.domain.user.service.UserService;
import org.example.when2go.global.response.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController implements UserControllerApi {

    private final UserService userService;

    @Override
    @PostMapping
    public ApiResponse<UserResponse> register(
            @RequestBody UserRegisterRequest request) {
        return ApiResponse.success(userService.registerOrFind(request));
    }

    @Override
    @GetMapping("/status")
    public ApiResponse<UserStatusResponse> exists(
            @RequestHeader("X-Device-Id") String deviceId) {
        return ApiResponse.success(userService.existsByDeviceId(deviceId));
    }

    @Override
    @PatchMapping("/me/fcm-token")
    public ApiResponse<FcmTokenUpdateResponse> updateFcmToken(
            @RequestHeader("X-Device-Id") String deviceId,
            @RequestBody FcmTokenUpdateRequest request) {
        return ApiResponse.success(userService.updateFcmToken(deviceId, request.fcmToken()));
    }
}
