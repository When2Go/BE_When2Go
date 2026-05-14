package org.example.when2go.domain.trip.listener;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.example.when2go.domain.trip.event.TripRecalcScanRequestedEvent;
import org.example.when2go.domain.trip.service.recalc.TripRecalcClaimService;
import org.example.when2go.domain.trip.service.recalc.TripRecalcProcessor;
import org.junit.jupiter.api.Test;

class TripRecalcScanListenerTest {

    private final TripRecalcClaimService tripRecalcClaimService =
            org.mockito.Mockito.mock(TripRecalcClaimService.class);
    private final TripRecalcProcessor tripRecalcProcessor =
            org.mockito.Mockito.mock(TripRecalcProcessor.class);
    private final TripRecalcScanListener listener = new TripRecalcScanListener(
            tripRecalcClaimService,
            tripRecalcProcessor
    );

    // scan 이벤트를 받으면 claim된 trip을 각각 재계산 처리하는지 확인한다.
    @Test
    void handleClaimsTripsAndProcessesEachTrip() {
        when(tripRecalcProcessor.isAvailable()).thenReturn(true);
        when(tripRecalcClaimService.claim(200)).thenReturn(List.of(1L, 2L));

        listener.handle(new TripRecalcScanRequestedEvent(200));

        verify(tripRecalcProcessor).process(1L);
        verify(tripRecalcProcessor).process(2L);
    }

    // claim 결과가 없으면 재계산 처리를 호출하지 않는지 확인한다.
    @Test
    void handleDoesNothingWhenNoTripsClaimed() {
        when(tripRecalcProcessor.isAvailable()).thenReturn(true);
        when(tripRecalcClaimService.claim(200)).thenReturn(List.of());

        listener.handle(new TripRecalcScanRequestedEvent(200));

        verify(tripRecalcProcessor, never()).process(org.mockito.ArgumentMatchers.anyLong());
    }

    // ODsay client가 아직 구현되지 않은 상태에서는 trip을 claim하지 않는지 확인한다.
    @Test
    void handleDoesNotClaimWhenProcessorIsUnavailable() {
        when(tripRecalcProcessor.isAvailable()).thenReturn(false);

        listener.handle(new TripRecalcScanRequestedEvent(200));

        verify(tripRecalcClaimService, never()).claim(org.mockito.ArgumentMatchers.anyInt());
    }
}
