package org.example.when2go.domain.reservation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.reservation.dto.request.ReservationCreateRequest;
import org.example.when2go.domain.reservation.dto.request.ReservationUpdateRequest;
import org.example.when2go.domain.reservation.dto.response.ReservationCreateResponse;
import org.example.when2go.domain.reservation.dto.response.ReservationListResponse;
import org.example.when2go.domain.reservation.entity.Reservation;
import org.example.when2go.domain.reservation.error.ReservationErrorCode;
import org.example.when2go.domain.reservation.repository.ReservationRepository;
import org.example.when2go.domain.route.client.GoogleRouteClient;
import org.example.when2go.domain.route.dto.GoogleRouteSearchRequest;
import org.example.when2go.domain.route.dto.GoogleRouteSearchResponse;
import org.example.when2go.domain.route.dto.RouteSearchRequest;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.error.UserErrorCode;
import org.example.when2go.domain.user.repository.AppUserRepository;
import org.example.when2go.global.error.DomainException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final long INITIAL_RECALC_LEAD_MINUTES = 60;

    private final ReservationRepository reservationRepository;
    private final AppUserRepository appUserRepository;
    private final TripRepository tripRepository;
    private final ObjectProvider<GoogleRouteClient> googleRouteClientProvider;
    private final Clock clock;

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

    @Transactional(readOnly = true)
    public ReservationListResponse findAllByUser(String deviceId) {
        AppUser user = appUserRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));

        return ReservationListResponse.from(
                reservationRepository.findAllByUserIdOrderByArrivalTimeAscIdAsc(user.getId())
        );
    }

    @Transactional
    public void update(String deviceId, Long reservationId, ReservationUpdateRequest request) {
        AppUser user = appUserRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new DomainException(ReservationErrorCode.RESERVATION_NOT_FOUND));

        if (!reservation.getUser().getId().equals(user.getId())) {
            throw new DomainException(ReservationErrorCode.RESERVATION_FORBIDDEN);
        }

        reservation.update(
                request.nickname(),
                request.originName(),
                request.originLat(),
                request.originLng(),
                request.destName(),
                request.destLat(),
                request.destLng(),
                request.routeOption(),
                request.arrivalTime(),
                request.repeatDays()
        );
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

    // 오늘 요일에 해당하는 반복 예약마다 Trip 생성
    @Transactional
    public void createTodayTrips() {
        LocalDate today = LocalDate.now(clock);
        DayOfWeek todayDow = today.getDayOfWeek();

        GoogleRouteClient googleRouteClient = googleRouteClientProvider.getIfAvailable();
        if (googleRouteClient == null) {
            log.warn("event=reservation.trip_create_skipped reason=google_route_client_missing");
            return;
        }

        reservationRepository.findAllWithUser().stream()
                .filter(r -> r.getRepeatDays().contains(todayDow))
                .forEach(r -> createTripIfAbsent(r, today, googleRouteClient));
    }

    private void createTripIfAbsent(Reservation reservation, LocalDate today, GoogleRouteClient client) {
        // 스케줄러 중복 실행 시 같은 날 Trip이 두 번 생성되지 않도록 멱등 보장
        if (tripRepository.existsByReservationIdAndArrivalTimeBetween(
                reservation.getId(),
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay())) {
            return;
        }

        GoogleRouteSearchResponse routeResult;
        try {
            routeResult = client.search(new GoogleRouteSearchRequest(
                    RouteSearchRequest.from(reservation, today)
            ));
        } catch (RuntimeException e) {
            // 경로 조회 실패 시 해당 예약 skip
            log.warn("event=reservation.trip_create_route_failed reservationId={}", reservation.getId(), e);
            return;
        }

        LocalDateTime arrivalTime = today.atTime(reservation.getArrivalTime());
        LocalDateTime estimatedDeparture = arrivalTime
                .minusSeconds(routeResult.totalMinutes() * 60L)
                .minusMinutes(reservation.getUser().getBufferMinutes());

        LocalDateTime nextRecalcAt = estimatedDeparture.minusMinutes(INITIAL_RECALC_LEAD_MINUTES);
        LocalDateTime now = LocalDateTime.now(clock);
        // 도착 시간이 너무 이른 경우 nextRecalcAt이 이미 지났을 수 있어 즉시 재계산 시작
        if (nextRecalcAt.isBefore(now)) {
            nextRecalcAt = now;
        }

        tripRepository.save(Trip.builder()
                .user(reservation.getUser())
                .reservation(reservation)
                .originName(reservation.getOriginName())
                .originLat(reservation.getOriginLat())
                .originLng(reservation.getOriginLng())
                .destName(reservation.getDestName())
                .destLat(reservation.getDestLat())
                .destLng(reservation.getDestLng())
                .arrivalTime(arrivalTime)
                .routeOption(reservation.getRouteOption())
                .bufferMinutes(reservation.getUser().getBufferMinutes())
                .nextRecalcAt(nextRecalcAt)
                .build());
    }

}
