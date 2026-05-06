package org.example.when2go.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.when2go.domain.user.enums.NotificationMode;
import org.example.when2go.domain.user.enums.Platform;
import org.example.when2go.global.common.entity.BaseEntity;

@Getter
@Entity
@Table(name = "app_users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser extends BaseEntity {

    private static final int DEFAULT_BUFFER_MINUTES = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, unique = true)
    private String deviceId;

    @Column(name = "fcm_token", length = 512)
    private String fcmToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Platform platform;

    @Column(name = "buffer_minutes", nullable = false)
    private Integer bufferMinutes = DEFAULT_BUFFER_MINUTES;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_mode", nullable = false, length = 30)
    private NotificationMode notificationMode = NotificationMode.SOUND_AND_VIBRATE;

    @Column(name = "widget_enabled", nullable = false)
    private Boolean widgetEnabled = true;

    @Builder
    private AppUser(
            String deviceId,
            String fcmToken,
            Platform platform,
            Integer bufferMinutes,
            NotificationMode notificationMode,
            Boolean widgetEnabled
    ) {
        this.deviceId = deviceId;
        this.fcmToken = fcmToken;
        this.platform = platform;
        this.bufferMinutes = bufferMinutes == null ? DEFAULT_BUFFER_MINUTES : bufferMinutes;
        this.notificationMode = notificationMode == null
                ? NotificationMode.SOUND_AND_VIBRATE
                : notificationMode;
        this.widgetEnabled = widgetEnabled == null || widgetEnabled;
    }

}
