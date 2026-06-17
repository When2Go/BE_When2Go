package org.example.when2go.domain.reservation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.reservation.controller.docs.ReservationControllerApi;
import org.example.when2go.domain.reservation.dto.request.ReservationCreateRequest;
import org.example.when2go.domain.reservation.dto.response.ReservationCreateResponse;
import org.example.when2go.domain.reservation.dto.response.ReservationListResponse;
import org.example.when2go.domain.reservation.service.ReservationService;
import org.example.when2go.global.response.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController implements ReservationControllerApi {

    private final ReservationService reservationService;

    @Override
    @GetMapping
    public ApiResponse<ReservationListResponse> list(
            @RequestHeader("X-Device-Id") String deviceId
    ) {
        return ApiResponse.success(reservationService.findAllByUser(deviceId));
    }

    @Override
    @PostMapping
    public ApiResponse<ReservationCreateResponse> create(
            @RequestHeader("X-Device-Id") String deviceId,
            @Valid @RequestBody ReservationCreateRequest request
    ) {
        return ApiResponse.success(reservationService.create(deviceId, request));
    }

    @Override
    @DeleteMapping("/{reservationId}")
    public ApiResponse<Void> delete(
            @RequestHeader("X-Device-Id") String deviceId,
            @PathVariable Long reservationId
    ) {
        reservationService.delete(deviceId, reservationId);
        return ApiResponse.success();
    }
}
