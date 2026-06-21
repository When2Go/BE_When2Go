package org.example.when2go.domain.trip.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class NearbyRecommendationWriter {

    private final TripRepository tripRepository;

    @Transactional
    public void save(Long tripId, String json) {
        Trip trip = tripRepository.findById(tripId).orElse(null);
        if (trip == null) {
            log.warn("[Nearby] save skipped — trip not found. tripId={}", tripId);
            return;
        }
        trip.updateNearbyRecommendations(json);
    }
}
