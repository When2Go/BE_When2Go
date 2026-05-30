package org.example.when2go.domain.reservation.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import org.example.when2go.domain.route.enums.RouteOption;

public record ReservationCreateRequest(
        @Size(max = 30)
        String nickname,

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
        RouteOption routeOption,

        @NotNull
        @JsonFormat(pattern = "HH:mm")
        LocalTime arrivalTime,

        @NotEmpty
        Set<DayOfWeek> repeatDays
) {
}
