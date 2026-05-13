package org.example.when2go.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.user.dto.UserRegisterRequest;
import org.example.when2go.domain.user.dto.UserResponse;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository appUserRepository;

    @Transactional
    public UserRegisterResult registerOrFind(UserRegisterRequest request) {
        Optional<AppUser> existing = appUserRepository.findByDeviceId(request.deviceId());
        if (existing.isPresent()) {
            return new UserRegisterResult(UserResponse.from(existing.get()), false);
        }

        AppUser newUser = AppUser.builder()
                .deviceId(request.deviceId())
                .platform(request.platform())
                .fcmToken(request.fcmToken())
                .build();

        AppUser saved = appUserRepository.save(newUser);
        return new UserRegisterResult(UserResponse.from(saved), true);
    }

    public record UserRegisterResult(UserResponse response, boolean isNew) {}
}