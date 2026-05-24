package org.example.when2go.domain.route.client;

import org.example.when2go.domain.route.dto.GoogleRouteSearchRequest;
import org.example.when2go.domain.route.dto.GoogleRouteSearchResponse;

public interface RouteClient {

    GoogleRouteSearchResponse search(GoogleRouteSearchRequest request);
}