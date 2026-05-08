package org.example.when2go.domain.notification.service.schedule;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.notification.enums.NotificationScheduleStatus;
import org.example.when2go.domain.notification.repository.NotificationScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationScheduleClaimService {

    private final NotificationScheduleRepository notificationScheduleRepository;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<Long> claimDueSchedules(int limit) {
        List<Long> claimedIds = notificationScheduleRepository.claimDueScheduleIds(limit);
        if (claimedIds.isEmpty()) {
            return List.of();
        }

        notificationScheduleRepository.updateProcessing(
                claimedIds,
                NotificationScheduleStatus.PROCESSING,
                LocalDateTime.now()
        );
        return claimedIds;
    }
}
