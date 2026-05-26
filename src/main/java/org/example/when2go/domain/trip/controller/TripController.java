package org.example.when2go.domain.trip.controller;

import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.trip.controller.docs.TripControllerApi;
import org.example.when2go.domain.trip.dto.TripCreateRequest;
import org.example.when2go.domain.trip.dto.TripCreateResponse;
import org.example.when2go.domain.trip.service.TripCreateService;
import org.example.when2go.global.response.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController implements TripControllerApi {

    private final TripCreateService tripCreateService;

    @Override
    @PostMapping
    public ApiResponse<TripCreateResponse> create(
            @RequestHeader("X-Device-Id") String deviceId,
            @RequestBody TripCreateRequest request
    ) {
        return ApiResponse.success(tripCreateService.create(deviceId, request));
    }
}
