package org.example.when2go.domain.trip.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
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
public class TripController {

    private final TripCreateService tripCreateService;

    @PostMapping
    public ApiResponse<TripCreateResponse> create(
            @RequestHeader("X-Device-Id")
            @NotBlank
            @Size(min = 36, max = 36)
            String deviceId,
            @Valid @RequestBody TripCreateRequest request
    ) {
        return ApiResponse.success(tripCreateService.create(deviceId, request));
    }
}