package org.example.when2go.domain.notification.event;

public record NotificationOutboxesCreatedEvent(
        int createdCount,
        int skippedMissingTokenCount,
        int skippedDuplicateCount
) {
}
