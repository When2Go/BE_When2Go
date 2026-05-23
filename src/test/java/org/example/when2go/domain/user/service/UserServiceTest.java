package org.example.when2go.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.example.when2go.domain.user.dto.FcmTokenUpdateResponse;
import org.example.when2go.domain.user.dto.UserRegisterRequest;
import org.example.when2go.domain.user.dto.UserResponse;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.entity.Platform;
import org.example.when2go.domain.user.error.UserErrorCode;
import org.example.when2go.domain.user.repository.AppUserRepository;
import org.example.when2go.global.error.DomainException;
import org.junit.jupiter.api.Test;

class UserServiceTest {

    private final AppUserRepository appUserRepository = org.mockito.Mockito.mock(AppUserRepository.class);
    private final UserService userService = new UserService(appUserRepository);

    @Test
    void registerOrFindCreatesNewUserWhenDeviceIdNotExists() {
        when(appUserRepository.findByDeviceId("device-abc")).thenReturn(Optional.empty());
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse result = userService.registerOrFind(new UserRegisterRequest("device-abc", Platform.IOS, "token-123"));

        assertThat(result.deviceId()).isEqualTo("device-abc");
        verify(appUserRepository).save(any(AppUser.class));
    }

    @Test
    void registerOrFindDoesNotSaveWhenDeviceIdExists() {
        AppUser existing = AppUser.builder()
                .deviceId("device-abc")
                .platform(Platform.IOS)
                .fcmToken("token-123")
                .build();
        when(appUserRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(existing));

        UserResponse result = userService.registerOrFind(new UserRegisterRequest("device-abc", Platform.IOS, "token-123"));

        assertThat(result.deviceId()).isEqualTo("device-abc");
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void updateFcmTokenChangesTokenWhenUserExists() {
        AppUser existing = AppUser.builder()
                .deviceId("device-abc")
                .platform(Platform.IOS)
                .fcmToken("old-token")
                .build();
        when(appUserRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(existing));

        FcmTokenUpdateResponse result = userService.updateFcmToken("device-abc", "new-token");

        assertThat(result.deviceId()).isEqualTo("device-abc");
        assertThat(result.fcmToken()).isEqualTo("new-token");
        assertThat(existing.getFcmToken()).isEqualTo("new-token");
    }

    @Test
    void updateFcmTokenThrowsWhenUserNotFound() {
        when(appUserRepository.findByDeviceId("device-missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateFcmToken("device-missing", "new-token"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void updateFcmTokenIsIdempotentForSameToken() {
        AppUser existing = AppUser.builder()
                .deviceId("device-abc")
                .platform(Platform.IOS)
                .fcmToken("same-token")
                .build();
        when(appUserRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(existing));

        FcmTokenUpdateResponse result = userService.updateFcmToken("device-abc", "same-token");

        assertThat(result.fcmToken()).isEqualTo("same-token");
        assertThat(existing.getFcmToken()).isEqualTo("same-token");
    }
}
