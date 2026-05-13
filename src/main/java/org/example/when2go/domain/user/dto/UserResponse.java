package org.example.when2go.domain.user.dto;

import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.enums.NotificationMode;
import org.example.when2go.domain.user.enums.Platform;

import java.time.LocalDateTime;

public record UserResponse(
        Long UserId,
        String deviceId,
        Platform platform,
        Integer bufferMinutes,
        NotificationMode notificationMode,
        Boolean widgetEnabled,
        LocalDateTime createdAt
) {
    public static UserResponse from (AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getDeviceId(),
                user.getPlatform(),
                user.getBufferMinutes(),
                user.getNotificationMode(),
                user.getWidgetEnabled(),
                user.getCreatedAt()
        );
    }
}