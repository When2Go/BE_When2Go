package org.example.when2go.domain.route.client;

import org.example.when2go.domain.route.dto.RouteSearchRequest;
import org.example.when2go.domain.route.dto.RouteSearchResult;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class GoogleRouteClientImpl implements RouteClient {

    private final WebClient webClient;

    public GoogleRouteClientImpl(WebClient googleRoutesWebClient) {
        this.webClient = googleRoutesWebClient;
    }

    @Override
    public RouteSearchResult search(RouteSearchRequest request) {

        return webClient.post()
                .uri("/directions/v2:computeRoutes")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(RouteSearchResult.class)
                .block();
    }
}
