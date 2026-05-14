package org.example.when2go.domain.trip.service.recalc;

import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.route.client.OdsayRouteClient;
import org.example.when2go.domain.route.client.RouteSearchResult;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripRecalcProcessor {

    private final ObjectProvider<OdsayRouteClient> odsayRouteClientProvider;
    private final TripRecalcFinalizer tripRecalcFinalizer;
    private final TripRepository tripRepository;

    public boolean isAvailable() {
        return odsayRouteClientProvider.getIfAvailable() != null;
    }

    @Transactional(readOnly = true)
    public void process(Long tripId) {
        OdsayRouteClient odsayRouteClient = odsayRouteClientProvider.getIfAvailable();
        if (odsayRouteClient == null) {
            throw new IllegalStateException("OdsayRouteClient bean is required to recalculate trips");
        }
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found: " + tripId));
        RouteSearchResult result = odsayRouteClient.search(trip);
        tripRecalcFinalizer.finalizeRecalc(tripId, result);
    }
}
