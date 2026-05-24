package org.example.when2go.domain.route.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class RouteSearchRequest {

    @NotNull
    Double originLat;
    @NotNull
    Double originLng;
    @NotNull
    Double destLat;
    @NotNull
    Double destLng;
    @Schema(example = "2026-05-22 13:30")
    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    LocalDateTime arrivalTime;
}
