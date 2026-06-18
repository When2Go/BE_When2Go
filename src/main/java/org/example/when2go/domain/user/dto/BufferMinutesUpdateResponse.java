package org.example.when2go.domain.user.dto;

import org.example.when2go.domain.user.entity.AppUser;

public record BufferMinutesUpdateResponse(
        Long userId,
        String deviceId,
        Integer bufferMinutes
) {
    public static BufferMinutesUpdateResponse from(AppUser user) {
        return new BufferMinutesUpdateResponse(
                user.getId(),
                user.getDeviceId(),
                user.getBufferMinutes()
        );
    }
}
