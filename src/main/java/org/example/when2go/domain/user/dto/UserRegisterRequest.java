package org.example.when2go.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.when2go.domain.user.entity.Platform;

public record UserRegisterRequest(
        @NotBlank
        @Size(min = 36, max = 36)
        String deviceId,
        @NotNull Platform platform,
        @NotBlank
        @Size(max = 512)
        String fcmToken
) {}
