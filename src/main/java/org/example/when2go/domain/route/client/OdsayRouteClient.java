package org.example.when2go.domain.route.client;

import org.example.when2go.domain.trip.entity.Trip;

public interface OdsayRouteClient {

    RouteSearchResult search(Trip trip);
}
