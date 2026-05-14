package org.example.when2go.domain.trip.repository;

import java.util.List;
import org.example.when2go.domain.trip.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripRepository extends JpaRepository<Trip, Long> {

    @Query(
            value = """
                    SELECT *
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
    List<Trip> claimDueRecalcTrips(@Param("limit") int limit);
}
