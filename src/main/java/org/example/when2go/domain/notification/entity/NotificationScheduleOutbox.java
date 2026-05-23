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
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
        name = "notification_schedule_outbox",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notification_schedule_outbox_dedup_key", columnNames = "dedup_key"),
                @UniqueConstraint(name = "uk_notification_schedule_outbox_schedule_id", columnNames = "schedule_id")
        },
        indexes = {
                @Index(name = "idx_notification_schedule_outbox_pending", columnList = "status, created_at, id"),
                @Index(name = "idx_notification_schedule_outbox_stuck", columnList = "status, processing_started_at, id"),
                @Index(name = "idx_notification_schedule_outbox_trip", columnList = "trip_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationScheduleOutbox extends BaseEntity {

    private static final int DEFAULT_RETRY_COUNT = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private NotificationSchedule schedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationOutboxStatus status = NotificationOutboxStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = DEFAULT_RETRY_COUNT;

    @Column(name = "dedup_key", nullable = false)
    private String dedupKey;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Builder
    private NotificationScheduleOutbox(
            NotificationSchedule schedule,
            AppUser user,
            Trip trip,
            NotificationType type,
            String title,
            String body,
            NotificationOutboxStatus status,
            Integer retryCount,
            String dedupKey,
            LocalDateTime processingStartedAt,
            LocalDateTime publishedAt
    ) {
        this.schedule = Objects.requireNonNull(schedule, "schedule must not be null");
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.trip = Objects.requireNonNull(trip, "trip must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.body = Objects.requireNonNull(body, "body must not be null");
        this.status = status == null ? NotificationOutboxStatus.PENDING : status;
        this.retryCount = retryCount == null ? DEFAULT_RETRY_COUNT : retryCount;
        this.dedupKey = Objects.requireNonNull(dedupKey, "dedupKey must not be null");
        this.processingStartedAt = processingStartedAt;
        this.publishedAt = publishedAt;
    }

    public void startProcessing(LocalDateTime now) {
        this.status = NotificationOutboxStatus.PROCESSING;
        this.processingStartedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void markPublished(LocalDateTime now) {
        this.status = NotificationOutboxStatus.PUBLISHED;
        this.processingStartedAt = null;
        this.publishedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void markRetryableFailure() {
        this.status = NotificationOutboxStatus.PENDING;
        this.retryCount += 1;
        this.processingStartedAt = null;
    }

    public void markFailed() {
        this.status = NotificationOutboxStatus.FAILED;
        this.processingStartedAt = null;
    }
}
