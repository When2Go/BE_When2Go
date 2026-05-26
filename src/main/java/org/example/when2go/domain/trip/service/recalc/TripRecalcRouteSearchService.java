package org.example.when2go.domain.trip.service.recalc;

import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.route.client.GoogleRouteClient;
import org.example.when2go.domain.route.dto.RouteSearchRequest;
import org.example.when2go.domain.route.dto.GoogleRouteSearchRequest;
import org.example.when2go.domain.route.dto.GoogleRouteSearchResponse;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TripRecalcRouteSearchService {

    private final ObjectProvider<GoogleRouteClient> googleRouteClientProvider;
    private final TripPhaseAdvanceService tripPhaseAdvanceService;
    private final TripRepository tripRepository;

    public boolean isAvailable() {
        return googleRouteClientProvider.getIfAvailable() != null;
    }

    // 재계산 포르세스
    public void process(Long tripId) {
        GoogleRouteClient googleRouteClient = googleRouteClientProvider.getIfAvailable();
        if (googleRouteClient == null) {
            throw new IllegalStateException("GoogleRouteClient bean is required to recalculate trips");
        }
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("Trip not found: " + tripId));

        GoogleRouteSearchRequest request = new GoogleRouteSearchRequest(RouteSearchRequest.from(trip));
        GoogleRouteSearchResponse result = googleRouteClient.search(request); // 실제 재계산 수행
        tripPhaseAdvanceService.advancePhase(tripId, result); // 재계산한 값에 맞게 DB 수정
    }
}
