package org.example.when2go.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.when2go.domain.user.entity.Platform;

public record UserRegisterRequest(
        @NotBlank String deviceId,
        @NotNull Platform platform,
        @NotBlank String fcmToken
) {}