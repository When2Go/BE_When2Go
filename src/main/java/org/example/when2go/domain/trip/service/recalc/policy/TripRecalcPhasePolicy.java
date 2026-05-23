package org.example.when2go.domain.trip.service.recalc.policy;

import java.time.LocalDateTime;
import java.util.Objects;
import org.example.when2go.domain.trip.entity.TripRecalcPhase;
import org.springframework.stereotype.Component;

@Component
public class TripRecalcPhasePolicy {

    public TripRecalcPhase decideNextPhase(
            TripRecalcPhase currentPhase,
            LocalDateTime departureTime,
            LocalDateTime now
    ) {
        Objects.requireNonNull(currentPhase, "currentPhase must not be null");
        Objects.requireNonNull(departureTime, "departureTime must not be null");
        Objects.requireNonNull(now, "now must not be null");

        return switch (currentPhase) {
            case INITIAL -> decideFromInitial(departureTime, now);
            case PHASE_1 -> decideFromPhase1(departureTime, now);
            case PHASE_2 -> TripRecalcPhase.PHASE_3;
            case PHASE_3, DONE -> TripRecalcPhase.DONE;
        };
    }

    public LocalDateTime computeNextRecalcAt(
            TripRecalcPhase nextPhase,
            LocalDateTime departureTime,
            LocalDateTime now
    ) {
        Objects.requireNonNull(nextPhase, "nextPhase must not be null");
        Objects.requireNonNull(departureTime, "departureTime must not be null");
        Objects.requireNonNull(now, "now must not be null");

        LocalDateTime nextRecalcAt = switch (nextPhase) {
            case PHASE_1 -> departureTime.minusMinutes(60);
            case PHASE_2 -> departureTime.minusMinutes(30);
            case PHASE_3 -> departureTime.minusMinutes(20);
            case INITIAL, DONE -> throw new IllegalArgumentException(
                    "nextPhase must be PHASE_1, PHASE_2, or PHASE_3"
            );
        };
        return nextRecalcAt.isBefore(now) ? now : nextRecalcAt;
    }

    private TripRecalcPhase decideFromInitial(LocalDateTime departureTime, LocalDateTime now) {
        if (!departureTime.isBefore(now.plusMinutes(60))) {
            return TripRecalcPhase.PHASE_1;
        }
        if (!departureTime.isBefore(now.plusMinutes(30))) {
            return TripRecalcPhase.PHASE_2;
        }
        return TripRecalcPhase.PHASE_3;
    }

    private TripRecalcPhase decideFromPhase1(LocalDateTime departureTime, LocalDateTime now) {
        if (!departureTime.isBefore(now.plusMinutes(30))) {
            return TripRecalcPhase.PHASE_2;
        }
        return TripRecalcPhase.PHASE_3;
    }
}
