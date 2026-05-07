package org.example.when2go.domain.notification.service;

import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.notification.repository.NotificationScheduleOutboxRepository;
import org.example.when2go.domain.notification.repository.NotificationScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationCleanupService {

    private final NotificationScheduleOutboxRepository notificationScheduleOutboxRepository;
    private final NotificationScheduleRepository notificationScheduleRepository;

    @Transactional
    public int deleteByTripId(Long tripId) {
        int outboxCount = notificationScheduleOutboxRepository.deleteByTripId(tripId);
        int scheduleCount = notificationScheduleRepository.deleteByTripId(tripId);
        return outboxCount + scheduleCount;
    }
}
