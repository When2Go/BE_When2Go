package org.example.when2go.domain.route.client;

import org.example.when2go.domain.route.dto.GoogleRouteSearchRequest;
import org.example.when2go.domain.route.dto.GoogleRouteSearchResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class GoogleRouteClient {

    private final WebClient webClient;

    public GoogleRouteClient(WebClient googleRoutesWebClient) {
        this.webClient = googleRoutesWebClient;
    }

    public GoogleRouteSearchResponse search(GoogleRouteSearchRequest request) {

        return webClient.post()
                .uri("/directions/v2:computeRoutes")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GoogleRouteSearchResponse.class)
                .block();
    }
}
