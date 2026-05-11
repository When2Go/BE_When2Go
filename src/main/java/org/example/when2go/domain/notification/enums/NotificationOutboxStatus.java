package org.example.when2go.domain.notification.enums;

public enum NotificationOutboxStatus {
    PENDING,
    PROCESSING,
    // SQS enqueue 완료를 의미. 이후 실제 FCM 발송은 Lambda 책임이며 Spring은 추적하지 않음.
    PUBLISHED,
    FAILED
}
