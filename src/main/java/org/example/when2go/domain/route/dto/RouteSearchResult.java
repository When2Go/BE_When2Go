package org.example.when2go.domain.route.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RouteSearchResult(
        List<Route> routes,
        Object geocodingResults
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Route(
            List<Leg> legs,
            Integer distanceMeters,
            String duration,
            String staticDuration,
            Polyline polyline,
            Viewport viewport,
            TravelAdvisory travelAdvisory,
            LocalizedValues localizedValues,
            List<String> routeLabels
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Leg(
            Integer distanceMeters,
            String duration,
            String staticDuration,
            Polyline polyline,
            Location startLocation,
            Location endLocation,
            List<Step> steps,
            LocalizedValues localizedValues,
            StepsOverview stepsOverview
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Step(
            Integer distanceMeters,
            String staticDuration,
            Polyline polyline,
            Location startLocation,
            Location endLocation,
            NavigationInstruction navigationInstruction,
            LocalizedValues localizedValues,
            String travelMode,
            TransitDetails transitDetails
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Polyline(String encodedPolyline) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Location(LatLng latLng) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LatLng(double latitude, double longitude) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NavigationInstruction(String instructions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Viewport(LatLng low, LatLng high) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TravelAdvisory(TransitFare transitFare) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransitFare(String currencyCode, String units, Integer nanos) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LocalizedValues(
            LocalizedText distance,
            LocalizedText duration,
            LocalizedText staticDuration,
            Object transitFare
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LocalizedText(String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StepsOverview(List<MultiModalSegment> multiModalSegments) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MultiModalSegment(
            Integer stepStartIndex,
            Integer stepEndIndex,
            NavigationInstruction navigationInstruction,
            String travelMode
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransitDetails(
            StopDetails stopDetails,
            TransitLocalizedValues localizedValues,
            String headsign,
            String headway,
            TransitLine transitLine,
            Integer stopCount
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StopDetails(
            TransitStop arrivalStop,
            String arrivalTime,
            TransitStop departureStop,
            String departureTime
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransitStop(String name, Location location) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransitLocalizedValues(
            TransitTime arrivalTime,
            TransitTime departureTime
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransitTime(LocalizedText time, String timeZone) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransitLine(
            List<Agency> agencies,
            String name,
            String color,
            String nameShort,
            String textColor,
            Vehicle vehicle
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Agency(String name, String uri) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Vehicle(LocalizedText name, String type, String iconUri) {}

    public int totalMinutes() {
        long seconds = Long.parseLong(routes.get(0).duration().replace("s", ""));
        return (int) (seconds / 60);
    }
}
