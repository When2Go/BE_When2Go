package org.example.when2go.domain.trip.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.trip.dto.TripCreateRequest;
import org.example.when2go.domain.trip.dto.TripCreateResponse;
import org.example.when2go.domain.trip.dto.TripDetailResponse;
import org.example.when2go.domain.trip.dto.TripListResponse;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.entity.TripStatus;
import org.example.when2go.domain.trip.error.TripErrorCode;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.error.UserErrorCode;
import org.example.when2go.domain.user.repository.AppUserRepository;
import org.example.when2go.global.error.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final AppUserRepository appUserRepository;

    private static final long INITIAL_RECALC_LEAD_MINUTES = 60;

    @Transactional
    public TripCreateResponse create(String deviceId, TripCreateRequest request) {
        AppUser user = appUserRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));

        // 재계산 시점 =  도착시간 - 소요시간 - 버퍼
        LocalDateTime estimatedDepartureTime = request.arrivalTime()
                .minusSeconds(request.durationSeconds())
                .minusMinutes(request.bufferMinutes());

        LocalDateTime nextRecalcAt = estimatedDepartureTime.minusMinutes(INITIAL_RECALC_LEAD_MINUTES);
        if (nextRecalcAt.isBefore(LocalDateTime.now())) {
            nextRecalcAt = LocalDateTime.now();
        }

        Trip trip = Trip.builder()
                .user(user)
                .originName(request.originName())
                .destName(request.destName())
                .originLat(request.originLat())
                .originLng(request.originLng())
                .destLat(request.destLat())
                .destLng(request.destLng())
                .arrivalTime(request.arrivalTime())
                .routeOption(RouteOption.TRANSIT) // routeOption TRANSIT으로 고정
                .bufferMinutes(request.bufferMinutes())
                .nextRecalcAt(nextRecalcAt)
                .build();

        return TripCreateResponse.from(tripRepository.save(trip));
    }

    @Transactional(readOnly = true)
    public List<TripListResponse> list(String deviceId, TripStatus status, LocalDate date) {
        AppUser user = appUserRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));

        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

        return tripRepository.findByUserIdAndStatusAndDate(user.getId(), status, startOfDay, endOfDay)
                .stream()
                .map(TripListResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TripDetailResponse getDetail(String deviceId, Long tripId) {
        AppUser user = appUserRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));

        Trip trip = tripRepository.findByIdAndUserId(tripId, user.getId())
                .orElseThrow(() -> new DomainException(TripErrorCode.TRIP_NOT_FOUND));

        return TripDetailResponse.from(trip);
    }

    @Transactional
    public void delete(String deviceId, Long tripId) {
        AppUser user = appUserRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));

        Trip trip = tripRepository.findByIdAndUserId(tripId, user.getId())
                .orElseThrow(() -> new DomainException(TripErrorCode.TRIP_NOT_FOUND));

        tripRepository.delete(trip);
    }
}
