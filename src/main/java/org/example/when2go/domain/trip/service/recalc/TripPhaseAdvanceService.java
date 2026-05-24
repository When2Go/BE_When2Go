package org.example.when2go.domain.trip.service.recalc;

import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.notification.service.schedule.NotificationScheduleCreateService;
import org.example.when2go.domain.route.dto.RouteSearchResponse;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.entity.TripRecalcPhase;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.example.when2go.domain.trip.service.recalc.policy.TripRecalcPhasePolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripPhaseAdvanceService {

    private final TripRepository tripRepository;
    private final TripRecalcPhasePolicy phasePolicy;
    private final NotificationScheduleCreateService notificationScheduleCreateService;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void advancePhase(Long tripId, RouteSearchResponse result) {
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
            notificationScheduleCreateService.createDepartureSchedules(trip);
            return;
        }

        trip.applyRecalcResult(
                nextPhase,
                phasePolicy.computeNextRecalcAt(nextPhase, departureTime, now)
        );
    }
}
