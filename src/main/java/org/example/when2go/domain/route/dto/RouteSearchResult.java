package org.example.when2go.domain.route.dto;

import java.util.List;

public record RouteSearchResult(List<Route> routes) {

    public record Route(String duration) {}

    public int totalMinutes() {
        long seconds = Long.parseLong(routes.get(0).duration().replace("s", ""));
        return (int) (seconds / 60);
    }
}
