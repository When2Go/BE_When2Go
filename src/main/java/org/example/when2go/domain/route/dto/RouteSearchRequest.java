package org.example.when2go.domain.route.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.when2go.domain.reservation.entity.Reservation;
import org.example.when2go.domain.trip.entity.Trip;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class RouteSearchRequest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

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

    public static RouteSearchRequest from(Trip trip) {
        LocalDateTime arrivalTimeKst = trip.getArrivalTime()
                .atZone(ZoneOffset.UTC)
                .withZoneSameInstant(KST)
                .toLocalDateTime();
        return new RouteSearchRequest(
                trip.getOriginLat(),
                trip.getOriginLng(),
                trip.getDestLat(),
                trip.getDestLng(),
                arrivalTimeKst
        );
    }

    public static RouteSearchRequest from(Reservation reservation, LocalDate today) {
        return new RouteSearchRequest(
                reservation.getOriginLat(),
                reservation.getOriginLng(),
                reservation.getDestLat(),
                reservation.getDestLng(),
                today.atTime(reservation.getArrivalTime())
        );
    }
}
