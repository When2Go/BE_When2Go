package org.example.when2go.domain.notification.service.schedule;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.notification.dto.NotificationMessage;
import org.example.when2go.domain.notification.entity.NotificationSchedule;
import org.example.when2go.domain.notification.entity.NotificationScheduleOutbox;
import org.example.when2go.domain.notification.enums.NotificationScheduleStatus;
import org.example.when2go.domain.notification.repository.NotificationScheduleOutboxRepository;
import org.example.when2go.domain.notification.repository.NotificationScheduleRepository;
import org.example.when2go.domain.notification.service.message.NotificationMessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationScheduleProcessor {

    private static final String DEDUP_KEY_PREFIX = "notification:";

    private final NotificationScheduleRepository notificationScheduleRepository;
    private final NotificationScheduleOutboxRepository notificationScheduleOutboxRepository;
    private final NotificationMessageBuilder notificationMessageBuilder;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int processDueSchedules(int limit) {
        List<Long> claimedIds = notificationScheduleRepository.claimDueScheduleIds(limit);
        if (claimedIds.isEmpty()) {
            return 0;
        }

        notificationScheduleRepository.updateProcessing(
                claimedIds,
                NotificationScheduleStatus.PROCESSING,
                LocalDateTime.now()
        );

        List<NotificationSchedule> schedules = notificationScheduleRepository
                .findAllByIdInWithUserAndTrip(claimedIds)
                .stream()
                .sorted(Comparator
                        .comparing(NotificationSchedule::getScheduledAt)
                        .thenComparing(NotificationSchedule::getId))
                .toList();

        schedules.forEach(this::processSchedule);
        return schedules.size();
    }

    private void processSchedule(NotificationSchedule schedule) {
        if (!StringUtils.hasText(schedule.getUser().getFcmToken())) {
            log.warn(
                    "event=notification.token_missing scheduleId={} userId={} tripId={} type={}",
                    schedule.getId(),
                    schedule.getUser().getId(),
                    schedule.getTrip().getId(),
                    schedule.getType()
            );
            schedule.markDone();
            return;
        }

        String dedupKey = DEDUP_KEY_PREFIX + schedule.getId();
        if (notificationScheduleOutboxRepository.existsByDedupKey(dedupKey)) {
            schedule.markDone();
            return;
        }

        NotificationMessage message = notificationMessageBuilder.build(schedule);
        notificationScheduleOutboxRepository.save(NotificationScheduleOutbox.builder()
                .schedule(schedule)
                .user(schedule.getUser())
                .trip(schedule.getTrip())
                .type(schedule.getType())
                .title(message.title())
                .body(message.body())
                .dedupKey(dedupKey)
                .build());
        schedule.markDone();
    }
}
