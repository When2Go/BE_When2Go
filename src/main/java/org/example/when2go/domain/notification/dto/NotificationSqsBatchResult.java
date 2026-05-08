package org.example.when2go.domain.notification.dto;

import java.util.List;

public record NotificationSqsBatchResult(
        List<Long> successIds,
        List<Long> failedIds
) {
}
