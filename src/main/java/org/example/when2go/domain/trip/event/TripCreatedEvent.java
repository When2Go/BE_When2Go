package org.example.when2go.domain.trip.event;

public record TripCreatedEvent(
        Long tripId,
        String destName,
        Double destLat,
        Double destLng
) {
}
