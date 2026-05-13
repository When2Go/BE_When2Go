package org.example.when2go.domain.user.repository;

import org.example.when2go.domain.user.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository <AppUser, Long> {
    Optional<AppUser> findByDeviceId(String deviceId);
};

