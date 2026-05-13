package org.example.when2go.domain.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.user.dto.UserRegisterRequest;
import org.example.when2go.domain.user.dto.UserResponse;
import org.example.when2go.domain.user.service.UserService.UserRegisterResult;
import org.example.when2go.domain.user.service.UserService;
import org.example.when2go.global.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @Valid @RequestBody UserRegisterRequest request) {
        UserRegisterResult result = userService.registerOrFind(request);
        HttpStatus status = result.isNew() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.success(result.response()));

    }
}
