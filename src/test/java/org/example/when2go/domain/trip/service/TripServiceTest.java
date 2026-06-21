package org.example.when2go.domain.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.example.when2go.domain.notification.service.schedule.NotificationScheduleCreateService;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.trip.dto.TripDetailResponse;
import org.example.when2go.domain.trip.dto.TripListResponse;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.entity.TripStatus;
import org.example.when2go.domain.trip.error.TripErrorCode;
import org.example.when2go.domain.trip.event.TripCreatedEvent;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.entity.Platform;
import org.example.when2go.domain.user.error.UserErrorCode;
import org.example.when2go.domain.user.repository.AppUserRepository;
import org.example.when2go.global.error.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class TripServiceTest {

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final NotificationScheduleCreateService notificationScheduleCreateService =
            mock(NotificationScheduleCreateService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final jakarta.persistence.EntityManager entityManager =
            mock(jakarta.persistence.EntityManager.class);
    private final TripService tripService = new TripService(
            tripRepository, appUserRepository, notificationScheduleCreateService, eventPublisher, entityManager
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

    private Trip buildTrip(Long id, AppUser user) {
        Trip trip = Trip.builder()
                .user(user)
                .originName("선릉역")
                .destName("강남역")
                .originLat(37.5045)
                .originLng(127.0498)
                .destLat(37.4979)
                .destLng(127.0276)
                .arrivalTime(LocalDateTime.of(2026, 5, 29, 18, 0))
                .routeOption(RouteOption.TRANSIT)
                .bufferMinutes(10)
                .nextRecalcAt(LocalDateTime.now().plusHours(1))
                .build();
        ReflectionTestUtils.setField(trip, "id", id);
        return trip;
    }

    @Test
    void listReturnsTripsForUser() {
        AppUser user = buildUser(1L);
        Trip trip = buildTrip(1L, user);
        LocalDate date = LocalDate.of(2026, 5, 29);

        when(appUserRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(user));
        when(tripRepository.findByUserIdAndStatusAndDate(
                1L, TripStatus.PENDING,
                date.atStartOfDay(),
                date.plusDays(1).atStartOfDay()
        )).thenReturn(List.of(trip));

        List<TripListResponse> result = tripService.list("device-abc", TripStatus.PENDING, date);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tripId()).isEqualTo(1L);
    }

    @Test
    void listThrowsWhenUserNotFound() {
        when(appUserRepository.findByDeviceId("device-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.list("device-missing", TripStatus.PENDING, LocalDate.now()))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void getDetailReturnsTripDetail() {
        AppUser user = buildUser(1L);
        Trip trip = buildTrip(1L, user);

        when(appUserRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(user));
        when(tripRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(trip));

        TripDetailResponse result = tripService.getDetail("device-abc", 1L);

        assertThat(result.tripId()).isEqualTo(1L);
        assertThat(result.originName()).isEqualTo("선릉역");
    }

    @Test
    void getDetail에_저장된_추천이_있으면_역직렬화하여_응답한다() {
        AppUser user = buildUser(1L);
        Trip trip = buildTrip(1L, user);
        trip.updateNearbyRecommendations(
                "[{\"name\":\"○○카페\",\"description\":\"분위기 좋은 카페\",\"category\":\"카페\"}]"
        );

        when(appUserRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(user));
        when(tripRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(trip));

        TripDetailResponse result = tripService.getDetail("device-abc", 1L);

        assertThat(result.nearbyRecommendations()).hasSize(1);
        assertThat(result.nearbyRecommendations().get(0).name()).isEqualTo("○○카페");
    }

    @Test
    void getDetail에_추천이_null이면_빈_리스트로_응답한다() {
        AppUser user = buildUser(1L);
        Trip trip = buildTrip(1L, user);
        // nearbyRecommendations 미설정 → null

        when(appUserRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(user));
        when(tripRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(trip));

        TripDetailResponse result = tripService.getDetail("device-abc", 1L);

        assertThat(result.nearbyRecommendations()).isEmpty();
    }

    @Test
    void getDetail에_손상된_JSON이_저장되어있으면_빈_리스트로_응답한다() {
        AppUser user = buildUser(1L);
        Trip trip = buildTrip(1L, user);
        trip.updateNearbyRecommendations("not-a-json");

        when(appUserRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(user));
        when(tripRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(trip));

        TripDetailResponse result = tripService.getDetail("device-abc", 1L);

        assertThat(result.nearbyRecommendations()).isEmpty();
    }

    @Test
    void create는_저장_후_TripCreatedEvent를_발행한다() {
        AppUser user = buildUser(1L);
        when(appUserRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(user));
        when(tripRepository.save(org.mockito.ArgumentMatchers.any(Trip.class)))
                .thenAnswer(invocation -> {
                    Trip t = invocation.getArgument(0);
                    ReflectionTestUtils.setField(t, "id", 42L);
                    return t;
                });

        org.example.when2go.domain.trip.dto.TripCreateRequest request =
                new org.example.when2go.domain.trip.dto.TripCreateRequest(
                        "선릉역", 37.5045, 127.0498,
                        "강남역", 37.4979, 127.0276,
                        LocalDateTime.of(2026, 6, 20, 18, 0),
                        10,
                        600
                );

        tripService.create("device-abc", request);

        verify(eventPublisher).publishEvent(
                new TripCreatedEvent(42L, "강남역", 37.4979, 127.0276)
        );
    }

    @Test
    void getDetailThrowsWhenUserNotFound() {
        when(appUserRepository.findByDeviceId("device-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.getDetail("device-missing", 1L))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void getDetailThrowsWhenTripNotFound() {
        AppUser user = buildUser(1L);

        when(appUserRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(user));
        when(tripRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.getDetail("device-abc", 99L))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(TripErrorCode.TRIP_NOT_FOUND);
    }

    @Test
    void deleteRemovesTrip() {
        AppUser user = buildUser(1L);
        Trip trip = buildTrip(1L, user);

        when(appUserRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(user));
        when(tripRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(trip));

        tripService.delete("device-abc", 1L);

        verify(tripRepository).delete(trip);
    }

    @Test
    void deleteThrowsWhenTripNotFound() {
        AppUser user = buildUser(1L);

        when(appUserRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(user));
        when(tripRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.delete("device-abc", 99L))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(TripErrorCode.TRIP_NOT_FOUND);
    }
}
