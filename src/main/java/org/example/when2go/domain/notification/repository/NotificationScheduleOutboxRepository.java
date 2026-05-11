package org.example.when2go.domain.notification.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.example.when2go.domain.notification.entity.NotificationScheduleOutbox;
import org.example.when2go.domain.notification.enums.NotificationOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationScheduleOutboxRepository extends JpaRepository<NotificationScheduleOutbox, Long> {

    @Query("""
            SELECT o.dedupKey
            FROM NotificationScheduleOutbox o
            WHERE o.dedupKey IN :dedupKeys
            """)
    List<String> findDedupKeysIn(@Param("dedupKeys") Collection<String> dedupKeys);

    @Query(
            value = """
                    SELECT id
                    FROM notification_schedule_outbox
                    WHERE status = 'PENDING'
                    ORDER BY created_at ASC, id ASC
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<Long> claimPendingOutboxIds(@Param("limit") int limit);

    @Query("""
            SELECT o
            FROM NotificationScheduleOutbox o
            JOIN FETCH o.schedule
            JOIN FETCH o.user
            JOIN FETCH o.trip
            WHERE o.id IN :ids
            """)
    List<NotificationScheduleOutbox> findAllByIdInWithRelations(@Param("ids") Collection<Long> ids);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE NotificationScheduleOutbox o
            SET o.status = :status,
                o.processingStartedAt = :processingStartedAt
            WHERE o.id IN :ids
            """)
    int updateProcessing(
            @Param("ids") Collection<Long> ids,
            @Param("status") NotificationOutboxStatus status,
            @Param("processingStartedAt") LocalDateTime processingStartedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE NotificationScheduleOutbox o
            SET o.status = :status,
                o.processingStartedAt = NULL,
                o.publishedAt = :publishedAt
            WHERE o.id IN :ids
            """)
    int markPublished(
            @Param("ids") Collection<Long> ids,
            @Param("status") NotificationOutboxStatus status,
            @Param("publishedAt") LocalDateTime publishedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE NotificationScheduleOutbox o
            SET o.status = :status,
                o.retryCount = o.retryCount + 1,
                o.processingStartedAt = NULL
            WHERE o.id IN :ids
            """)
    int markRetryableFailure(
            @Param("ids") Collection<Long> ids,
            @Param("status") NotificationOutboxStatus status
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE NotificationScheduleOutbox o
            SET o.status = :status,
                o.processingStartedAt = NULL
            WHERE o.id IN :ids
            """)
    int markFailed(
            @Param("ids") Collection<Long> ids,
            @Param("status") NotificationOutboxStatus status
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE NotificationScheduleOutbox o
            SET o.status = :targetStatus,
                o.processingStartedAt = NULL
            WHERE o.status = :sourceStatus
              AND o.processingStartedAt < :threshold
            """)
    int resetStuckProcessing(
            @Param("sourceStatus") NotificationOutboxStatus sourceStatus,
            @Param("targetStatus") NotificationOutboxStatus targetStatus,
            @Param("threshold") LocalDateTime threshold
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM NotificationScheduleOutbox o
            WHERE o.trip.id = :tripId
              AND o.status IN :statuses
            """)
    int deleteByTripIdAndStatusIn(
            @Param("tripId") Long tripId,
            @Param("statuses") Collection<NotificationOutboxStatus> statuses
    );
}
