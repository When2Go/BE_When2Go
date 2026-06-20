package org.example.when2go.domain.trip.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.trip.client.GeminiNearbyRecommendClient;
import org.example.when2go.domain.trip.dto.NearbyRecommendation;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.example.when2go.global.config.trip.TripAsyncConfig;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class NearbyRecommendationService {

    private static final String EMPTY_JSON_ARRAY = "[]";

    private final GeminiNearbyRecommendClient client;
    private final TripRepository tripRepository;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Async(TripAsyncConfig.NEARBY_RECOMMEND_EXECUTOR)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void populate(Long tripId, String destName, Double destLat, Double destLng) {
        Trip trip = tripRepository.findById(tripId).orElse(null);
        if (trip == null) {
            log.warn("[Nearby] populate skipped — trip not found. tripId={}", tripId);
            return;
        }

        List<NearbyRecommendation> recommendations = client.recommend(destName, destLat, destLng);

        String json;
        try {
            json = objectMapper.writeValueAsString(recommendations);
        } catch (RuntimeException e) {
            log.error("[Nearby] 직렬화 실패. tripId={}", tripId, e);
            json = EMPTY_JSON_ARRAY;
        }

        trip.updateNearbyRecommendations(json);
    }
}
