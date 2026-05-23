package org.example.when2go.domain.user.dto;

import org.example.when2go.domain.user.entity.AppUser;

public record FcmTokenUpdateResponse(
        Long userId,
        String deviceId,
        String fcmToken
) {
    public static FcmTokenUpdateResponse from(AppUser user) {
        return new FcmTokenUpdateResponse(
                user.getId(),
                user.getDeviceId(),
                user.getFcmToken()
        );
    }
}
