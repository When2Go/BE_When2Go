package org.example.when2go.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.user.dto.BufferMinutesUpdateResponse;
import org.example.when2go.domain.user.dto.FcmTokenUpdateResponse;
import org.example.when2go.domain.user.dto.UserStatusResponse;
import org.example.when2go.domain.user.dto.UserRegisterRequest;
import org.example.when2go.domain.user.dto.UserResponse;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.error.UserErrorCode;
import org.example.when2go.domain.user.repository.AppUserRepository;
import org.example.when2go.global.error.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository appUserRepository;

    @Transactional
    public UserResponse registerOrFind(UserRegisterRequest request) {
        return appUserRepository.findByDeviceId(request.deviceId())
                .map(UserResponse::from)
                .orElseGet(() -> UserResponse.from(appUserRepository.save(AppUser.builder()
                        .deviceId(request.deviceId())
                        .platform(request.platform())
                        .fcmToken(request.fcmToken())
                        .build())));
    }

    @Transactional
    public FcmTokenUpdateResponse updateFcmToken(String deviceId, String fcmToken) {
        AppUser user = appUserRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));
        user.updateFcmToken(fcmToken);
        return FcmTokenUpdateResponse.from(user);
    }

    @Transactional
    public BufferMinutesUpdateResponse updateBufferMinutes(String deviceId, Integer bufferMinutes) {
        AppUser user = appUserRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new DomainException(UserErrorCode.USER_NOT_FOUND));
        user.updateBufferMinutes(bufferMinutes);
        return BufferMinutesUpdateResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserStatusResponse existsByDeviceId(String deviceId) {
        return UserStatusResponse.of(appUserRepository.existsByDeviceId(deviceId));
    }

}
