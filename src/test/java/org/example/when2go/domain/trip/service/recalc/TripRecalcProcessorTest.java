package org.example.when2go.domain.trip.service.recalc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.example.when2go.domain.route.client.OdsayRouteClient;
import org.example.when2go.domain.route.client.RouteSearchResult;
import org.example.when2go.domain.trip.entity.Trip;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class TripRecalcProcessorTest {

    private final OdsayRouteClient odsayRouteClient = org.mockito.Mockito.mock(OdsayRouteClient.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<OdsayRouteClient> odsayRouteClientProvider =
            org.mockito.Mockito.mock(ObjectProvider.class);
    private final TripRecalcFinalizer tripRecalcFinalizer =
            org.mockito.Mockito.mock(TripRecalcFinalizer.class);
    private final TripRecalcProcessor processor = new TripRecalcProcessor(
            odsayRouteClientProvider,
            tripRecalcFinalizer
    );

    // ODsay 조회 결과를 trip 확정 단계로 전달하는지 확인한다.
    @Test
    void processSearchesRouteAndDelegatesFinalize() {
        Trip trip = org.mockito.Mockito.mock(Trip.class);
        RouteSearchResult result = new RouteSearchResult(40);
        when(trip.getId()).thenReturn(1L);
        when(odsayRouteClientProvider.getIfAvailable()).thenReturn(odsayRouteClient);
        when(odsayRouteClient.search(trip)).thenReturn(result);

        processor.process(trip);

        verify(odsayRouteClient).search(trip);
        verify(tripRecalcFinalizer).finalizeRecalc(1L, result);
    }
}
