package org.example.when2go.domain.trip.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.trip.controller.docs.TripControllerApi;
import org.example.when2go.domain.trip.dto.TripCreateRequest;
import org.example.when2go.domain.trip.dto.TripCreateResponse;
import org.example.when2go.domain.trip.dto.TripDetailResponse;
import org.example.when2go.domain.trip.dto.TripListResponse;
import org.example.when2go.domain.trip.entity.TripStatus;
import org.example.when2go.domain.trip.service.TripService;
import org.example.when2go.global.response.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController implements TripControllerApi {

    private final TripService tripService;

    @Override
    @PostMapping
    public ApiResponse<TripCreateResponse> create(
            @RequestHeader("X-Device-Id")
            @NotBlank
            @Size(min = 36, max = 36)
            String deviceId,
            @Valid @RequestBody TripCreateRequest request
    ) {
        return ApiResponse.success(tripService.create(deviceId, request));
    }

    @Override
    @GetMapping
    public ApiResponse<List<TripListResponse>> list(
            @RequestHeader("X-Device-Id")
            @NotBlank
            @Size(min = 36, max = 36)
            String deviceId,
            @RequestParam TripStatus status,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)LocalDate date
    ) {
        return ApiResponse.success(tripService.list(deviceId, status, date));
    }

    @Override
    @GetMapping("/{tripId}")
    public ApiResponse<TripDetailResponse> getDetail(
            @RequestHeader("X-Device-Id") String deviceId,
            @PathVariable Long tripId
    ) {
        return ApiResponse.success(tripService.getDetail(deviceId, tripId));
    }

    @Override
    @DeleteMapping("/{tripId}")
    public ApiResponse<Void> delete(
            @RequestHeader("X-Device-Id") String deviceId,
            @PathVariable Long tripId
    ) {
        tripService.delete(deviceId, tripId);
        return ApiResponse.success();
    }
}
