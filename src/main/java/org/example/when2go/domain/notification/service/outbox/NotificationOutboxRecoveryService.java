package org.example.when2go.domain.notification.service.outbox;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.notification.entity.NotificationScheduleOutbox;
import org.example.when2go.domain.notification.event.NotificationOutboxRepublishRequestedEvent;
import org.example.when2go.domain.notification.repository.NotificationScheduleOutboxRepository;
import org.example.when2go.global.config.notification.NotificationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationOutboxRecoveryService {

    private final NotificationScheduleOutboxRepository notificationScheduleOutboxRepository;
    private final NotificationOutboxStatusService notificationOutboxStatusService;
    private final NotificationProperties notificationProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void recoverStuckOutboxes() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime stuckThreshold = now.minus(notificationProperties.getOutbox().getStuckTimeout());
        LocalDateTime staleThreshold = now.minus(notificationProperties.getOutbox().getStaleTimeout());
        int claimSize = notificationProperties.getOutbox().getClaimSize();
        int maxRetry = notificationProperties.getOutbox().getMaxRetry();

        List<Long> stuckIds = notificationScheduleOutboxRepository.claimStuckProcessingIds(stuckThreshold, claimSize);
        List<Long> staleIds = notificationScheduleOutboxRepository.claimStalePendingIds(staleThreshold, claimSize);

        List<Long> targetIds = new ArrayList<>(stuckIds.size() + staleIds.size());
        targetIds.addAll(stuckIds);
        targetIds.addAll(staleIds);
        if (targetIds.isEmpty()) {
            return;
        }

        List<NotificationScheduleOutbox> rows = notificationScheduleOutboxRepository.findAllByIdIn(targetIds);
        int recovered = 0;

        for (NotificationScheduleOutbox outbox : rows) {
            int nextRetry = outbox.getRetryCount() + 1;
            if (nextRetry > maxRetry) {
                notificationOutboxStatusService.markFailed(outbox.getId());
                log.warn(
                        "event=notification.outbox_failed outboxId={} retryCount={}",
                        outbox.getId(),
                        nextRetry
                );
            } else {
                notificationOutboxStatusService.markRetryableFailure(outbox.getId());
                recovered++;
                log.info(
                        "event=notification.outbox_recovered outboxId={} retryCount={}",
                        outbox.getId(),
                        nextRetry
                );
            }
        }

        if (recovered > 0) {
            eventPublisher.publishEvent(new NotificationOutboxRepublishRequestedEvent(recovered));
        }
    }
}
