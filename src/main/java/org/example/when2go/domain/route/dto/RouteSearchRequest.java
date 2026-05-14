package org.example.when2go.domain.route.dto;

import java.time.LocalDateTime;
import java.util.Objects;
import org.example.when2go.domain.route.enums.RouteOption;

public record RouteSearchRequest(
        double originLat,
        double originLng,
        double destLat,
        double destLng,
        LocalDateTime arrivalTime,
        RouteOption routeOption
) {
    public RouteSearchRequest {
        Objects.requireNonNull(arrivalTime, "arrivalTime must not be null");
        Objects.requireNonNull(routeOption, "routeOption must not be null");
    }
}
