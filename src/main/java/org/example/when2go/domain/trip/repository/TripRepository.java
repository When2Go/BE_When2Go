package org.example.when2go.domain.trip.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.entity.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripRepository extends JpaRepository<Trip, Long> {

    @Query(
            value = """
                    SELECT id
                    FROM trips
                    WHERE recalc_phase <> 'DONE'
                      AND next_recalc_at IS NOT NULL
                      AND next_recalc_at <= :now
                    ORDER BY next_recalc_at ASC, id ASC
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    // NOW()는 DB 시간대(RDS 기본 UTC)를 따르므로, 저장할 때와 같은 앱 시계 기준 시각을 넘겨받는다.
    List<Long> claimDueRecalcTripIds(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Trip t set t.nextRecalcAt = :nextRecalcAt where t.id in :ids")
    int updateNextRecalcAt(
            @Param("ids") List<Long> ids,
            @Param("nextRecalcAt") LocalDateTime nextRecalcAt
    );

    @Query("SELECT t FROM Trip t WHERE t.user.id = :userId AND t.status = :status AND t.arrivalTime >= :startOfDay AND t.arrivalTime < :endOfDay ORDER BY t.arrivalTime ASC")
    List<Trip> findByUserIdAndStatusAndDate(
            @Param("userId") Long userId,
            @Param("status")TripStatus status,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    Optional<Trip> findByIdAndUserId(Long id, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Trip t set t.reservation = null where t.reservation.id = :reservationId")
    int detachReservation(@Param("reservationId") Long reservationId);

    boolean existsByReservationIdAndArrivalTimeBetween(
            Long reservationId,
            LocalDateTime start,
            LocalDateTime end
    );
}