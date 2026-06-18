package com.hotel_booking.service.impl;

import com.hotel_booking.config.GeminiProperties;
import com.hotel_booking.service.GeminiService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class GeminiServiceImpl implements GeminiService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE =
            new ParameterizedTypeReference<>() {
            };
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final WebClient geminiWebClient;
    private final GeminiProperties geminiProperties;

    public GeminiServiceImpl(
            @Qualifier("geminiWebClient") WebClient geminiWebClient,
            GeminiProperties geminiProperties
    ) {
        this.geminiWebClient = geminiWebClient;
        this.geminiProperties = geminiProperties;
    }

    @Override
    public String generateText(String prompt) {
        validateRequest(prompt);

        Map<String, Object> response = geminiWebClient.post()
                .uri("/models/{model}:generateContent", geminiProperties.getModel())
                .bodyValue(buildRequestBody(prompt.trim()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> new IllegalStateException("Gemini API request failed with status "
                                        + clientResponse.statusCode())))
                .bodyToMono(MAP_RESPONSE)
                .block(REQUEST_TIMEOUT);

        return extractText(response)
                .orElseThrow(() -> new IllegalStateException("Gemini API response did not include generated text"));
    }

    private void validateRequest(String prompt) {
        if (!geminiProperties.isConfigured()) {
            throw new IllegalStateException("Gemini API key is not configured");
        }
        if (!StringUtils.hasText(geminiProperties.getModel())) {
            throw new IllegalStateException("Gemini model is not configured");
        }
        if (!StringUtils.hasText(prompt)) {
            throw new IllegalArgumentException("Prompt is required");
        }
    }

    private Map<String, Object> buildRequestBody(String prompt) {
        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("text", prompt);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("role", "user");
        content.put("parts", List.of(textPart));

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", 0.4);
        generationConfig.put("maxOutputTokens", 900);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(content));
        body.put("generationConfig", generationConfig);
        return body;
    }

    private Optional<String> extractText(Map<String, Object> response) {
        return firstArrayItem(response == null ? null : response.get("candidates"))
                .flatMap(candidate -> object(candidate.get("content")))
                .flatMap(content -> firstArrayItem(content.get("parts")))
                .map(part -> text(part, "text"))
                .filter(StringUtils::hasText);
    }

    private Optional<Map<String, Object>> firstArrayItem(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return Optional.empty();
        }
        return object(list.get(0));
    }

    private Optional<Map<String, Object>> object(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Optional.empty();
        }

        Map<String, Object> map = new LinkedHashMap<>();
        rawMap.forEach((key, mapValue) -> {
            if (key != null) {
                map.put(String.valueOf(key), mapValue);
            }
        });
        return Optional.of(map);
    }

    private String text(Map<String, Object> node, String fieldName) {
        Object value = node.get(fieldName);
        return value == null ? null : String.valueOf(value);
    }
}
