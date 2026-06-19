package org.example.when2go.domain.trip.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.example.when2go.domain.trip.dto.NavigationParseResponse;
import org.example.when2go.global.error.DomainException;
import org.junit.jupiter.api.Test;

class GeminiAudioParseClientTest {

    // 2026-06-19T01:00:00Z == KST 2026-06-19 10:00 (금)
    private final GeminiAudioParseClient client = new GeminiAudioParseClient(
            null,
            Clock.fixed(Instant.parse("2026-06-19T01:00:00Z"), ZoneOffset.UTC),
            "test-model"
    );

    @Test
    void 정상_응답을_필드로_파싱한다() {
        String body = wrap("{\"start_location\": null, \"end_location\": \"강남역\", \"appointment_time\": \"2026-06-20 14:00\"}");

        NavigationParseResponse result = client.toResponse(body);

        assertThat(result.startLocation()).isNull();
        assertThat(result.endLocation()).isEqualTo("강남역");
        assertThat(result.appointmentTime()).isEqualTo("2026-06-20 14:00");
    }

    @Test
    void candidates가_비어있으면_예외() {
        String body = "{\"candidates\": []}";

        assertThatThrownBy(() -> client.toResponse(body))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void text가_JSON이_아니면_예외() {
        String body = wrap("죄송해요, 무슨 말인지 못 알아들었어요");

        assertThatThrownBy(() -> client.toResponse(body))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void 현재_시각은_KST_기준으로_생성된다() {
        assertThat(client.currentTimeText()).isEqualTo("2026-06-19 10:00 (금)");
    }

    // Gemini 응답 봉투(candidates[0].content.parts[0].text) 모양으로 감싼다.
    private String wrap(String text) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + quote(text) + "}]}}],\"modelVersion\":\"x\"}";
    }

    private String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
