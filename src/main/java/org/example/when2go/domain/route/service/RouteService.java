package org.example.when2go.domain.route.service;

import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.route.client.RouteClient;
import org.example.when2go.domain.route.dto.RouteDTO;
import org.example.when2go.domain.route.dto.RouteSearchRequest;
import org.example.when2go.domain.route.dto.RouteSearchResult;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteClient routeClient;

    public RouteSearchResult search(RouteDTO routeDTO) {
        RouteSearchRequest request = new RouteSearchRequest(routeDTO);
        return routeClient.search(request);
    }
}
