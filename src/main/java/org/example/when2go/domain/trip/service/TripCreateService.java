package org.example.when2go.domain.trip.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.trip.dto.TripCreateRequest;
import org.example.when2go.domain.trip.dto.TripCreateResponse;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.error.UserErrorCode;
import org.example.when2go.domain.user.repository.AppUserRepository;
import org.example.when2go.global.error.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripCreateService {

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
}
