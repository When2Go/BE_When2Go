package org.example.when2go.domain.notification.dto;

public record NotificationSqsPayload(
        String outboxId,
        String fcmToken,
        String title,
        String body,
        NotificationData data
) {
    public record NotificationData(
            String tripId,
            String type
    ) {}
}
