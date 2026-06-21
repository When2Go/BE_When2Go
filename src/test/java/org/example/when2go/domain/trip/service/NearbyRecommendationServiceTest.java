package org.example.when2go.domain.trip.service;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.example.when2go.domain.trip.client.GeminiNearbyRecommendClient;
import org.example.when2go.domain.trip.dto.NearbyRecommendation;
import org.junit.jupiter.api.Test;

class NearbyRecommendationServiceTest {

    private final GeminiNearbyRecommendClient client = mock(GeminiNearbyRecommendClient.class);
    private final NearbyRecommendationWriter writer = mock(NearbyRecommendationWriter.class);
    private final NearbyRecommendationService service =
            new NearbyRecommendationService(client, writer);

    @Test
    void Gemini_정상_응답을_JSON_문자열로_writer에_넘긴다() {
        when(client.recommend("강남역", 37.4979, 127.0276)).thenReturn(List.of(
                new NearbyRecommendation("○○카페", "분위기 좋은 카페", "카페"),
                new NearbyRecommendation("△△공원", "산책 좋은 공원", "공원")
        ));

        service.populate(1L, "강남역", 37.4979, 127.0276);

        verify(writer).save(eq(1L), contains("○○카페"));
    }

    @Test
    void Gemini가_빈_리스트를_반환하면_빈_배열로_writer에_넘긴다() {
        when(client.recommend(eq("강남역"), eq(37.4979), eq(127.0276))).thenReturn(List.of());

        service.populate(1L, "강남역", 37.4979, 127.0276);

        verify(writer).save(1L, "[]");
    }
}
