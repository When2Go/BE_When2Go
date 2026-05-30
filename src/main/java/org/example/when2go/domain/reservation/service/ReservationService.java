package org.example.when2go.domain.reservation.service;

import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.reservation.dto.request.ReservationCreateRequest;
import org.example.when2go.domain.reservation.dto.response.ReservationCreateResponse;
import org.example.when2go.domain.reservation.entity.Reservation;
import org.example.when2go.domain.reservation.error.ReservationErrorCode;
import org.example.when2go.domain.reservation.repository.ReservationRepository;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.error.UserErrorCode;
import org.example.when2go.domain.user.repository.AppUserRepository;
import org.example.when2go.global.error.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final AppUserRepository appUserRepository;
    private final TripRepository tripRepository;

    @Transactional
    public ReservationCreateResponse create(String deviceId, ReservationCreateRequest request) {
        AppUser user = appUserRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));

        Reservation reservation = Reservation.builder()
                .user(user)
                .nickname(request.nickname())
                .originName(request.originName())
                .originLat(request.originLat())
                .originLng(request.originLng())
                .destName(request.destName())
                .destLat(request.destLat())
                .destLng(request.destLng())
                .routeOption(request.routeOption())
                .arrivalTime(request.arrivalTime())
                .repeatDays(request.repeatDays())
                .build();

        return ReservationCreateResponse.from(reservationRepository.save(reservation));
    }

    @Transactional
    public void delete(String deviceId, Long reservationId) {
        AppUser user = appUserRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new DomainException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.getUser().getId().equals(user.getId())) {
            throw new DomainException(ReservationErrorCode.RESERVATION_FORBIDDEN);
        }

        tripRepository.detachReservation(reservationId);
        reservationRepository.delete(reservation);
    }
}
