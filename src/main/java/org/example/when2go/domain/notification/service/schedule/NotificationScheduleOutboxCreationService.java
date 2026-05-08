package org.example.when2go.domain.notification.service.schedule;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.notification.dto.NotificationMessage;
import org.example.when2go.domain.notification.entity.NotificationSchedule;
import org.example.when2go.domain.notification.entity.NotificationScheduleOutbox;
import org.example.when2go.domain.notification.enums.NotificationScheduleStatus;
import org.example.when2go.domain.notification.event.NotificationOutboxesCreatedEvent;
import org.example.when2go.domain.notification.repository.NotificationScheduleOutboxRepository;
import org.example.when2go.domain.notification.repository.NotificationScheduleRepository;
import org.example.when2go.domain.notification.service.message.NotificationMessageBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationScheduleOutboxCreationService {

    private static final String DEDUP_KEY_PREFIX = "notification:";

    private final NotificationScheduleRepository notificationScheduleRepository;
    private final NotificationScheduleOutboxRepository notificationScheduleOutboxRepository;
    private final NotificationMessageBuilder notificationMessageBuilder;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public NotificationScheduleOutboxCreationResult createOutboxes(Collection<Long> scheduleIds) {
        if (scheduleIds.isEmpty()) {
            return new NotificationScheduleOutboxCreationResult(0, 0, 0, 0);
        }

        List<NotificationSchedule> schedules = notificationScheduleRepository
                .findAllByIdInAndStatusWithUserAndTrip(scheduleIds, NotificationScheduleStatus.PROCESSING)
                .stream()
                .sorted(Comparator
                        .comparing(NotificationSchedule::getScheduledAt)
                        .thenComparing(NotificationSchedule::getId))
                .toList();

        logSkippedNonProcessingSchedules(scheduleIds, schedules);

        Set<String> existingDedupKeys = findExistingDedupKeys(schedules);
        int createdCount = 0;
        int skippedMissingTokenCount = 0;
        int skippedDuplicateCount = 0;

        for (NotificationSchedule schedule : schedules) {
            if (!StringUtils.hasText(schedule.getUser().getFcmToken())) {
                skippedMissingTokenCount++;
                log.warn(
                        "event=notification.token_missing scheduleId={} userId={} tripId={} type={}",
                        schedule.getId(),
                        schedule.getUser().getId(),
                        schedule.getTrip().getId(),
                        schedule.getType()
                );
                schedule.markDone();
                continue;
            }

            String dedupKey = dedupKey(schedule);
            if (existingDedupKeys.contains(dedupKey)) {
                skippedDuplicateCount++;
                schedule.markDone();
                continue;
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
            existingDedupKeys.add(dedupKey);
            createdCount++;
            schedule.markDone();
        }

        eventPublisher.publishEvent(new NotificationOutboxesCreatedEvent(
                createdCount,
                skippedMissingTokenCount,
                skippedDuplicateCount
        ));
        return new NotificationScheduleOutboxCreationResult(
                schedules.size(),
                createdCount,
                skippedMissingTokenCount,
                skippedDuplicateCount
        );
    }

    private Set<String> findExistingDedupKeys(List<NotificationSchedule> schedules) {
        List<String> dedupKeys = schedules.stream()
                .map(this::dedupKey)
                .toList();
        if (dedupKeys.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(notificationScheduleOutboxRepository.findDedupKeysIn(dedupKeys));
    }

    private void logSkippedNonProcessingSchedules(
            Collection<Long> requestedIds,
            List<NotificationSchedule> processingSchedules
    ) {
        Set<Long> processingIds = processingSchedules.stream()
                .map(NotificationSchedule::getId)
                .collect(java.util.stream.Collectors.toSet());
        List<Long> skippedIds = requestedIds.stream()
                .filter(id -> !processingIds.contains(id))
                .toList();
        if (skippedIds.isEmpty()) {
            return;
        }
        log.warn("event=notification.schedule_not_processing skippedCount={} scheduleIds={}", skippedIds.size(), skippedIds);
    }

    private String dedupKey(NotificationSchedule schedule) {
        return DEDUP_KEY_PREFIX + schedule.getId();
    }
}
