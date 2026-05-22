package org.example.when2go.domain.route.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import lombok.Getter;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
public class RouteSearchRequest {
    private static final String TRAVEL_MODE = "TRANSIT";
    private static final boolean COMPUTE_ALTERNATIVE_ROUTES = true;
    private static final String LANGUAGE_CODE = "ko-KR";
    private static final String UNITS = "METRIC";
    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private Waypoint origin;
    private Waypoint destination;
    private String travelMode;
    private String routingPreference;
    private boolean computeAlternativeRoutes;
    private String languageCode;
    private String units;
    private String arrivalTime;

    public RouteSearchRequest(RouteDTO routeDTO) {
        this.origin = toWaypoint(routeDTO.getOriginLat(), routeDTO.getOriginLng());
        this.destination = toWaypoint(routeDTO.getDestLat(), routeDTO.getDestLng());
        this.travelMode = TRAVEL_MODE;
        this.routingPreference = null;
        this.computeAlternativeRoutes = COMPUTE_ALTERNATIVE_ROUTES;
        this.languageCode = LANGUAGE_CODE;
        this.units = UNITS;
        this.arrivalTime = toUtcRfc3339(routeDTO.getArrivalTime());
    }

    private static String toUtcRfc3339(String kstDateTime) {
        return LocalDateTime.parse(kstDateTime, INPUT_FORMAT)
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
