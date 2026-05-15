package org.example.when2go.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.example.when2go.domain.notification.enums.NotificationOutboxStatus;
import org.example.when2go.domain.notification.enums.NotificationScheduleStatus;
import org.example.when2go.domain.notification.enums.NotificationType;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.enums.Platform;
import org.junit.jupiter.api.Test;

class NotificationScheduleTest {

    @Test
    void scheduleDefaultsToPendingAndCanMoveToDone() {
        NotificationSchedule schedule = NotificationSchedule.builder()
                .trip(trip())
                .user(user("token"))
                .type(NotificationType.DEPART_NOW)
                .scheduledAt(LocalDateTime.of(2026, 5, 7, 9, 0))
                .build();

        assertThat(schedule.getStatus()).isEqualTo(NotificationScheduleStatus.PENDING);

        schedule.markDone();

        assertThat(schedule.getStatus()).isEqualTo(NotificationScheduleStatus.DONE);
    }

    @Test
    void outboxDefaultsToPendingAndCanMoveToPublished() {
        NotificationSchedule schedule = NotificationSchedule.builder()
                .trip(trip())
                .user(user("token"))
                .type(NotificationType.DEPART_NOW)
                .scheduledAt(LocalDateTime.of(2026, 5, 7, 9, 0))
                .build();
        LocalDateTime publishedAt = LocalDateTime.of(2026, 5, 7, 9, 0);

        NotificationScheduleOutbox outbox = NotificationScheduleOutbox.builder()
                .schedule(schedule)
                .user(schedule.getUser())
                .trip(schedule.getTrip())
                .type(schedule.getType())
                .title("title")
                .body("body")
                .dedupKey("notification:1")
                .build();

        outbox.startProcessing(LocalDateTime.of(2026, 5, 7, 8, 59));
        outbox.markPublished(publishedAt);

        assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.PUBLISHED);
        assertThat(outbox.getProcessingStartedAt()).isNull();
        assertThat(outbox.getPublishedAt()).isEqualTo(publishedAt);
    }

    private AppUser user(String fcmToken) {
        return AppUser.builder()
                .deviceId("device-id")
                .fcmToken(fcmToken)
                .platform(Platform.IOS)
                .build();
    }

    private Trip trip() {
        AppUser user = user("token");
        return Trip.builder()
                .user(user)
                .originName("home")
                .destName("office")
                .originLat(37.1)
                .originLng(127.1)
                .destLat(37.2)
                .destLng(127.2)
                .arrivalTime(LocalDateTime.of(2026, 5, 7, 10, 0))
                .routeOption(RouteOption.TRANSIT)
                .bufferMinutes(10)
                .finalDepartureTime(LocalDateTime.of(2026, 5, 7, 9, 0))
                .build();
    }
}
