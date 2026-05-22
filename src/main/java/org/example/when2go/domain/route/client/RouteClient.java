package org.example.when2go.domain.route.client;

import org.example.when2go.domain.route.dto.RouteSearchRequest;
import org.example.when2go.domain.route.dto.RouteSearchResponse;

public interface RouteClient {

    RouteSearchResponse search(RouteSearchRequest request);
}