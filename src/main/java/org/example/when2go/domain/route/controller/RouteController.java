package org.example.when2go.domain.route.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.route.dto.RouteDTO;
import org.example.when2go.domain.route.dto.RouteSearchResponse;
import org.example.when2go.domain.route.service.RouteService;
import org.example.when2go.global.response.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @PostMapping("/search")
    public ApiResponse<RouteSearchResponse> search(@Valid @RequestBody RouteDTO routeDTO) {
        return ApiResponse.success(routeService.search(routeDTO));
    }
}
