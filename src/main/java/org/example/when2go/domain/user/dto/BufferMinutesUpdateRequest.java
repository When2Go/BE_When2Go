package org.example.when2go.domain.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BufferMinutesUpdateRequest(
        @NotNull
        @Min(0)
        Integer bufferMinutes
) {}
