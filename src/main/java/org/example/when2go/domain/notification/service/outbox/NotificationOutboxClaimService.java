package org.example.when2go.domain.notification.service.outbox;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.notification.entity.NotificationScheduleOutbox;
import org.example.when2go.domain.notification.entity.NotificationOutboxStatus;
import org.example.when2go.domain.notification.repository.NotificationScheduleOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * PENDING Outbox를 선점(claim)하는 서비스.
 *
 * 여기서 "claim"은 단순 조회가 아니라, 다른 워커가 같은 row를 처리하지 못하도록 소유권을 가져오는 행위를 뜻한다.
 *
 * 처리 순서는 아래과 같다
 * 1. 처리할 PENDING Outbox ID 후보를 락과 함께 조회 (claimPendingOutboxIds)
 * 2. 해당 row들을 PROCESSING 상태로 전환하여 다른 워커가 못 가져가게 표시 (updateProcessing)
 * 3. 실제 처리에 필요한 연관 데이터까지 재조회 후 정렬하여 반환
 *
 * 즉, "조회 + 소유권 선언"을 하나의 트랜잭션 단위로 묶은 작업이다.
 * */
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
