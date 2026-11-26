package org.example.when2go.global.config.gemini;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class GeminiConfig {

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.base-url}")
    private String baseUrl;

    // 오디오를 base64로 실어 보내므로 기본 버퍼(256KB)로는 부족하다. 20MB로 확장.
    private static final int MAX_IN_MEMORY_SIZE = 20 * 1024 * 1024;

    @Bean
    public WebClient geminiWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Goog-Api-Key", apiKey)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .build();
    }

    // 음성 파싱 배치의 Gemini 호출량 제어. 스케줄러(언제 확인할지)와 분리된 호출 정책(얼마나 호출할지).
    @Bean
    public RateLimiter geminiAudioParseRateLimiter(
            @Value("${gemini.audio-parse.rate-limiter.limit-for-period}") int limitForPeriod,
            @Value("${gemini.audio-parse.rate-limiter.limit-refresh-period}") Duration limitRefreshPeriod,
            @Value("${gemini.audio-parse.rate-limiter.timeout-duration}") Duration timeoutDuration
    ) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitForPeriod(limitForPeriod)
                .limitRefreshPeriod(limitRefreshPeriod)
                .timeoutDuration(timeoutDuration)
                .build();
        return RateLimiter.of("geminiAudioParse", config);
    }
}
