package org.example.when2go.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.example.when2go.domain.reservation.dto.request.ReservationCreateRequest;
import org.example.when2go.domain.reservation.dto.response.ReservationCreateResponse;
import org.example.when2go.domain.reservation.entity.Reservation;
import org.example.when2go.domain.reservation.error.ReservationErrorCode;
import org.example.when2go.domain.reservation.repository.ReservationRepository;
import org.example.when2go.domain.route.client.GoogleRouteClient;
import org.example.when2go.domain.route.dto.GoogleRouteSearchRequest;
import org.example.when2go.domain.route.dto.GoogleRouteSearchResponse;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.entity.Platform;
import org.example.when2go.domain.user.error.UserErrorCode;
import org.example.when2go.domain.user.repository.AppUserRepository;
import org.example.when2go.global.error.DomainException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

class ReservationServiceTest {

    // 2026-06-04 (목요일) 자정으로 고정
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 4);

    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<GoogleRouteClient> googleRouteClientProvider = mock(ObjectProvider.class);
    private final Clock clock = Clock.fixed(
            TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC
    );
    private final ReservationService reservationService = new ReservationService(
            reservationRepository, appUserRepository, tripRepository,
            googleRouteClientProvider, clock
    );

    private AppUser buildUser(Long id) {
        AppUser user = AppUser.builder()
                .deviceId("device-abc")
                .platform(Platform.IOS)
                .fcmToken("token-123")
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private ReservationCreateRequest buildRequest() {
        return new ReservationCreateRequest(
                "출근",
                "집",
                37.5,
                127.0,
                "회사",
                37.55,
                127.05,
                RouteOption.TRANSIT,
                LocalTime.of(9, 0),
                Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        );
    }

    private Reservation buildReservation(Long id, AppUser owner) {
        return buildReservation(id, owner, LocalTime.of(9, 0), Set.of(DayOfWeek.THURSDAY));
    }

    private Reservation buildReservation(Long id, AppUser owner, Set<DayOfWeek> repeatDays) {
        return buildReservation(id, owner, LocalTime.of(9, 0), repeatDays);
    }

    private Reservation buildReservation(Long id, AppUser owner, LocalTime arrivalTime, Set<DayOfWeek> repeatDays) {
        Reservation reservation = Reservation.builder()
                .user(owner)
                .nickname("출근")
                .originName("집")
                .originLat(37.5)
                .originLng(127.0)
                .destName("회사")
                .destLat(37.55)
                .destLng(127.05)
                .routeOption(RouteOption.TRANSIT)
                .arrivalTime(arrivalTime)
                .repeatDays(repeatDays)
                .build();
        ReflectionTestUtils.setField(reservation, "id", id);
        return reservation;
    }

    // GoogleRouteSearchResponse는 record라 mock 불가 — 직접 생성
    private GoogleRouteSearchResponse buildRouteResponse(int durationSeconds) {
        GoogleRouteSearchResponse.Route route = new GoogleRouteSearchResponse.Route(
                null, null, durationSeconds + "s", null, null, null, null, null, null
        );
        return new GoogleRouteSearchResponse(List.of(route), null);
    }

    // 정상 흐름: 사용자가 존재하면 예약이 저장되고 reservationId가 반환되는지 확인한다.
    @Test
    void createSavesReservationAndReturnsResponse() {
        AppUser user = buildUser(1L);
        ReservationCreateRequest request = buildRequest();

        when(appUserRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(user));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation entity = invocation.getArgument(0);
            ReflectionTestUtils.setField(entity, "id", 100L);
            return entity;
        });

        ReservationCreateResponse response = reservationService.create("device-abc", request);

        assertThat(response.reservationId()).isEqualTo(100L);
    }

    // 사용자가 없으면 USER_NOT_FOUND 예외가 발생하는지 확인한다.
    @Test
    void createThrowsWhenUserNotFound() {
        when(appUserRepository.findByDeviceId("device-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.create("device-missing", buildRequest()))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    // repeatDays가 비어 있으면 엔티티 빌드 단계에서 IllegalArgumentException이 발생하는지 확인한다.
    @Test
    void createThrowsWhenRepeatDaysIsEmpty() {
        AppUser user = buildUser(1L);
        ReservationCreateRequest request = new ReservationCreateRequest(
                null, "집", 37.5, 127.0, "회사", 37.55, 127.05,
                RouteOption.TRANSIT, LocalTime.of(9, 0), Set.of()
        );

        when(appUserRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> reservationService.create("device-abc", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("repeatDays must not be empty");
    }

    // 정상 삭제: trip detach 후 reservation이 삭제되는지 확인한다.
    @Test
    void deleteDetachesTripAndDeletesReservation() {
        AppUser user = buildUser(1L);
        Reservation reservation = buildReservation(100L, user);

        when(appUserRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(user));
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));

        reservationService.delete("device-abc", 100L);

        verify(tripRepository).detachReservation(100L);
        verify(reservationRepository).delete(reservation);
    }

    // 사용자가 없으면 USER_NOT_FOUND 예외가 발생한다.
    @Test
    void deleteThrowsWhenUserNotFound() {
        when(appUserRepository.findByDeviceId("device-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.delete("device-missing", 100L))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);

        verify(tripRepository, never()).detachReservation(any());
        verify(reservationRepository, never()).delete(any());
    }

    // 예약이 없으면 RESERVATION_NOT_FOUND 예외가 발생한다.
    @Test
    void deleteThrowsWhenReservationNotFound() {
        AppUser user = buildUser(1L);

        when(appUserRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(user));
        when(reservationRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.delete("device-abc", 100L))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ReservationErrorCode.RESERVATION_NOT_FOUND);

        verify(tripRepository, never()).detachReservation(any());
        verify(reservationRepository, never()).delete(any());
    }

    // 본인 소유 예약이 아니면 RESERVATION_FORBIDDEN 예외가 발생한다.
    @Test
    void deleteThrowsWhenNotOwner() {
        AppUser requester = buildUser(1L);
        AppUser owner = buildUser(2L);
        Reservation reservation = buildReservation(100L, owner);

        when(appUserRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(requester));
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));

        assertThatThrownBy(() -> reservationService.delete("device-abc", 100L))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ReservationErrorCode.RESERVATION_FORBIDDEN);

        verify(tripRepository, never()).detachReservation(any());
        verify(reservationRepository, never()).delete(any());
    }

    // GoogleRouteClient Bean이 없으면 Trip을 생성하지 않는다.
    @Test
    void createTodayTripsSkipsWhenRouteClientMissing() {
        when(googleRouteClientProvider.getIfAvailable()).thenReturn(null);

        reservationService.createTodayTrips();

        verify(tripRepository, never()).save(any());
    }

    // 오늘 요일(목요일)에 해당하는 Reservation이 있고 Trip이 없으면 Trip을 생성한다.
    @Test
    void createTodayTripsCreatesTripForMatchingReservation() {
        AppUser user = buildUser(1L);
        Reservation reservation = buildReservation(100L, user, Set.of(DayOfWeek.THURSDAY));
        GoogleRouteClient client = mock(GoogleRouteClient.class);

        when(googleRouteClientProvider.getIfAvailable()).thenReturn(client);
        when(reservationRepository.findAllWithUser()).thenReturn(List.of(reservation));
        when(tripRepository.existsByReservationIdAndArrivalTimeBetween(eq(100L), any(), any()))
                .thenReturn(false);
        when(client.search(any(GoogleRouteSearchRequest.class)))
                .thenReturn(buildRouteResponse(1800)); // 30분

        reservationService.createTodayTrips();

        verify(tripRepository).save(any(Trip.class));
    }

    // 오늘 날짜 Trip이 이미 있으면 중복 생성하지 않는다.
    @Test
    void createTodayTripsSkipsWhenTripAlreadyExists() {
        AppUser user = buildUser(1L);
        Reservation reservation = buildReservation(100L, user, Set.of(DayOfWeek.THURSDAY));
        GoogleRouteClient client = mock(GoogleRouteClient.class);

        when(googleRouteClientProvider.getIfAvailable()).thenReturn(client);
        when(reservationRepository.findAllWithUser()).thenReturn(List.of(reservation));
        when(tripRepository.existsByReservationIdAndArrivalTimeBetween(eq(100L), any(), any()))
                .thenReturn(true);

        reservationService.createTodayTrips();

        verify(tripRepository, never()).save(any());
    }

    // 오늘 요일이 repeatDays에 없는 Reservation은 건너뛴다.
    @Test
    void createTodayTripsSkipsReservationNotMatchingToday() {
        AppUser user = buildUser(1L);
        Reservation reservation = buildReservation(100L, user, Set.of(DayOfWeek.MONDAY));
        GoogleRouteClient client = mock(GoogleRouteClient.class);

        when(googleRouteClientProvider.getIfAvailable()).thenReturn(client);
        when(reservationRepository.findAllWithUser()).thenReturn(List.of(reservation));

        reservationService.createTodayTrips();

        verify(tripRepository, never()).save(any());
    }

    // 경로 조회 실패 시 해당 Reservation만 skip하고 Trip을 생성하지 않는다.
    @Test
    void createTodayTripsSkipsWhenRouteSearchFails() {
        AppUser user = buildUser(1L);
        Reservation reservation = buildReservation(100L, user, Set.of(DayOfWeek.THURSDAY));
        GoogleRouteClient client = mock(GoogleRouteClient.class);

        when(googleRouteClientProvider.getIfAvailable()).thenReturn(client);
        when(reservationRepository.findAllWithUser()).thenReturn(List.of(reservation));
        when(tripRepository.existsByReservationIdAndArrivalTimeBetween(eq(100L), any(), any()))
                .thenReturn(false);
        when(client.search(any())).thenThrow(new RuntimeException("API 오류"));

        reservationService.createTodayTrips();

        verify(tripRepository, never()).save(any());
    }

    // nextRecalcAt이 현재 시각보다 이전이면 now로 보정한다.
    @Test
    void createTodayTripsUsesNowWhenNextRecalcAtIsInPast() {
        AppUser user = buildUser(1L);
        // 도착 00:10, 소요 30분 → 예상 출발 23:40(전날), nextRecalcAt = 22:40(전날) → 이미 과거
        Reservation reservation = buildReservation(100L, user, LocalTime.of(0, 10), Set.of(DayOfWeek.THURSDAY));
        GoogleRouteClient client = mock(GoogleRouteClient.class);

        when(googleRouteClientProvider.getIfAvailable()).thenReturn(client);
        when(reservationRepository.findAllWithUser()).thenReturn(List.of(reservation));
        when(tripRepository.existsByReservationIdAndArrivalTimeBetween(eq(100L), any(), any()))
                .thenReturn(false);
        when(client.search(any())).thenReturn(buildRouteResponse(1800)); // 30분

        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        reservationService.createTodayTrips();

        verify(tripRepository).save(captor.capture());
        LocalDateTime expectedNow = TODAY.atStartOfDay(); // clock이 자정으로 고정
        assertThat(captor.getValue().getNextRecalcAt()).isEqualTo(expectedNow);
    }

    // 생성된 Trip에 reservation이 연결되는지 확인한다.
    @Test
    void createTodayTripsSetsReservationOnTrip() {
        AppUser user = buildUser(1L);
        Reservation reservation = buildReservation(100L, user, Set.of(DayOfWeek.THURSDAY));
        GoogleRouteClient client = mock(GoogleRouteClient.class);

        when(googleRouteClientProvider.getIfAvailable()).thenReturn(client);
        when(reservationRepository.findAllWithUser()).thenReturn(List.of(reservation));
        when(tripRepository.existsByReservationIdAndArrivalTimeBetween(eq(100L), any(), any()))
                .thenReturn(false);
        when(client.search(any())).thenReturn(buildRouteResponse(1800));

        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        reservationService.createTodayTrips();

        verify(tripRepository).save(captor.capture());
        assertThat(captor.getValue().getReservation()).isEqualTo(reservation);
        assertThat(captor.getValue().getBufferMinutes()).isEqualTo(user.getBufferMinutes());
    }
}
