package org.example.when2go.domain.trip.service.recalc;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
    public List<Long> claim(int limit) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> tripIds = tripRepository.claimDueRecalcTripIds(now, limit);
        if (tripIds.isEmpty()) {
            return List.of();
        }

        // FOR UPDATE 락은 트랜잭션 종료 시 풀리므로, next_recalc_at을 미래로 밀어 soft lock 유지
        LocalDateTime holdUntil = now.plus(tripRecalcProperties.getClaimHoldDuration());
        tripRepository.updateNextRecalcAt(tripIds, holdUntil);
        return tripIds;
    }
}
