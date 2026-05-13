package org.example.when2go.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.example.when2go.domain.user.dto.UserRegisterRequest;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.enums.Platform;
import org.example.when2go.domain.user.repository.AppUserRepository;
import org.example.when2go.domain.user.service.UserService.UserRegisterResult;
import org.junit.jupiter.api.Test;

class UserServiceTest {

    private final AppUserRepository appUserRepository = org.mockito.Mockito.mock(AppUserRepository.class);
    private final UserService userService = new UserService(appUserRepository);

    // 존재하지 않는 deviceId로 요청하면 새 사용자를 저장하고 isNew가 true인지 확인한다.
    @Test
    void registerOrFindCreatesNewUserWhenDeviceIdNotExists() {
        UserRegisterRequest request = new UserRegisterRequest("device-abc", Platform.IOS, "token-123");
        when(appUserRepository.findByDeviceId("device-abc")).thenReturn(Optional.empty());
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        UserRegisterResult result = userService.registerOrFind(request);

        assertThat(result.isNew()).isTrue();
        assertThat(result.response().deviceId()).isEqualTo("device-abc");
        verify(appUserRepository).save(any(AppUser.class));
    }

    // 이미 존재하는 deviceId로 요청하면 저장 없이 isNew가 false인지 확인한다.
    @Test
    void registerOrFindReturnsExistingUserWithoutSavingWhenDeviceIdExists() {
        AppUser existing = AppUser.builder()
                .deviceId("device-abc")
                .platform(Platform.IOS)
                .fcmToken("token-123")
                .build();
        when(appUserRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(existing));

        UserRegisterResult result = userService.registerOrFind(
                new UserRegisterRequest("device-abc", Platform.IOS, "token-123"));

        assertThat(result.isNew()).isFalse();
        assertThat(result.response().deviceId()).isEqualTo("device-abc");
        verify(appUserRepository, never()).save(any());
    }

    // 기존 사용자 재요청 시 저장된 사용자 정보가 그대로 response에 담기는지 확인한다.
    @Test
    void registerOrFindReturnsExistingUserDataAsResponse() {
        AppUser existing = AppUser.builder()
                .deviceId("device-abc")
                .platform(Platform.ANDROID)
                .fcmToken("token-456")
                .build();
        when(appUserRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(existing));

        UserRegisterResult result = userService.registerOrFind(
                new UserRegisterRequest("device-abc", Platform.ANDROID, "token-456"));

        assertThat(result.response().platform()).isEqualTo(Platform.ANDROID);
    }
}