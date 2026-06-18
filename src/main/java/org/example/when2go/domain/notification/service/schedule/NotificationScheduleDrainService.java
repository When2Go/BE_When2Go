package org.example.when2go.domain.notification.service.schedule;

import lombok.RequiredArgsConstructor;
import org.example.when2go.global.config.notification.NotificationProperties;

// release 단순화로 빈 등록 해제됨. 코드 보존용.
@RequiredArgsConstructor
public class NotificationScheduleDrainService {

    private final NotificationScheduleBatchService notificationScheduleBatchService;
    private final NotificationProperties notificationProperties;

    public void drainDueSchedules(int claimSize) {
        int maxDrainSize = notificationProperties.getSchedule().getMaxDrainSize();
        validateDrainSettings(claimSize, maxDrainSize);

        int claimedCount = 0;
        while (claimedCount < maxDrainSize) {
            int batchLimit = Math.min(claimSize, maxDrainSize - claimedCount);
            int processedCount = notificationScheduleBatchService.processDueScheduleBatch(batchLimit);
            if (processedCount == 0) {
                return;
            }
            claimedCount += processedCount;
        }
    }

    private void validateDrainSettings(int claimSize, int maxDrainSize) {
        if (claimSize < 1) {
            throw new IllegalArgumentException("claimSize must be greater than or equal to 1");
        }
        if (maxDrainSize < 1) {
            throw new IllegalArgumentException("maxDrainSize must be greater than or equal to 1");
        }
    }
}
