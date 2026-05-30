package org.example.when2go.domain.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
import org.example.when2go.domain.reservation.dto.request.ReservationCreateRequest;
import org.example.when2go.domain.reservation.dto.response.ReservationCreateResponse;
import org.example.when2go.domain.reservation.entity.Reservation;
import org.example.when2go.domain.reservation.repository.ReservationRepository;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.entity.Platform;
import org.example.when2go.domain.user.error.UserErrorCode;
import org.example.when2go.domain.user.repository.AppUserRepository;
import org.example.when2go.global.error.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReservationServiceTest {

    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final ReservationService reservationService = new ReservationService(reservationRepository, appUserRepository);

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
                null,
                "집",
                37.5,
                127.0,
                "회사",
                37.55,
                127.05,
                RouteOption.TRANSIT,
                LocalTime.of(9, 0),
                Set.of()
        );

        when(appUserRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> reservationService.create("device-abc", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("repeatDays must not be empty");
    }
}
