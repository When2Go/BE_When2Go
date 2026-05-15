package org.example.when2go.domain.route.client;

import org.example.when2go.domain.route.dto.RouteSearchRequest;
import org.example.when2go.domain.route.dto.RouteSearchResult;

public interface GoogleRouteClient {

    RouteSearchResult search(RouteSearchRequest request);
}