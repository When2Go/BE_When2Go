package org.example.when2go.domain.route;

import org.example.when2go.domain.route.client.GoogleRouteClientImpl;
import org.example.when2go.domain.route.dto.RouteDTO;
import org.example.when2go.domain.route.dto.RouteSearchRequest;
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

        RouteDTO routeDTO = new RouteDTO(
                37.5665,
                126.9780,
                37.4979,
                127.0276,
                "2026-05-16 10:00"
        );
        RouteSearchRequest request = new RouteSearchRequest(routeDTO);

        RouteSearchResult result = client.search(request);
        System.out.println("result: " + result);
        System.out.println("totalMinutes: " + result.totalMinutes());
    }
}
