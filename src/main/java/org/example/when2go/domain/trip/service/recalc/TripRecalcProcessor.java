package org.example.when2go.domain.trip.service.recalc;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.route.client.RouteClient;
import org.example.when2go.domain.route.dto.RouteDTO;
import org.example.when2go.domain.route.dto.RouteSearchRequest;
import org.example.when2go.domain.route.dto.RouteSearchResponse;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TripRecalcProcessor {

    private final ObjectProvider<RouteClient> googleRouteClientProvider;
    private final TripRecalcFinalizer tripRecalcFinalizer;
    private final TripRepository tripRepository;

    public boolean isAvailable() {
        return googleRouteClientProvider.getIfAvailable() != null;
    }

    public void process(Long tripId) {
        RouteClient googleRouteClient = googleRouteClientProvider.getIfAvailable();
        if (googleRouteClient == null) {
            throw new IllegalStateException("GoogleRouteClient bean is required to recalculate trips");
        }
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found: " + tripId));

        LocalDateTime arrivalTime = trip.getArrivalTime()
                .atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.of("Asia/Seoul"))
                .toLocalDateTime();
        RouteDTO routeDTO = new RouteDTO(
                trip.getOriginLat(),
                trip.getOriginLng(),
                trip.getDestLat(),
                trip.getDestLng(),
                arrivalTime
        );
        RouteSearchRequest request = new RouteSearchRequest(routeDTO);
        RouteSearchResponse result = googleRouteClient.search(request);
        tripRecalcFinalizer.finalizeRecalc(tripId, result);
    }
}
