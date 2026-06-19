package org.example.when2go.domain.trip.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.trip.dto.NavigationParseResponse;
import org.example.when2go.domain.trip.error.NavigationParseErrorCode;
import org.example.when2go.global.error.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
public class GeminiAudioParseClient {

    private final WebClient webClient;
    private final Clock clock;
    private final String model;

    // Gemini 응답에 modelVersion/usageMetadata 등 우리가 안 쓰는 필드가 많아서 모르는 필드는 무시.
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    public GeminiAudioParseClient(
            WebClient geminiWebClient,
            Clock clock,
            @Value("${gemini.model}") String model
    ) {
        this.webClient = geminiWebClient;
        this.clock = clock;
        this.model = model;
    }

    // 프롬프트에 현재 시각을 주입할 때 쓰는 형식. 예: 2026-06-19 14:00 (금)
    private static final DateTimeFormatter NOW_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm (E)", Locale.KOREAN);

    // 한국 사용자의 음성을 해석하므로 날짜 계산 기준은 항상 KST로 고정한다.
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final String PROMPT_TEMPLATE = """
            이 음성은 길찾기 앱에 입력된 사용자의 음성 명령입니다.
            현재 시각은 {{NOW}} 입니다. 이 시각을 기준으로 날짜를 계산하세요.
            음성에서 다음 3가지 값을 추출해서 JSON 형식으로만 출력하세요. 설명이나 코드블록 없이 JSON만.

            {
              "start_location": "출발지 (언급이 없으면 null)",
              "end_location": "도착지",
              "appointment_time": "yyyy-MM-dd HH:mm 형식의 날짜+시각 (24시간제)"
            }

            [추출 규칙]

            1) start_location (출발지)
               - 사용자가 출발지를 명시하지 않으면 null (앱이 GPS 현재 위치로 처리하거나 사용자가 직접 설정함)
               - "집에서", "회사에서" 같이 출발지가 명시되면 그대로 추출
               - 예: "지금 강남역으로 가야해" → null
               - 예: "집에서 강남역까지 가는 길" → "집"

            2) end_location (도착지)
               - "~까지", "~에", "~로", "~으로" 같은 조사 뒤에 오는 장소
               - 조사는 제거하고 장소명만 깔끔하게 추출
               - 예: "강남역까지" → "강남역"
               - 예: "스타벅스 역삼점에 가야해" → "스타벅스 역삼점"

            3) appointment_time (약속 시간)
               - "yyyy-MM-dd HH:mm" 형식 (24시간제). 반드시 현재 시각을 기준으로 날짜를 계산.
               - 날짜 계산: "오늘"이거나 날짜 언급이 없으면 현재 날짜, "내일" → 현재 날짜 +1일, "모레" → +2일
               - "이번 주 금요일", "다음 주 월요일" 같은 요일 표현은 현재 날짜 기준으로 해당 날짜를 계산
               - 시각 변환: "오후 2시" → 14:00, "오전 9시 30분" → 09:30, "정오" → 12:00, "자정" → 00:00
               - "2시까지" (오전/오후 명시 없음) → 일반적인 활동 시간 기준으로 판단해서 14:00으로 추정
               - 시간 정보가 전혀 없으면 null

            4) 핵심 원칙
               - "오늘 중요한 회의가 있어서", "친구를 만나야 해서" 같은 부가적인 맥락은 무시
               - 위 3가지 값만 추출
               - 음성이 명령어가 아니거나 정보가 부족하면 해당 필드를 null로

            [예시] (아래 예시는 현재 시각이 "2026-06-19 10:00 (금)" 라고 가정한 경우입니다)
            음성: "오늘 중요한 회의가 있어서 오후 2시까지 강남역에 가야해"
            출력: {"start_location": null, "end_location": "강남역", "appointment_time": "2026-06-19 14:00"}

            음성: "집에서 출발해서 9시 30분까지 회사 가야해"
            출력: {"start_location": "집", "end_location": "회사", "appointment_time": "2026-06-20 09:30"}

            음성: "홍대입구역까지 어떻게 가지"
            출력: {"start_location": null, "end_location": "홍대입구역", "appointment_time": null}""";

    public NavigationParseResponse parse(byte[] audioBytes, String mimeType) {
        String base64Audio = Base64.getEncoder().encodeToString(audioBytes);

        String prompt = PROMPT_TEMPLATE.replace("{{NOW}}", currentTimeText());

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(
                                Map.of("inline_data", Map.of(
                                        "mime_type", mimeType,
                                        "data", base64Audio
                                )),
                                Map.of("text", prompt)
                        )
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
            log.error("[Gemini] 호출 실패", e);
            throw new DomainException(NavigationParseErrorCode.PARSE_FAILED, e);
        }

        return toResponse(body);
    }

    // 한국 시각(KST) 기준 현재 시각 문자열. LLM의 "오늘/내일" 날짜 계산 기준이 된다.
    String currentTimeText() {
        return LocalDateTime.now(clock.withZone(KST)).format(NOW_FORMATTER);
    }

    // 테스트에서 직접 호출하기 위해 package-private. Gemini 응답 본문(JSON 문자열)을 파싱한다.
    NavigationParseResponse toResponse(String body) {
        if (body == null || body.isBlank()) {
            log.error("[Gemini] 응답 본문이 비어있음");
            throw new DomainException(NavigationParseErrorCode.PARSE_FAILED);
        }

        // Gemini 응답에서 candidates[0].content.parts[0].text(= 우리가 시킨 JSON 문자열)를 꺼낸다.
        String resultJson;
        try {
            GeminiResponse response = objectMapper.readValue(body, GeminiResponse.class);
            resultJson = extractText(response);
        } catch (RuntimeException e) {
            log.error("[Gemini] 응답 구조 파싱 실패. 본문: {}", body, e);
            throw new DomainException(NavigationParseErrorCode.PARSE_FAILED, e);
        }

        if (resultJson == null || resultJson.isBlank()) {
            log.error("[Gemini] 응답에 text가 없음. 본문: {}", body);
            throw new DomainException(NavigationParseErrorCode.PARSE_FAILED);
        }

        try {
            ParsedFields fields = objectMapper.readValue(resultJson, ParsedFields.class);
            return new NavigationParseResponse(
                    fields.startLocation(),
                    fields.endLocation(),
                    fields.appointmentTime()
            );
        } catch (RuntimeException e) {
            log.error("[Gemini] 결과 JSON 파싱 실패. text: {}", resultJson, e);
            throw new DomainException(NavigationParseErrorCode.PARSE_FAILED, e);
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

    // --- Gemini 응답 구조 (필요한 필드만) ---
    record GeminiResponse(List<Candidate> candidates) {
    }

    record Candidate(Content content) {
    }

    record Content(List<Part> parts) {
    }

    record Part(String text) {
    }

    // 모델이 text 안에 넣어주는 최종 결과 JSON
    record ParsedFields(
            @JsonProperty("start_location") String startLocation,
            @JsonProperty("end_location") String endLocation,
            @JsonProperty("appointment_time") String appointmentTime
    ) {
    }
}
