package org.example.when2go.domain.route.service;

import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.route.client.GoogleRouteClient;
import org.example.when2go.domain.route.dto.RouteSearchRequest;
import org.example.when2go.domain.route.dto.GoogleRouteSearchRequest;
import org.example.when2go.domain.route.dto.GoogleRouteSearchResponse;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final GoogleRouteClient googleRouteClient;

    public GoogleRouteSearchResponse search(RouteSearchRequest routeSearchRequest) {
        GoogleRouteSearchRequest request = new GoogleRouteSearchRequest(routeSearchRequest);
        return googleRouteClient.search(request);
    }
}
