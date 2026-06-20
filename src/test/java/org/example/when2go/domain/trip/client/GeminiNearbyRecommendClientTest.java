package org.example.when2go.domain.trip.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.example.when2go.domain.trip.dto.NearbyRecommendation;
import org.junit.jupiter.api.Test;

class GeminiNearbyRecommendClientTest {

    private final GeminiNearbyRecommendClient client = new GeminiNearbyRecommendClient(
            null,
            "test-model"
    );

    @Test
    void 정상_JSON을_3개의_추천으로_파싱한다() {
        String body = wrap("["
                + "{\"name\":\"○○카페\",\"description\":\"분위기 좋은 디저트 카페\",\"category\":\"카페\"},"
                + "{\"name\":\"△△공원\",\"description\":\"산책하기 좋은 공원\",\"category\":\"공원\"},"
                + "{\"name\":\"□□서점\",\"description\":\"독립서적 큐레이션 서점\",\"category\":\"서점\"}"
                + "]");

        List<NearbyRecommendation> result = client.toResponse(body);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).name()).isEqualTo("○○카페");
        assertThat(result.get(0).category()).isEqualTo("카페");
        assertThat(result.get(2).description()).isEqualTo("독립서적 큐레이션 서점");
    }

    @Test
    void 추천이_3개_초과면_앞_3개만_반환한다() {
        String body = wrap("["
                + "{\"name\":\"A\",\"description\":\"a\",\"category\":\"카페\"},"
                + "{\"name\":\"B\",\"description\":\"b\",\"category\":\"카페\"},"
                + "{\"name\":\"C\",\"description\":\"c\",\"category\":\"카페\"},"
                + "{\"name\":\"D\",\"description\":\"d\",\"category\":\"카페\"}"
                + "]");

        List<NearbyRecommendation> result = client.toResponse(body);

        assertThat(result).extracting(NearbyRecommendation::name)
                .containsExactly("A", "B", "C");
    }

    @Test
    void 추천이_3개_미만이면_그대로_반환한다() {
        String body = wrap("[{\"name\":\"A\",\"description\":\"a\",\"category\":\"카페\"}]");

        List<NearbyRecommendation> result = client.toResponse(body);

        assertThat(result).hasSize(1);
    }

    @Test
    void candidates가_비어있으면_빈_리스트() {
        String body = "{\"candidates\": []}";

        assertThat(client.toResponse(body)).isEmpty();
    }

    @Test
    void text가_JSON_배열이_아니면_빈_리스트() {
        String body = wrap("죄송해요");

        assertThat(client.toResponse(body)).isEmpty();
    }

    @Test
    void 본문이_null이면_빈_리스트() {
        assertThat(client.toResponse(null)).isEmpty();
    }

    @Test
    void 본문이_빈_문자열이면_빈_리스트() {
        assertThat(client.toResponse("")).isEmpty();
    }

    // Gemini 응답 봉투(candidates[0].content.parts[0].text) 모양으로 감싼다.
    private String wrap(String text) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + quote(text) + "}]}}],\"modelVersion\":\"x\"}";
    }

    private String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
