package org.example.when2go.domain.notification.service.schedule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.notification.dto.NotificationMessage;
import org.example.when2go.domain.notification.entity.NotificationSchedule;
import org.example.when2go.domain.notification.entity.NotificationScheduleOutbox;
import org.example.when2go.domain.notification.entity.NotificationScheduleStatus;
import org.example.when2go.domain.notification.event.NotificationOutboxesCreatedEvent;
import org.example.when2go.domain.notification.repository.NotificationScheduleOutboxRepository;
import org.example.when2go.domain.notification.repository.NotificationScheduleRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationScheduleBatchService {

    private static final String DEDUP_KEY_PREFIX = "notification:";

    private final NotificationScheduleRepository notificationScheduleRepository;
    private final NotificationScheduleOutboxRepository notificationScheduleOutboxRepository;
    private final ApplicationEventPublisher eventPublisher;

    // FOR UPDATE SKIP LOCKED로 한 batch를 잠그고, outbox 생성과 DONE 전이까지 한 트랜잭션으로 처리
    // 실패 시 batch 전체가 rollback되어 schedule은 PENDING으로 복구
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int processDueScheduleBatch(int limit) {
        List<Long> claimedIds = notificationScheduleRepository.claimDueScheduleIds(limit);
        if (claimedIds.isEmpty()) {
            return 0;
        }

        List<NotificationSchedule> schedules = loadSchedules(claimedIds);
        Set<String> existingDedupKeys = findExistingDedupKeys(schedules);

        List<Long> doneCandidateIds = new ArrayList<>(schedules.size());
        int createdCount = 0;
        int skippedMissingTokenCount = 0;
        int skippedDuplicateCount = 0;

        for (NotificationSchedule schedule : schedules) {
            BatchOutcome outcome = processSchedule(schedule, existingDedupKeys);
            doneCandidateIds.add(schedule.getId());
            switch (outcome) {
                case CREATED -> createdCount++;
                case SKIPPED_MISSING_TOKEN -> skippedMissingTokenCount++;
                case SKIPPED_DUPLICATE -> skippedDuplicateCount++;
            }
        }

        markAllDone(doneCandidateIds);
        publishCreatedEvent(createdCount, skippedMissingTokenCount, skippedDuplicateCount);

        return schedules.size();
    }

    private List<NotificationSchedule> loadSchedules(List<Long> claimedIds) {
        return notificationScheduleRepository.findAllByIdInWithUserAndTrip(claimedIds).stream()
                .sorted(Comparator
                        .comparing(NotificationSchedule::getScheduledAt)
                        .thenComparing(NotificationSchedule::getId))
                .toList();
    }

    private BatchOutcome processSchedule(NotificationSchedule schedule, Set<String> existingDedupKeys) {
        if (!StringUtils.hasText(schedule.getUser().getFcmToken())) {
            log.warn(
                    "event=notification.token_missing scheduleId={} userId={} tripId={} type={}",
                    schedule.getId(),
                    schedule.getUser().getId(),
                    schedule.getTrip().getId(),
                    schedule.getType()
            );
            return BatchOutcome.SKIPPED_MISSING_TOKEN;
        }

        String dedupKey = dedupKey(schedule);
        if (existingDedupKeys.contains(dedupKey)) {
            return BatchOutcome.SKIPPED_DUPLICATE;
        }

        saveOutbox(schedule, dedupKey);
        existingDedupKeys.add(dedupKey);
        return BatchOutcome.CREATED;
    }

    private void saveOutbox(NotificationSchedule schedule, String dedupKey) {
        NotificationMessage message = schedule.getType().toMessage();
        notificationScheduleOutboxRepository.save(NotificationScheduleOutbox.builder()
                .schedule(schedule)
                .user(schedule.getUser())
                .trip(schedule.getTrip())
                .type(schedule.getType())
                .title(message.title())
                .body(message.body())
                .dedupKey(dedupKey)
                .build());
    }

    private void markAllDone(List<Long> doneCandidateIds) {
        if (doneCandidateIds.isEmpty()) {
            return;
        }
        notificationScheduleRepository.updateStatus(doneCandidateIds, NotificationScheduleStatus.DONE);
    }

    private void publishCreatedEvent(int createdCount, int skippedMissingTokenCount, int skippedDuplicateCount) {
        eventPublisher.publishEvent(new NotificationOutboxesCreatedEvent(
                createdCount,
                skippedMissingTokenCount,
                skippedDuplicateCount
        ));
    }

    private Set<String> findExistingDedupKeys(List<NotificationSchedule> schedules) {
        List<String> dedupKeys = schedules.stream()
                .map(this::dedupKey)
                .toList();
        if (dedupKeys.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(notificationScheduleOutboxRepository.findDedupKeysIn(dedupKeys));
    }

    private String dedupKey(NotificationSchedule schedule) {
        return DEDUP_KEY_PREFIX + schedule.getId();
    }

    private enum BatchOutcome {
        CREATED,
        SKIPPED_MISSING_TOKEN,
        SKIPPED_DUPLICATE
    }
}
