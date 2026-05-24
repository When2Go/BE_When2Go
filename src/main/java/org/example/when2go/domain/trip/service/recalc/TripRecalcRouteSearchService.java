package org.example.when2go.domain.trip.service.recalc;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.route.client.RouteClient;
import org.example.when2go.domain.route.dto.RouteSearchRequest;
import org.example.when2go.domain.route.dto.GoogleRouteSearchRequest;
import org.example.when2go.domain.route.dto.GoogleRouteSearchResponse;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TripRecalcRouteSearchService {

    private final ObjectProvider<RouteClient> googleRouteClientProvider;
    private final TripPhaseAdvanceService tripPhaseAdvanceService;
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
        RouteSearchRequest routeSearchRequest = new RouteSearchRequest(
                trip.getOriginLat(),
                trip.getOriginLng(),
                trip.getDestLat(),
                trip.getDestLng(),
                arrivalTime
        );
        GoogleRouteSearchRequest request = new GoogleRouteSearchRequest(routeSearchRequest);
        GoogleRouteSearchResponse result = googleRouteClient.search(request);
        tripPhaseAdvanceService.advancePhase(tripId, result);
    }
}
