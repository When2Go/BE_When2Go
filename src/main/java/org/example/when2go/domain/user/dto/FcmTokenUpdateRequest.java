package org.example.when2go.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FcmTokenUpdateRequest(
        @NotBlank
        @Size(max = 512)
        String fcmToken
) {}
