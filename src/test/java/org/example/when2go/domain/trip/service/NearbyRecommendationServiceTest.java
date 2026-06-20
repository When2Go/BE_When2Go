package org.example.when2go.domain.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.trip.client.GeminiNearbyRecommendClient;
import org.example.when2go.domain.trip.dto.NearbyRecommendation;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.entity.Platform;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NearbyRecommendationServiceTest {

    private final GeminiNearbyRecommendClient client = mock(GeminiNearbyRecommendClient.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final NearbyRecommendationService service =
            new NearbyRecommendationService(client, tripRepository);

    @Test
    void Gemini_정상_응답을_JSON_문자열로_Trip에_저장한다() {
        Trip trip = sampleTrip(1L);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
        when(client.recommend("강남역", 37.4979, 127.0276)).thenReturn(List.of(
                new NearbyRecommendation("○○카페", "분위기 좋은 카페", "카페"),
                new NearbyRecommendation("△△공원", "산책 좋은 공원", "공원")
        ));

        service.populate(1L, "강남역", 37.4979, 127.0276);

        assertThat(trip.getNearbyRecommendations())
                .contains("\"○○카페\"")
                .contains("\"△△공원\"")
                .contains("\"카페\"")
                .contains("\"공원\"");
    }

    @Test
    void Gemini가_빈_리스트를_반환하면_빈_배열로_저장한다() {
        Trip trip = sampleTrip(1L);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
        when(client.recommend(eq("강남역"), eq(37.4979), eq(127.0276))).thenReturn(List.of());

        service.populate(1L, "강남역", 37.4979, 127.0276);

        assertThat(trip.getNearbyRecommendations()).isEqualTo("[]");
    }

    @Test
    void Trip이_없으면_조용히_종료한다() {
        when(tripRepository.findById(999L)).thenReturn(Optional.empty());

        service.populate(999L, "강남역", 37.4979, 127.0276);

        verify(client, never()).recommend(eq("강남역"), eq(37.4979), eq(127.0276));
    }

    private Trip sampleTrip(Long id) {
        AppUser user = AppUser.builder()
                .deviceId("device-abc")
                .platform(Platform.IOS)
                .fcmToken("token")
                .build();
        Trip trip = Trip.builder()
                .user(user)
                .originName("선릉역")
                .destName("강남역")
                .originLat(37.5045)
                .originLng(127.0498)
                .destLat(37.4979)
                .destLng(127.0276)
                .arrivalTime(LocalDateTime.of(2026, 6, 20, 18, 0))
                .routeOption(RouteOption.TRANSIT)
                .bufferMinutes(10)
                .nextRecalcAt(LocalDateTime.of(2026, 6, 20, 17, 0))
                .build();
        ReflectionTestUtils.setField(trip, "id", id);
        return trip;
    }
}
