package org.example.when2go.domain.notification.event;

public record NotificationOutboxRepublishRequestedEvent(int recoveredCount) {
}
