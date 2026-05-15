package org.example.when2go.domain.notification.service.schedule;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.global.config.notification.NotificationProperties;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationScheduleProcessor {

    private final NotificationScheduleClaimService notificationScheduleClaimService;
    private final NotificationScheduleOutboxCreationService notificationScheduleOutboxCreationService;
    private final NotificationProperties notificationProperties;

    public int processDueSchedules(int limit) {
        List<Long> scheduleIds = notificationScheduleClaimService.claimDueSchedules(limit);
        if (scheduleIds.isEmpty()) {
            return 0;
        }

        return notificationScheduleOutboxCreationService.createOutboxes(scheduleIds).processedCount();
    }

    public void drainDueSchedules(int claimSize) {
        NotificationProperties.Schedule scheduleProperties = notificationProperties.getSchedule();

        // 이번 event 에서 최대 몇 개 처리할 지
        int maxDrainSize = scheduleProperties.getMaxDrainSize();
        validateDrainSettings(claimSize, maxDrainSize);

        log.info("event=notification.schedule_drain_started claimSize={} maxDrainSize={}", claimSize, maxDrainSize);

        int claimedCount = 0;
        int processedCount = 0;
        int createdCount = 0;
        int batchCount = 0;

        while (claimedCount < maxDrainSize) {
            int batchLimit = Math.min(claimSize, maxDrainSize - claimedCount);
            List<Long> claimedIds = notificationScheduleClaimService.claimDueSchedules(batchLimit);
            if (claimedIds.isEmpty()) {
                logFinished(claimedCount, processedCount, createdCount, batchCount, maxDrainSize);
                return;
            }

            claimedCount += claimedIds.size();
            batchCount++;

            NotificationScheduleOutboxCreationResult result =
                    notificationScheduleOutboxCreationService.createOutboxes(claimedIds);
            processedCount += result.processedCount();
            createdCount += result.createdCount();
        }

        logFinished(claimedCount, processedCount, createdCount, batchCount, maxDrainSize);
    }

    // 결과 로그 출력
    private void logFinished(
            int claimedCount,
            int processedCount,
            int createdCount,
            int batchCount,
            int maxDrainSize
    ) {
        boolean limitReached = claimedCount >= maxDrainSize;
        log.info(
                "event=notification.schedule_drain_finished claimedCount={} processedCount={} createdCount={} batchCount={} limitReached={}",
                claimedCount,
                processedCount,
                createdCount,
                batchCount,
                limitReached
        );
    }

    // size 유효성 검사
    private void validateDrainSettings(int claimSize, int maxDrainSize) {
        if (claimSize < 1) {
            throw new IllegalArgumentException("claimSize must be greater than or equal to 1");
        }
        if (maxDrainSize < 1) {
            throw new IllegalArgumentException("maxDrainSize must be greater than or equal to 1");
        }
    }
}
