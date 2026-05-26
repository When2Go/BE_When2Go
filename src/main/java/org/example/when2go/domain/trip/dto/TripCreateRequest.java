package org.example.when2go.domain.trip.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDateTime;

public record TripCreateRequest(
        @NotBlank
        String originName,

        @NotNull
        Double originLat,

        @NotNull
        Double originLng,

        @NotBlank
        String destName,

        @NotNull
        Double destLat,

        @NotNull
        Double destLng,

        @NotNull
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
        LocalDateTime arrivalTime,

        @NotNull
        @PositiveOrZero
        Integer bufferMinutes,

        @NotNull
        @PositiveOrZero
        Integer durationSeconds
) {
}
