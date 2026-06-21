package org.example.when2go.domain.trip.listener;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.example.when2go.domain.trip.event.TripCreatedEvent;
import org.example.when2go.domain.trip.service.NearbyRecommendationService;
import org.junit.jupiter.api.Test;

class NearbyRecommendationEventListenerTest {

    private final NearbyRecommendationService nearbyRecommendationService =
            mock(NearbyRecommendationService.class);
    private final NearbyRecommendationEventListener listener =
            new NearbyRecommendationEventListener(nearbyRecommendationService);

    @Test
    void TripCreatedEvent를_받으면_populate를_호출한다() {
        TripCreatedEvent event = new TripCreatedEvent(42L, "강남역", 37.4979, 127.0276);

        listener.handle(event);

        verify(nearbyRecommendationService).populate(42L, "강남역", 37.4979, 127.0276);
    }

    @Test
    void populate가_실패해도_예외를_삼킨다() {
        TripCreatedEvent event = new TripCreatedEvent(42L, "강남역", 37.4979, 127.0276);
        doThrow(new RuntimeException("boom"))
                .when(nearbyRecommendationService).populate(42L, "강남역", 37.4979, 127.0276);

        listener.handle(event);

        verify(nearbyRecommendationService).populate(42L, "강남역", 37.4979, 127.0276);
    }
}
