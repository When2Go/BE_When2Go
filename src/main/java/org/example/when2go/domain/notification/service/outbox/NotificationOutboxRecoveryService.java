package org.example.when2go.domain.notification.service.outbox;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.example.when2go.global.config.notification.NotificationProperties;
import org.example.when2go.domain.notification.entity.NotificationOutboxStatus;
import org.example.when2go.domain.notification.repository.NotificationScheduleOutboxRepository;
import org.springframework.transaction.annotation.Transactional;

// release 단순화로 빈 등록 해제됨. 코드 보존용.
@RequiredArgsConstructor
public class NotificationOutboxRecoveryService {

    private final NotificationScheduleOutboxRepository notificationScheduleOutboxRepository;
    private final NotificationProperties notificationProperties;

    // PROCESSING 상태로 멈춰있는 outbox row를 다시 PENDING으로 되돌린다
    @Transactional
    public int recoverStuckProcessingRows() {
        LocalDateTime threshold = LocalDateTime.now()
                .minus(notificationProperties.getOutbox().getStuckTimeout());

        return notificationScheduleOutboxRepository.resetStuckProcessing(
                NotificationOutboxStatus.PROCESSING,
                NotificationOutboxStatus.PENDING,
                threshold
        );
    }
}
