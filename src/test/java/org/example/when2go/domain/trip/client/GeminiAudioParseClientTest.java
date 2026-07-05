package org.example.when2go.domain.trip.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.example.when2go.domain.trip.dto.AudioParseRequest;
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

    private final List<AudioParseRequest> threeRequests = List.of(
            new AudioParseRequest(100L, new byte[]{1}, "audio/mp3"),
            new AudioParseRequest(200L, new byte[]{2}, "audio/mp3"),
            new AudioParseRequest(300L, new byte[]{3}, "audio/mp3")
    );

    @Test
    void 정상_배열_응답을_원래_id로_매핑한다() {
        String body = wrap("""
                [
                  {"id":0, "start_location":null, "end_location":"강남역", "appointment_time":"2026-06-19 14:00"},
                  {"id":1, "start_location":"집", "end_location":"회사", "appointment_time":"2026-06-20 09:30"},
                  {"id":2, "start_location":null, "end_location":"홍대입구역", "appointment_time":null}
                ]""");

        Map<Long, NavigationParseResponse> result = client.toResponseMap(body, threeRequests);

        assertThat(result).hasSize(3);
        assertThat(result.get(100L).endLocation()).isEqualTo("강남역");
        assertThat(result.get(200L).startLocation()).isEqualTo("집");
        assertThat(result.get(300L).appointmentTime()).isNull();
    }

    @Test
    void 문자열_null도_null로_정규화한다() {
        // flash-lite 모델이 가끔 "null" 문자열로 주는 경우.
        String body = wrap("""
                [
                  {"id":0, "start_location":"null", "end_location":"강남역", "appointment_time":"null"}
                ]""");

        Map<Long, NavigationParseResponse> result = client.toResponseMap(
                body, List.of(new AudioParseRequest(100L, new byte[]{1}, "audio/mp3")));

        assertThat(result.get(100L).startLocation()).isNull();
        assertThat(result.get(100L).appointmentTime()).isNull();
        assertThat(result.get(100L).endLocation()).isEqualTo("강남역");
    }

    @Test
    void 응답에서_일부_id가_누락되면_누락된_요청은_Map에_없다() {
        String body = wrap("""
                [
                  {"id":0, "start_location":null, "end_location":"강남역", "appointment_time":"2026-06-19 14:00"},
                  {"id":2, "start_location":null, "end_location":"홍대입구역", "appointment_time":null}
                ]""");

        Map<Long, NavigationParseResponse> result = client.toResponseMap(body, threeRequests);

        assertThat(result).hasSize(2);
        assertThat(result).containsOnlyKeys(100L, 300L);
        assertThat(result).doesNotContainKey(200L);
    }

    @Test
    void 응답_id가_요청_범위를_벗어나면_무시한다() {
        String body = wrap("""
                [
                  {"id":0, "start_location":null, "end_location":"강남역", "appointment_time":null},
                  {"id":99, "start_location":null, "end_location":"이상한역", "appointment_time":null}
                ]""");

        Map<Long, NavigationParseResponse> result = client.toResponseMap(body, threeRequests);

        assertThat(result).hasSize(1);
        assertThat(result).containsOnlyKeys(100L);
    }

    @Test
    void candidates가_비어있으면_예외() {
        String body = "{\"candidates\": []}";

        assertThatThrownBy(() -> client.toResponseMap(body, threeRequests))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void text가_JSON이_아니면_예외() {
        String body = wrap("죄송해요, 무슨 말인지 못 알아들었어요");

        assertThatThrownBy(() -> client.toResponseMap(body, threeRequests))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void 응답_본문이_비어있으면_예외() {
        assertThatThrownBy(() -> client.toResponseMap("", threeRequests))
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
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
