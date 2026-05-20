package org.example.when2go.domain.route;

import org.example.when2go.domain.route.client.GoogleRouteClientImpl;
import org.example.when2go.domain.route.dto.RouteSearchRequest;
import org.example.when2go.domain.route.dto.RouteSearchRequest.Waypoint;
import org.example.when2go.domain.route.dto.RouteSearchRequest.Waypoint.Location;
import org.example.when2go.domain.route.dto.RouteSearchRequest.Waypoint.Location.LatLng;
import org.example.when2go.domain.route.dto.RouteSearchResult;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class GoogleRouteClientImplTest {

    private static final String API_KEY = Dotenv.load().get("GOOGLE_ROUTES_API_KEY");

    @Test
    void search() {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://routes.googleapis.com")
                .defaultHeader("X-Goog-Api-Key", API_KEY)
                .defaultHeader("X-Goog-FieldMask", "*")
                .build();

        GoogleRouteClientImpl client = new GoogleRouteClientImpl(webClient);

        RouteSearchRequest request = RouteSearchRequest.builder()
                .origin(new Waypoint(new Location(new LatLng(37.5665, 126.9780))))
                .destination(new Waypoint(new Location(new LatLng(37.4979, 127.0276))))
                .arrivalTime("2026-05-16T10:00:00+09:00")
                .build();

        RouteSearchResult result = client.search(request);
        System.out.println("result: " + result);
        System.out.println("totalMinutes: " + result.totalMinutes());
    }
}
