package org.example.when2go.domain.notification.service.outbox;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.notification.entity.NotificationScheduleOutbox;
import org.example.when2go.domain.notification.enums.NotificationOutboxStatus;
import org.example.when2go.domain.notification.repository.NotificationScheduleOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationOutboxClaimService {

    private final NotificationScheduleOutboxRepository notificationScheduleOutboxRepository;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<NotificationScheduleOutbox> claimPendingOutboxes(int limit) {
        List<Long> claimedIds = notificationScheduleOutboxRepository.claimPendingOutboxIds(limit);
        if (claimedIds.isEmpty()) {
            return List.of();
        }

        notificationScheduleOutboxRepository.updateProcessing(
                claimedIds,
                NotificationOutboxStatus.PROCESSING,
                LocalDateTime.now()
        );

        return notificationScheduleOutboxRepository.findAllByIdInWithRelations(claimedIds)
                .stream()
                .sorted(Comparator
                        .comparing(NotificationScheduleOutbox::getCreatedAt)
                        .thenComparing(NotificationScheduleOutbox::getId))
                .toList();
    }
}
