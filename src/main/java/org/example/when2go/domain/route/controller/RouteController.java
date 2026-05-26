package org.example.when2go.domain.route.controller;

import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.route.controller.docs.RouteControllerApi;
import org.example.when2go.domain.route.dto.RouteSearchRequest;
import org.example.when2go.domain.route.dto.GoogleRouteSearchResponse;
import org.example.when2go.domain.route.service.RouteService;
import org.example.when2go.global.response.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController implements RouteControllerApi {

    private final RouteService routeService;

    @Override
    @PostMapping("/search")
    public ApiResponse<GoogleRouteSearchResponse> search(@RequestBody RouteSearchRequest routeSearchRequest) {
        return ApiResponse.success(routeService.search(routeSearchRequest));
    }
}
