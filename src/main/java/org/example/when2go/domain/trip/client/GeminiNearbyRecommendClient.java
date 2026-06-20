package org.example.when2go.domain.trip.client;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.trip.dto.NearbyRecommendation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
public class GeminiNearbyRecommendClient {

    private static final int MAX_RECOMMENDATIONS = 3;

    private final WebClient webClient;
    private final String model;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    public GeminiNearbyRecommendClient(
            WebClient geminiWebClient,
            @Value("${gemini.model}") String model
    ) {
        this.webClient = geminiWebClient;
        this.model = model;
    }

    private static final String PROMPT_TEMPLATE = """
            당신은 한국 장소 추천 도우미입니다.
            목적지: {{DEST_NAME}} (위도 {{LAT}}, 경도 {{LNG}})

            이 목적지 근처(도보 10분 이내)에서 약속 전 잠시 시간을 보내기 좋은,
            실제로 존재할 가능성이 높은 장소 3개를 추천하세요.
            카페/공원/서점/전시공간 등 가볍게 머물 수 있는 곳 위주로 골라주세요.

            JSON 배열만 출력하세요 (코드블록, 설명 없이):
            [
              {"name": "장소명", "description": "한 줄 설명", "category": "카페|공원|서점|전시|기타"}
            ]""";

    public List<NearbyRecommendation> recommend(String destName, Double destLat, Double destLng) {
        String prompt = PROMPT_TEMPLATE
                .replace("{{DEST_NAME}}", destName)
                .replace("{{LAT}}", String.valueOf(destLat))
                .replace("{{LNG}}", String.valueOf(destLng));

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "response_mime_type", "application/json"
                )
        );

        String body;
        try {
            body = webClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (RuntimeException e) {
            log.error("[Gemini-Nearby] 호출 실패. dest={}", destName, e);
            return Collections.emptyList();
        }

        return toResponse(body);
    }

    // Gemini 응답 본문을 추천 리스트로 파싱. 어떤 실패에도 예외를 던지지 않고 빈 리스트 반환.
    List<NearbyRecommendation> toResponse(String body) {
        if (body == null || body.isBlank()) {
            return Collections.emptyList();
        }

        String resultJson;
        try {
            GeminiResponse response = objectMapper.readValue(body, GeminiResponse.class);
            resultJson = extractText(response);
        } catch (RuntimeException e) {
            log.error("[Gemini-Nearby] 응답 구조 파싱 실패. 본문: {}", body, e);
            return Collections.emptyList();
        }

        if (resultJson == null || resultJson.isBlank()) {
            return Collections.emptyList();
        }

        try {
            List<NearbyRecommendation> parsed = objectMapper.readValue(
                    resultJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, NearbyRecommendation.class)
            );
            if (parsed.size() > MAX_RECOMMENDATIONS) {
                return List.copyOf(parsed.subList(0, MAX_RECOMMENDATIONS));
            }
            return parsed;
        } catch (RuntimeException e) {
            log.error("[Gemini-Nearby] 결과 JSON 파싱 실패. text: {}", resultJson, e);
            return Collections.emptyList();
        }
    }

    private String extractText(GeminiResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            return null;
        }
        Candidate candidate = response.candidates().get(0);
        if (candidate == null || candidate.content() == null) {
            return null;
        }
        List<Part> parts = candidate.content().parts();
        if (parts == null || parts.isEmpty()) {
            return null;
        }
        return parts.get(0).text();
    }

    record GeminiResponse(List<Candidate> candidates) {
    }

    record Candidate(Content content) {
    }

    record Content(List<Part> parts) {
    }

    record Part(String text) {
    }
}
