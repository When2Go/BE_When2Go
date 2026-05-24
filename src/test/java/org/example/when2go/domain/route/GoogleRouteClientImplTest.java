package org.example.when2go.domain.route;

import org.example.when2go.domain.route.client.GoogleRouteClientImpl;
import org.example.when2go.domain.route.dto.RouteSearchRequest;
import org.example.when2go.domain.route.dto.GoogleRouteSearchRequest;
import org.example.when2go.domain.route.dto.GoogleRouteSearchResponse;
import io.github.cdimascio.dotenv.Dotenv;
import java.time.LocalDateTime;
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

        RouteSearchRequest routeSearchRequest = new RouteSearchRequest(
                37.5665,
                126.9780,
                37.4979,
                127.0276,
                LocalDateTime.of(2026, 5, 16, 10, 0)
        );
        GoogleRouteSearchRequest request = new GoogleRouteSearchRequest(routeSearchRequest);

        GoogleRouteSearchResponse result = client.search(request);
        System.out.println("result: " + result);
        System.out.println("totalMinutes: " + result.totalMinutes());
    }
}
