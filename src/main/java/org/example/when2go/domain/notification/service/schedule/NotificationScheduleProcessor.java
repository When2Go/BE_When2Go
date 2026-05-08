package org.example.when2go.domain.notification.service.schedule;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationScheduleProcessor {

    private final NotificationScheduleClaimService notificationScheduleClaimService;
    private final NotificationScheduleOutboxCreationService notificationScheduleOutboxCreationService;

    public int processDueSchedules(int limit) {
        List<Long> scheduleIds = notificationScheduleClaimService.claimDueSchedules(limit);
        if (scheduleIds.isEmpty()) {
            return 0;
        }

        return notificationScheduleOutboxCreationService.createOutboxes(scheduleIds).processedCount();
    }
}
