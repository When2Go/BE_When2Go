package org.example.when2go.domain.notification.service.schedule;

import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.notification.entity.NotificationSchedule;
import org.example.when2go.domain.notification.enums.NotificationType;
import org.example.when2go.domain.notification.repository.NotificationScheduleRepository;
import org.example.when2go.domain.trip.entity.Trip;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationScheduleCreateService {

    private final NotificationScheduleRepository notificationScheduleRepository;

    // Trip을 기반으로 NotificationSchedule 생성
    @Transactional
    public List<NotificationSchedule> createDepartureSchedules(Trip trip) {
        Objects.requireNonNull(trip, "trip must not be null");
        Objects.requireNonNull(trip.getFinalDepartureTime(), "finalDepartureTime must not be null");

        List<NotificationSchedule> schedules = List.of(
                NotificationSchedule.builder()
                        .trip(trip)
                        .user(trip.getUser())
                        .type(NotificationType.DEPART_10MIN)
                        .scheduledAt(trip.getFinalDepartureTime().minusMinutes(10))
                        .build(),
                NotificationSchedule.builder()
                        .trip(trip)
                        .user(trip.getUser())
                        .type(NotificationType.DEPART_NOW)
                        .scheduledAt(trip.getFinalDepartureTime())
                        .build()
        );

        return notificationScheduleRepository.saveAll(schedules);
    }
}
