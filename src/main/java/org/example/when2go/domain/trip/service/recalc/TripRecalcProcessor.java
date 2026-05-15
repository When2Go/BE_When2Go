package org.example.when2go.domain.trip.service.recalc;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.route.client.GoogleRouteClient;
import org.example.when2go.domain.route.dto.RouteSearchRequest;
import org.example.when2go.domain.route.dto.RouteSearchRequest.Waypoint;
import org.example.when2go.domain.route.dto.RouteSearchRequest.Waypoint.Location;
import org.example.when2go.domain.route.dto.RouteSearchRequest.Waypoint.Location.LatLng;
import org.example.when2go.domain.route.dto.RouteSearchResult;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TripRecalcProcessor {

    private final ObjectProvider<GoogleRouteClient> googleRouteClientProvider;
    private final TripRecalcFinalizer tripRecalcFinalizer;
    private final TripRepository tripRepository;

    public boolean isAvailable() {
        return googleRouteClientProvider.getIfAvailable() != null;
    }

    public void process(Long tripId) {
        GoogleRouteClient googleRouteClient = googleRouteClientProvider.getIfAvailable();
        if (googleRouteClient == null) {
            throw new IllegalStateException("GoogleRouteClient bean is required to recalculate trips");
        }
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found: " + tripId));

        // LazyInitializationException 을 피하기 위해 DTO 에 담아 googleRouteClient에게 전달
        RouteSearchRequest request = new RouteSearchRequest(
                new Waypoint(new Location(new LatLng(trip.getOriginLat(), trip.getOriginLng()))),
                new Waypoint(new Location(new LatLng(trip.getDestLat(), trip.getDestLng()))),
                trip.getRouteOption().name(),
                trip.getRouteOption() == RouteOption.DRIVE ? "TRAFFIC_AWARE" : null,
                false,
                null,
                null,
                null,
                trip.getArrivalTime().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        );
        RouteSearchResult result = googleRouteClient.search(request);
        tripRecalcFinalizer.finalizeRecalc(tripId, result);
    }
}
