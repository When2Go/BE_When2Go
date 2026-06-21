package org.example.when2go.domain.trip.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.trip.event.TripCreatedEvent;
import org.example.when2go.domain.trip.service.NearbyRecommendationService;
import org.example.when2go.global.config.trip.TripAsyncConfig;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NearbyRecommendationEventListener {

    private final NearbyRecommendationService nearbyRecommendationService;

    @Async(TripAsyncConfig.NEARBY_RECOMMEND_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(TripCreatedEvent event) {
        try {
            nearbyRecommendationService.populate(
                    event.tripId(),
                    event.destName(),
                    event.destLat(),
                    event.destLng()
            );
        } catch (RuntimeException e) {
            log.warn("event=trip.nearby_recommendation_failed tripId={}", event.tripId(), e);
        }
    }
}
