package org.example.when2go.domain.notification.service.dispatch;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.notification.entity.NotificationSchedule;
import org.example.when2go.domain.notification.repository.NotificationScheduleRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int GRACE_PERIOD_MINUTES = 3;

    private final NotificationScheduleRepository notificationScheduleRepository;
    private final NotificationDispatcher notificationDispatcher;
    private final Clock clock;

    public void dispatchDueSchedules() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<NotificationSchedule> dueSchedules = notificationScheduleRepository.findDueSchedules(
                now,
                PageRequest.of(0, DEFAULT_LIMIT)
        );
        if (dueSchedules.isEmpty()) {
            return;
        }

        LocalDateTime expirationThreshold = now.minusMinutes(GRACE_PERIOD_MINUTES);
        for (NotificationSchedule schedule : dueSchedules) {
            try {
                notificationDispatcher.dispatchOne(schedule.getId(), expirationThreshold);
            } catch (Exception e) {
                log.error("event=notification.dispatch_failed scheduleId={}", schedule.getId(), e);
            }
        }
    }
}
