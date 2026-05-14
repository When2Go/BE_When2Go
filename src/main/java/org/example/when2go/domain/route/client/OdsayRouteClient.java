package org.example.when2go.domain.route.client;

import org.example.when2go.domain.route.dto.RouteSearchRequest;
import org.example.when2go.domain.route.dto.RouteSearchResult;

public interface OdsayRouteClient {

    RouteSearchResult search(RouteSearchRequest request);
}
