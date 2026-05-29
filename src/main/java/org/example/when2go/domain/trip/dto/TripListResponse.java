package org.example.when2go.domain.trip.dto;

import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.entity.TripStatus;

import java.time.LocalDateTime;

public record TripListResponse(
        Long tripId,
        String originName,
        String destName,
        LocalDateTime arrivalTime,
        LocalDateTime finalDepartureTime,
        TripStatus status
) {

    public static TripListResponse from(Trip trip) {
        return new TripListResponse(
                trip.getId(),
                trip.getOriginName(),
                trip.getDestName(),
                trip.getArrivalTime(),
                trip.getFinalDepartureTime(),
                trip.getStatus()
        );
    }
}
