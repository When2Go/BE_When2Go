package org.example.when2go.domain.route.client;

import org.example.when2go.domain.route.dto.RouteSearchRequest;
import org.example.when2go.domain.route.dto.RouteSearchResult;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class GoogleRouteClientImpl implements GoogleRouteClient {

    private static final String TRAVEL_MODE = "TRANSIT";
    private static final boolean COMPUTE_ALTERNATIVE_ROUTES = true;
    private static final String LANGUAGE_CODE = "ko-KR";
    private static final String UNITS = "METRIC";

    private final WebClient webClient;

    public GoogleRouteClientImpl(WebClient googleRoutesWebClient) {
        this.webClient = googleRoutesWebClient;
    }

    @Override
    public RouteSearchResult search(RouteSearchRequest request) {
        RouteSearchRequest body = RouteSearchRequest.builder()
                .origin(request.origin())
                .destination(request.destination())
                .travelMode(TRAVEL_MODE)
                .computeAlternativeRoutes(COMPUTE_ALTERNATIVE_ROUTES)
                .languageCode(LANGUAGE_CODE)
                .units(UNITS)
                .arrivalTime(request.arrivalTime())
                .build();

        return webClient.post()
                .uri("/directions/v2:computeRoutes")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(RouteSearchResult.class)
                .block();
    }
}
