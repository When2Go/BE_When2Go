package org.example.when2go.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.user.dto.UserRegisterRequest;
import org.example.when2go.domain.user.dto.UserResponse;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.repository.AppUserRepository;
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

}