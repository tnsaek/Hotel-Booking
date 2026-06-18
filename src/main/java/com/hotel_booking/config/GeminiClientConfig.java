package com.hotel_booking.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class GeminiClientConfig {
    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    @Bean
    @Qualifier("geminiWebClient")
    public WebClient geminiWebClient(GeminiProperties geminiProperties) {
        return WebClient.builder()
                .baseUrl(GEMINI_BASE_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("x-goog-api-key", geminiProperties.getApiKey() == null ? "" : geminiProperties.getApiKey())
                .build();
    }
}
