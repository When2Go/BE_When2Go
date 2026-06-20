package org.example.when2go.domain.trip.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NearbyRecommendationTest {

    @Test
    void 필드를_그대로_보관한다() {
        NearbyRecommendation rec = new NearbyRecommendation("○○카페", "분위기 좋은 디저트 카페", "카페");

        assertThat(rec.name()).isEqualTo("○○카페");
        assertThat(rec.description()).isEqualTo("분위기 좋은 디저트 카페");
        assertThat(rec.category()).isEqualTo("카페");
    }
}
