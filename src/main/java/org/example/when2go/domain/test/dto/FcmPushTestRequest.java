package org.example.when2go.domain.test.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record FcmPushTestRequest(
        @NotBlank
        String token,

        @NotBlank
        String title,

        @NotBlank
        String body,

        Map<String, String> data
) {
}
