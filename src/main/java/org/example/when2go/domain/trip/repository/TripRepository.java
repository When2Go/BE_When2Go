package org.example.when2go.domain.trip.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.example.when2go.domain.trip.entity.Trip;
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
                      AND next_recalc_at <= NOW()
                    ORDER BY next_recalc_at ASC, id ASC
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true
    )
    List<Long> claimDueRecalcTripIds(@Param("limit") int limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Trip t set t.nextRecalcAt = :nextRecalcAt where t.id in :ids")
    int updateNextRecalcAt(
            @Param("ids") List<Long> ids,
            @Param("nextRecalcAt") LocalDateTime nextRecalcAt
    );
}
