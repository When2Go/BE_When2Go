package org.example.when2go.domain.trip.service.recalc;

import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.route.client.OdsayRouteClient;
import org.example.when2go.domain.route.dto.RouteSearchRequest;
import org.example.when2go.domain.route.dto.RouteSearchResult;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TripRecalcProcessor {

    private final ObjectProvider<OdsayRouteClient> odsayRouteClientProvider;
    private final TripRecalcFinalizer tripRecalcFinalizer;
    private final TripRepository tripRepository;

    public boolean isAvailable() {
        return odsayRouteClientProvider.getIfAvailable() != null;
    }

    public void process(Long tripId) {
        OdsayRouteClient odsayRouteClient = odsayRouteClientProvider.getIfAvailable();
        if (odsayRouteClient == null) {
            throw new IllegalStateException("OdsayRouteClient bean is required to recalculate trips");
        }
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found: " + tripId));

        // LazyInitializationException 을 피하기 위해 DTO 에 담아 odsayRouteClient에게 전달
        RouteSearchRequest request = new RouteSearchRequest(
                trip.getOriginLat(),
                trip.getOriginLng(),
                trip.getDestLat(),
                trip.getDestLng(),
                trip.getArrivalTime(),
                trip.getRouteOption()
        );
        RouteSearchResult result = odsayRouteClient.search(request);
        tripRecalcFinalizer.finalizeRecalc(tripId, result);
    }
}
