package org.example.when2go.domain.route.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RouteSearchRequest(
        Waypoint origin,
        Waypoint destination,
        String travelMode,
        String routingPreference,
        boolean computeAlternativeRoutes,
        RouteModifiers routeModifiers,
        String languageCode,
        String units,
        String arrivalTime
) {
    public record Waypoint(Location location) {
        public record Location(LatLng latLng) {
            public record LatLng(double latitude, double longitude) {}
        }
    }

    public record RouteModifiers(boolean avoidTolls, boolean avoidHighways, boolean avoidFerries) {}
}
