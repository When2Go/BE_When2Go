package org.example.when2go.domain.route.client;

import java.util.List;
import org.example.when2go.domain.route.dto.RouteSearchRequest;
import org.example.when2go.domain.route.dto.RouteSearchRequest.RouteModifiers;
import org.example.when2go.domain.route.dto.RouteSearchResult;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class GoogleRouteClientImpl implements GoogleRouteClient {

    private static final boolean COMPUTE_ALTERNATIVE_ROUTES = false;
    private static final RouteModifiers ROUTE_MODIFIERS = new RouteModifiers(false, false, false);
    private static final String LANGUAGE_CODE = "ko-KR";
    private static final String UNITS = "METRIC";

    private final WebClient webClient;

    public GoogleRouteClientImpl(WebClient googleRoutesWebClient) {
        this.webClient = googleRoutesWebClient;
    }

    @Override
    public RouteSearchResult search(RouteSearchRequest request) {
        RouteSearchRequest body = new RouteSearchRequest(
                request.origin(),
                request.destination(),
                request.travelMode(),
                request.routingPreference(),
                COMPUTE_ALTERNATIVE_ROUTES,
                ROUTE_MODIFIERS,
                LANGUAGE_CODE,
                UNITS,
                request.arrivalTime()
        );

        return webClient.post()
                .uri("/directions/v2:computeRoutes")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(RouteSearchResult.class)
                .block();
    }
}
