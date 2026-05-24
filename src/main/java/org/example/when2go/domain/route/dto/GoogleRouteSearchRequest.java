package org.example.when2go.domain.route.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import lombok.Getter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class GoogleRouteSearchRequest {
    private static final String TRAVEL_MODE = "TRANSIT";
    private static final boolean COMPUTE_ALTERNATIVE_ROUTES = true;
    private static final String LANGUAGE_CODE = "ko-KR";
    private static final String UNITS = "METRIC";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private Waypoint origin;
    private Waypoint destination;
    private String travelMode;
    private String routingPreference;
    private boolean computeAlternativeRoutes;
    private String languageCode;
    private String units;
    private String arrivalTime;

    public GoogleRouteSearchRequest(RouteSearchRequest routeSearchRequest) {
        this.origin = toWaypoint(routeSearchRequest.getOriginLat(), routeSearchRequest.getOriginLng());
        this.destination = toWaypoint(routeSearchRequest.getDestLat(), routeSearchRequest.getDestLng());
        this.travelMode = TRAVEL_MODE;
        this.routingPreference = null;
        this.computeAlternativeRoutes = COMPUTE_ALTERNATIVE_ROUTES;
        this.languageCode = LANGUAGE_CODE;
        this.units = UNITS;
        this.arrivalTime = toUtcRfc3339(routeSearchRequest.getArrivalTime());
    }

    private static String toUtcRfc3339(LocalDateTime kstDateTime) {
        return kstDateTime
                .atZone(KST)
                .withZoneSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT);
    }

    private static Waypoint toWaypoint(double latitude, double longitude) {
        return new Waypoint(new Waypoint.Location(new Waypoint.Location.LatLng(latitude, longitude)));
    }

    public record Waypoint(Location location) {
        public record Location(LatLng latLng) {
            public record LatLng(double latitude, double longitude) {}
        }
    }
}
