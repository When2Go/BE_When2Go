package org.example.when2go.domain.trip.dto;

import java.time.LocalDateTime;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.entity.TripStatus;

public record TripCreateResponse(
        Long tripId,
        TripStatus status,
        String originName,
        String destName,
        LocalDateTime arrivalTime,
        Integer bufferMinutes,
        LocalDateTime createdAt
) {

    public static TripCreateResponse from(Trip trip) {
        return new TripCreateResponse(
                trip.getId(),
                trip.getStatus(),
                trip.getOriginName(),
                trip.getDestName(),
                trip.getArrivalTime(),
                trip.getBufferMinutes(),
                trip.getCreatedAt()
        );
    }
}
