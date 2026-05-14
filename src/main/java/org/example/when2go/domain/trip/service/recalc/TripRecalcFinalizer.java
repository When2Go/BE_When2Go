package org.example.when2go.domain.trip.service.recalc;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.notification.service.NotificationScheduleService;
import org.example.when2go.domain.route.dto.RouteSearchResult;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.enums.TripRecalcPhase;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripRecalcFinalizer {

    private final TripRepository tripRepository;
    private final TripRecalcPhasePolicy phasePolicy;
    private final NotificationScheduleService notificationScheduleService;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizeRecalc(Long tripId, RouteSearchResult result) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found: " + tripId));
        if (trip.getRecalcPhase() == TripRecalcPhase.DONE) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime departureTime = trip.getArrivalTime()
                .minusMinutes(result.totalMinutes())
                .minusMinutes(trip.getBufferMinutes());
        TripRecalcPhase nextPhase = phasePolicy.decideNextPhase(
                trip.getRecalcPhase(),
                departureTime,
                now
        );

        if (nextPhase == TripRecalcPhase.DONE) {
            trip.markFinalized(departureTime);
            notificationScheduleService.createDepartureSchedules(trip);
            return;
        }

        trip.applyRecalcResult(
                nextPhase,
                phasePolicy.computeNextRecalcAt(nextPhase, departureTime, now)
        );
    }
}
