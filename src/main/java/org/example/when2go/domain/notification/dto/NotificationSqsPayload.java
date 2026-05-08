package org.example.when2go.domain.notification.dto;

import org.example.when2go.domain.notification.enums.NotificationType;

public record NotificationSqsPayload(
        Long outboxId,
        Long scheduleId,
        Long tripId,
        Long userId,
        NotificationType type,
        String title,
        String body,
        String dedupKey
) {
}
