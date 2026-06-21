package org.example.when2go.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.global.common.entity.BaseEntity;

@Getter
@Entity
@Table(
        name = "notification_schedules",
        indexes = {
                @Index(name = "idx_notification_schedule_due", columnList = "status, scheduled_at, id"),
                @Index(name = "idx_notification_schedule_trip", columnList = "trip_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSchedule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationScheduleStatus status = NotificationScheduleStatus.PENDING;

    @Builder
    private NotificationSchedule(
            Trip trip,
            AppUser user,
            NotificationType type,
            LocalDateTime scheduledAt,
            NotificationScheduleStatus status
    ) {
        this.trip = Objects.requireNonNull(trip, "trip must not be null");
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.scheduledAt = Objects.requireNonNull(scheduledAt, "scheduledAt must not be null");
        this.status = status == null ? NotificationScheduleStatus.PENDING : status;
    }

    public void markDone() {
        this.status = NotificationScheduleStatus.DONE;
    }

    public void markFailed() {
        this.status = NotificationScheduleStatus.FAILED;
    }

    public void markExpired() {
        this.status = NotificationScheduleStatus.EXPIRED;
    }
}
