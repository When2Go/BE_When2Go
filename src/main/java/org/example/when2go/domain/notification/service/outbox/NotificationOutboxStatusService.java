package org.example.when2go.domain.notification.service.outbox;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.notification.entity.NotificationOutboxStatus;
import org.example.when2go.domain.notification.repository.NotificationScheduleOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationOutboxStatusService {

    private final NotificationScheduleOutboxRepository notificationScheduleOutboxRepository;
    private final Clock clock;

    @Transactional
    public void markSent(Long outboxId) {
        notificationScheduleOutboxRepository.markSent(
                List.of(outboxId),
                NotificationOutboxStatus.SENT,
                LocalDateTime.now(clock)
        );
    }

    @Transactional
    public void markRetryableFailure(Long outboxId) {
        notificationScheduleOutboxRepository.markRetryableFailure(
                List.of(outboxId),
                NotificationOutboxStatus.PENDING
        );
    }

    @Transactional
    public void markFailed(Long outboxId) {
        notificationScheduleOutboxRepository.markFailed(
                List.of(outboxId),
                NotificationOutboxStatus.FAILED
        );
    }
}
