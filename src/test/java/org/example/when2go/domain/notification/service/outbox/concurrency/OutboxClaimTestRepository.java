package org.example.when2go.domain.notification.service.outbox.concurrency;

import java.util.List;
import org.example.when2go.domain.notification.entity.NotificationScheduleOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * SKIP LOCKED 비교 실험 전용 Repository.
 *
 * 운영 Repository(NotificationScheduleOutboxRepository) 는 FOR UPDATE SKIP LOCKED 한 가지만
 * 정의되어 있어, 락 없음 / FOR UPDATE 만 두 케이스를 비교하려면 별도 native query 가 필요하다.
 */
public interface OutboxClaimTestRepository extends JpaRepository<NotificationScheduleOutbox, Long> {

    /** 케이스 A: 락 없음. 단순 SELECT. */
    @Query(
            value = """
                    SELECT id
                    FROM notification_schedule_outbox
                    WHERE status = 'PENDING'
                    ORDER BY created_at ASC, id ASC
                    LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<Long> selectPendingIdsNoLock(@Param("limit") int limit);

    /** 케이스 B: FOR UPDATE 만. SKIP LOCKED 없음 → 다른 워커는 락 해제까지 대기. */
    @Query(
            value = """
                    SELECT id
                    FROM notification_schedule_outbox
                    WHERE status = 'PENDING'
                    ORDER BY created_at ASC, id ASC
                    LIMIT :limit
                    FOR UPDATE
                    """,
            nativeQuery = true
    )
    List<Long> selectPendingIdsForUpdate(@Param("limit") int limit);
}
