package org.example.when2go.domain.trip.service.recalc;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.example.when2go.global.config.trip.TripRecalcProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripRecalcClaimService {

    private final TripRepository tripRepository;
    private final TripRecalcProperties tripRecalcProperties;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<Trip> claim(int limit) {
        List<Trip> trips = tripRepository.claimDueRecalcTrips(limit);
        if (trips.isEmpty()) {
            return List.of();
        }

        LocalDateTime holdUntil = LocalDateTime.now(clock)
                .plus(tripRecalcProperties.getClaimHoldDuration());
        trips.forEach(trip -> trip.holdRecalcUntil(holdUntil));
        return trips;
    }
}
