package org.example.when2go.global.config.route;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class GoogleRoutesConfig {

    @Value("${google.routes.api-key}")
    private String apiKey;

    @Value("${google.routes.base-url}")
    private String baseUrl;

    @Bean
    public WebClient googleRoutesWebClient()  {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Goog-Api-Key", apiKey)
                .defaultHeader("X-Goog-FieldMask", "routes.duration")
                .build();
    }



}