package org.example.when2go.domain.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import org.example.when2go.domain.route.enums.RouteOption;
import org.example.when2go.domain.trip.entity.Trip;
import org.example.when2go.domain.trip.repository.TripRepository;
import org.example.when2go.domain.user.entity.AppUser;
import org.example.when2go.domain.user.entity.Platform;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NearbyRecommendationWriterTest {

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final NearbyRecommendationWriter writer = new NearbyRecommendationWriter(tripRepository);

    @Test
    void Trip이_존재하면_JSON을_저장한다() {
        Trip trip = sampleTrip(1L);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        writer.save(1L, "[{\"name\":\"카페\"}]");

        assertThat(trip.getNearbyRecommendations()).isEqualTo("[{\"name\":\"카페\"}]");
    }

    @Test
    void Trip이_없으면_조용히_종료한다() {
        when(tripRepository.findById(999L)).thenReturn(Optional.empty());

        writer.save(999L, "[]");
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
