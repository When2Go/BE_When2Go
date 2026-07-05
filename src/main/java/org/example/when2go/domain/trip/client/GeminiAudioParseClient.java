package org.example.when2go.domain.trip.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.example.when2go.domain.trip.dto.AudioParseRequest;
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

    // flash-lite 모델이 가끔 "null" 문자열로 주는 경우를 정규화한다.
    private static final String NULL_LITERAL = "null";

    private static final String PROMPT_TEMPLATE = """
            다음은 길찾기 앱에 입력된 사용자의 음성 명령 {{COUNT}}개입니다.
            현재 시각은 {{NOW}} 입니다. 이 시각을 기준으로 날짜를 계산하세요.
            입력된 순서대로 id 0, 1, 2, ... 를 부여하고, 각 음성에서 출발지/도착지/약속시간을 추출하세요.

            다음 형식의 JSON 배열로만 출력하세요. 설명, 코드블록 없이 JSON 배열만.

            [
              {"id": 0, "start_location": "...", "end_location": "...", "appointment_time": "yyyy-MM-dd HH:mm"},
              {"id": 1, "start_location": "...", "end_location": "...", "appointment_time": "yyyy-MM-dd HH:mm"}
            ]

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
               - 반드시 입력 순서대로 id 0,1,2,... 를 매기고 입력 개수와 동일한 개수의 객체를 반환
               - null 은 JSON null 로 출력. 문자열 "null" 로 쓰지 말 것""";

    public Map<Long, NavigationParseResponse> parseBatch(List<AudioParseRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> requestBody = buildRequestBody(requests);

        String body;
        try {
            body = webClient.post()
                    .uri("/models/{model}:generateContent", model)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (RuntimeException e) {
            log.error("[Gemini] 배치 호출 실패. size={}", requests.size(), e);
            throw new DomainException(NavigationParseErrorCode.PARSE_FAILED, e);
        }

        return toResponseMap(body, requests);
    }

    private Map<String, Object> buildRequestBody(List<AudioParseRequest> requests) {
        List<Map<String, Object>> parts = new ArrayList<>(requests.size() + 1);
        for (AudioParseRequest req : requests) {
            String base64 = Base64.getEncoder().encodeToString(req.audioBytes());
            parts.add(Map.of("inline_data", Map.of(
                    "mime_type", req.mimeType(),
                    "data", base64
            )));
        }
        String prompt = PROMPT_TEMPLATE
                .replace("{{COUNT}}", String.valueOf(requests.size()))
                .replace("{{NOW}}", currentTimeText());
        parts.add(Map.of("text", prompt));

        return Map.of(
                "contents", List.of(Map.of("parts", parts)),
                "generationConfig", Map.of("response_mime_type", "application/json")
        );
    }

    // 한국 시각(KST) 기준 현재 시각 문자열. LLM의 "오늘/내일" 날짜 계산 기준이 된다.
    String currentTimeText() {
        return LocalDateTime.now(clock.withZone(KST)).format(NOW_FORMATTER);
    }

    // 테스트에서 직접 호출하기 위해 package-private.
    // Gemini 응답 본문(JSON 문자열)을 파싱해 요청 순서(0,1,2,...)의 id를 원래 요청 id로 다시 매핑한다.
    Map<Long, NavigationParseResponse> toResponseMap(String body, List<AudioParseRequest> requests) {
        if (body == null || body.isBlank()) {
            log.error("[Gemini] 응답 본문이 비어있음");
            throw new DomainException(NavigationParseErrorCode.PARSE_FAILED);
        }

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

        List<BatchItem> items;
        try {
            items = objectMapper.readValue(
                    resultJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, BatchItem.class)
            );
        } catch (RuntimeException e) {
            log.error("[Gemini] 결과 JSON 파싱 실패. text: {}", resultJson, e);
            throw new DomainException(NavigationParseErrorCode.PARSE_FAILED, e);
        }

        // 모델이 매긴 id(0,1,2,...)를 원래 요청 id(=호출자 입장의 식별자)로 변환한다.
        // 모델이 id를 빠뜨리거나 범위를 벗어나면 해당 항목은 Map에 안 들어가고, 호출 측(Processor)이 누락 처리한다.
        Map<Long, NavigationParseResponse> result = new LinkedHashMap<>(items.size());
        for (BatchItem item : items) {
            if (item.id() == null) continue;
            int idx = item.id().intValue();
            if (idx < 0 || idx >= requests.size()) continue;
            Long originalId = requests.get(idx).id();
            result.put(originalId, new NavigationParseResponse(
                    normalize(item.startLocation()),
                    normalize(item.endLocation()),
                    normalize(item.appointmentTime())
            ));
        }
        return result;
    }

    // 모델이 가끔 문자열 "null"을 그대로 주는 경우가 있어 정규화한다.
    private String normalize(String value) {
        if (value == null) return null;
        if (NULL_LITERAL.equalsIgnoreCase(value)) return null;
        return value;
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

    // 모델이 text 안에 넣어주는 배열의 한 요소
    record BatchItem(
            @JsonProperty("id") Long id,
            @JsonProperty("start_location") String startLocation,
            @JsonProperty("end_location") String endLocation,
            @JsonProperty("appointment_time") String appointmentTime
    ) {
    }
}
