package org.example.when2go.domain.trip.service.recalc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.example.when2go.domain.route.client.GoogleRouteClient;
import org.example.when2go.domain.route.dto.RouteSearchRequest;
import org.example.when2go.domain.route.dto.RouteSearchResult;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class TripRecalcProcessorTest {

    private final GoogleRouteClient googleRouteClient = org.mockito.Mockito.mock(GoogleRouteClient.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<GoogleRouteClient> googleRouteClientProvider =
            org.mockito.Mockito.mock(ObjectProvider.class);
    private final TripRecalcFinalizer tripRecalcFinalizer =
            org.mockito.Mockito.mock(TripRecalcFinalizer.class);
    private final TripRepository tripRepository =
            org.mockito.Mockito.mock(TripRepository.class);
    private final TripRecalcProcessor processor = new TripRecalcProcessor(
            googleRouteClientProvider,
            tripRecalcFinalizer,
            tripRepository
    );

    @Test
    void processSearchesRouteAndDelegatesFinalize() {
        Trip trip = org.mockito.Mockito.mock(Trip.class);
        LocalDateTime arrivalTime = LocalDateTime.of(2026, 5, 15, 10, 0);
        when(trip.getOriginLat()).thenReturn(37.5);
        when(trip.getOriginLng()).thenReturn(127.0);
        when(trip.getDestLat()).thenReturn(37.6);
        when(trip.getDestLng()).thenReturn(127.1);
        when(trip.getArrivalTime()).thenReturn(arrivalTime);
        when(trip.getRouteOption()).thenReturn(RouteOption.TRANSIT);

        RouteSearchResult result = new RouteSearchResult(List.of(new RouteSearchResult.Route("2400s")));
        when(googleRouteClientProvider.getIfAvailable()).thenReturn(googleRouteClient);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
        when(googleRouteClient.search(any(RouteSearchRequest.class))).thenReturn(result);

        processor.process(1L);

        ArgumentCaptor<RouteSearchRequest> captor = ArgumentCaptor.forClass(RouteSearchRequest.class);
        verify(googleRouteClient).search(captor.capture());
        RouteSearchRequest captured = captor.getValue();
        assertThat(captured.origin().location().latLng().latitude()).isEqualTo(37.5);
        assertThat(captured.origin().location().latLng().longitude()).isEqualTo(127.0);
        assertThat(captured.destination().location().latLng().latitude()).isEqualTo(37.6);
        assertThat(captured.destination().location().latLng().longitude()).isEqualTo(127.1);
        assertThat(captured.travelMode()).isEqualTo("TRANSIT");
        assertThat(captured.routingPreference()).isNull();

        verify(tripRecalcFinalizer).finalizeRecalc(1L, result);
    }
}
