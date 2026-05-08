package org.example.when2go.domain.notification.service.schedule;

public record NotificationScheduleOutboxCreationResult(
        int processedCount,
        int createdCount,
        int skippedMissingTokenCount,
        int skippedDuplicateCount
) {
}
