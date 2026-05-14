package org.example.when2go.domain.trip.service.recalc;

import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.route.client.OdsayRouteClient;
import org.example.when2go.domain.route.client.RouteSearchResult;
import org.example.when2go.domain.trip.entity.Trip;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TripRecalcProcessor {

    private final ObjectProvider<OdsayRouteClient> odsayRouteClientProvider;
    private final TripRecalcFinalizer tripRecalcFinalizer;

    public boolean isAvailable() {
        return odsayRouteClientProvider.getIfAvailable() != null;
    }

    public void process(Trip trip) {
        OdsayRouteClient odsayRouteClient = odsayRouteClientProvider.getIfAvailable();
        if (odsayRouteClient == null) {
            throw new IllegalStateException("OdsayRouteClient bean is required to recalculate trips");
        }
        RouteSearchResult result = odsayRouteClient.search(trip);
        tripRecalcFinalizer.finalizeRecalc(trip.getId(), result);
    }
}
