package org.example.when2go.domain.trip.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.trip.client.GeminiNearbyRecommendClient;
import org.example.when2go.domain.trip.dto.NearbyRecommendation;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class NearbyRecommendationService {

    private static final String EMPTY_JSON_ARRAY = "[]";

    private final GeminiNearbyRecommendClient client;
    private final NearbyRecommendationWriter writer;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    // 외부 API 호출은 트랜잭션 밖에서 수행하고, DB 업데이트만 별도 컴포넌트의 짧은 트랜잭션으로 위임한다.
    public void populate(Long tripId, String destName, Double destLat, Double destLng) {
        List<NearbyRecommendation> recommendations = client.recommend(destName, destLat, destLng);

        String json;
        try {
            json = objectMapper.writeValueAsString(recommendations);
        } catch (RuntimeException e) {
            log.error("[Nearby] 직렬화 실패. tripId={}", tripId, e);
            json = EMPTY_JSON_ARRAY;
        }

        writer.save(tripId, json);
    }
}
