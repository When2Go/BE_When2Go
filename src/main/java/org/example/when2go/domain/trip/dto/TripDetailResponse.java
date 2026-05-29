package org.example.when2go.domain.trip.dto;

import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.entity.TripStatus;

import java.time.LocalDateTime;

public record TripDetailResponse(
        Long tripId,
        String originName,
        Double originLat,
        Double originLng,
        String destName,
        Double destLat,
        Double destLng,
        LocalDateTime arrivalTime,
        Integer bufferMinutes,
        LocalDateTime finalDepartureTime,
        TripStatus status,
        LocalDateTime updatedAt
) {

    public static TripDetailResponse from(Trip trip) {
        return new TripDetailResponse(
                trip.getId(),
                trip.getOriginName(),
                trip.getOriginLat(),
                trip.getOriginLng(),
                trip.getDestName(),
                trip.getDestLat(),
                trip.getDestLng(),
                trip.getArrivalTime(),
                trip.getBufferMinutes(),
                trip.getFinalDepartureTime(),
                trip.getStatus(),
                trip.getUpdatedAt()
        );
    }
}
