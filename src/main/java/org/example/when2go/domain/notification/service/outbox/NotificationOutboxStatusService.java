package org.example.when2go.domain.notification.service.outbox;

import java.time.LocalDateTime;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.notification.enums.NotificationOutboxStatus;
import org.example.when2go.domain.notification.repository.NotificationScheduleOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationOutboxStatusService {

    private final NotificationScheduleOutboxRepository notificationScheduleOutboxRepository;

    @Transactional
    public void markPublished(Collection<Long> outboxIds) {
        if (outboxIds.isEmpty()) {
            return;
        }
        notificationScheduleOutboxRepository.markPublished(
                outboxIds,
                NotificationOutboxStatus.PUBLISHED,
                LocalDateTime.now()
        );
    }

    @Transactional
    public void markRetryableFailure(Collection<Long> outboxIds) {
        if (outboxIds.isEmpty()) {
            return;
        }
        notificationScheduleOutboxRepository.markRetryableFailure(
                outboxIds,
                NotificationOutboxStatus.PENDING
        );
    }

    @Transactional
    public void markFailed(Collection<Long> outboxIds) {
        if (outboxIds.isEmpty()) {
            return;
        }
        notificationScheduleOutboxRepository.markFailed(
                outboxIds,
                NotificationOutboxStatus.FAILED
        );
    }
}
