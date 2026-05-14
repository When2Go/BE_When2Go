package org.example.when2go.domain.route.client;

public record RouteSearchResult(int totalMinutes) {

    public RouteSearchResult {
        if (totalMinutes < 0) {
            throw new IllegalArgumentException("totalMinutes must not be negative");
        }
    }
}
