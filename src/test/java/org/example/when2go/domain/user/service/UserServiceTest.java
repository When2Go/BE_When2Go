package org.example.when2go.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.example.when2go.domain.user.dto.UserRegisterRequest;
import org.example.when2go.domain.user.dto.UserResponse;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.entity.Platform;
import org.example.when2go.domain.user.repository.AppUserRepository;
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
}
