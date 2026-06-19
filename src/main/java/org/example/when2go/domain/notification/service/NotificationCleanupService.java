package org.example.when2go.domain.notification.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.notification.entity.NotificationOutboxStatus;
import org.example.when2go.domain.notification.repository.NotificationScheduleOutboxRepository;
import org.example.when2go.domain.notification.repository.NotificationScheduleRepository;
import org.springframework.transaction.annotation.Transactional;

// release 단순화로 빈 등록 해제됨. 코드 보존용.
@RequiredArgsConstructor
public class NotificationCleanupService {

    private static final List<NotificationOutboxStatus> DELETABLE_OUTBOX_STATUSES = List.of(
            NotificationOutboxStatus.PENDING,
            NotificationOutboxStatus.PUBLISHED,
            NotificationOutboxStatus.FAILED
    );

    private final NotificationScheduleOutboxRepository notificationScheduleOutboxRepository;
    private final NotificationScheduleRepository notificationScheduleRepository;

    @Transactional
    public int deleteByTripId(Long tripId) {
        int outboxCount = notificationScheduleOutboxRepository.deleteByTripIdAndStatusIn(
                tripId,
                DELETABLE_OUTBOX_STATUSES
        );
        int scheduleCount = notificationScheduleRepository.deleteByTripIdWithoutOutboxes(tripId);
        return outboxCount + scheduleCount;
    }
}
