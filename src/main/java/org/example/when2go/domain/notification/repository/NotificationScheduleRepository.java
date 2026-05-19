package org.example.when2go.domain.notification.repository;

import java.util.Collection;
import java.util.List;
import org.example.when2go.domain.notification.entity.NotificationSchedule;
import org.example.when2go.domain.notification.enums.NotificationScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationScheduleRepository extends JpaRepository<NotificationSchedule, Long> {

    @Query(
            value = """
                    SELECT id
                    FROM notification_schedules
                    WHERE status = 'PENDING'
                      AND scheduled_at <= NOW()
                    ORDER BY scheduled_at ASC, id ASC
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<Long> claimDueScheduleIds(@Param("limit") int limit);

    @Query("""
            SELECT s
            FROM NotificationSchedule s
            JOIN FETCH s.user
            JOIN FETCH s.trip
            WHERE s.id IN :ids
            """)
    List<NotificationSchedule> findAllByIdInWithUserAndTrip(@Param("ids") Collection<Long> ids);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE NotificationSchedule s
            SET s.status = :status
            WHERE s.id IN :ids
            """)
    int updateStatus(
            @Param("ids") Collection<Long> ids,
            @Param("status") NotificationScheduleStatus status
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM NotificationSchedule s
            WHERE s.trip.id = :tripId
              AND NOT EXISTS (
                  SELECT 1
                  FROM NotificationScheduleOutbox o
                  WHERE o.schedule = s
              )
            """)
    int deleteByTripIdWithoutOutboxes(@Param("tripId") Long tripId);
}
