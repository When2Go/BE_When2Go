package org.example.when2go.domain.notification.service.schedule;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.notification.entity.NotificationSchedule;
import org.example.when2go.domain.notification.entity.NotificationType;
import org.example.when2go.domain.notification.repository.NotificationScheduleRepository;
import org.example.when2go.domain.trip.entity.Trip;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationScheduleCreateService {

    private final NotificationScheduleRepository notificationScheduleRepository;
    private final Clock clock;

    // Trip을 기반으로 NotificationSchedule 생성
    @Transactional
    public List<NotificationSchedule> createDepartureSchedules(Trip trip) {
        Objects.requireNonNull(trip, "trip must not be null");
        LocalDateTime departureTime = Objects.requireNonNull(
                trip.getFinalDepartureTime(), "finalDepartureTime must not be null"
        );

        LocalDateTime now = LocalDateTime.now(clock);
        // 출발 시각이 이미 지난 경우, 정상 알림 대신 즉시 IMMEDIATE_LATE 1개만 예약
        if (!departureTime.isAfter(now)) {
            return notificationScheduleRepository.saveAll(List.of(
                    NotificationSchedule.builder()
                            .trip(trip)
                            .user(trip.getUser())
                            .type(NotificationType.IMMEDIATE_LATE)
                            .scheduledAt(now)
                            .build()
            ));
        }

        return notificationScheduleRepository.saveAll(List.of(
                NotificationSchedule.builder()
                        .trip(trip)
                        .user(trip.getUser())
                        .type(NotificationType.DEPART_10MIN)
                        .scheduledAt(departureTime.minusMinutes(10))
                        .build(),
                NotificationSchedule.builder()
                        .trip(trip)
                        .user(trip.getUser())
                        .type(NotificationType.DEPART_NOW)
                        .scheduledAt(departureTime)
                        .build()
        ));
    }
}
