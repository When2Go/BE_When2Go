package org.example.when2go.domain.notification.service.recovery;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.example.when2go.global.config.notification.NotificationProperties;
import org.example.when2go.domain.notification.enums.NotificationOutboxStatus;
import org.example.when2go.domain.notification.enums.NotificationScheduleStatus;
import org.example.when2go.domain.notification.repository.NotificationScheduleOutboxRepository;
import org.example.when2go.domain.notification.repository.NotificationScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationProcessingRecoveryService {

    private final NotificationScheduleRepository notificationScheduleRepository;
    private final NotificationScheduleOutboxRepository notificationScheduleOutboxRepository;
    private final NotificationProperties notificationProperties;

    // 오래된 PROCESSING row를 다시 PENDING으로 돌린다
    @Transactional
    public int recoverStuckProcessingRows() {
        LocalDateTime threshold = LocalDateTime.now()
                .minus(notificationProperties.getOutbox().getStuckTimeout());

        int scheduleCount = notificationScheduleRepository.resetStuckProcessing(
                NotificationScheduleStatus.PROCESSING,
                NotificationScheduleStatus.PENDING,
                threshold
        );
        int outboxCount = notificationScheduleOutboxRepository.resetStuckProcessing(
                NotificationOutboxStatus.PROCESSING,
                NotificationOutboxStatus.PENDING,
                threshold
        );

        return scheduleCount + outboxCount;
    }
}
