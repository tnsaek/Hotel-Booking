package com.hotel_booking.service.impl;

import com.hotel_booking.config.GeminiProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiServiceImplTest {

    private HttpServer server;
    private GeminiProperties properties;
    private GeminiServiceImpl service;
    private AtomicReference<String> requestPath;
    private AtomicReference<String> requestBody;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();

        properties = new GeminiProperties();
        properties.setApiKey("test-api-key");
        properties.setModel("gemini-test-model");

        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + server.getAddress().getPort())
                .build();
        service = new GeminiServiceImpl(webClient, properties);

        requestPath = new AtomicReference<>("");
        requestBody = new AtomicReference<>("");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void generateTextPostsTrimmedPromptAndReturnsGeneratedText() {
        respond(exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(readBody(exchange));
            writeJson(exchange, 200, """
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [
                              {
                                "text": "Here is a hotel answer."
                              }
                            ]
                          }
                        }
                      ]
                    }
                    """);
        });

        String text = service.generateText("  Explain hotel pricing.  ");

        assertThat(text).isEqualTo("Here is a hotel answer.");
        assertThat(requestPath.get()).isEqualTo("/models/gemini-test-model:generateContent");
        assertThat(requestBody.get())
                .contains("\"role\":\"user\"")
                .contains("\"text\":\"Explain hotel pricing.\"")
                .contains("\"temperature\":0.4")
                .contains("\"maxOutputTokens\":900");
    }

    @Test
    void generateTextThrowsWhenGeminiReturnsErrorStatus() {
        respond(exchange -> writeJson(exchange, 429, """
                {
                  "error": "rate limited"
                }
                """));

        assertThatThrownBy(() -> service.generateText("Find hotels"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Gemini API request failed with status 429 TOO_MANY_REQUESTS");
    }

    @Test
    void generateTextThrowsWhenGeminiReturnsEmptySuccessfulBody() {
        respond(this::writeNoContent);

        assertThatThrownBy(() -> service.generateText("Find hotels"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Gemini API response did not include generated text");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"candidates\":[]}",
            "{\"candidates\":[\"not-an-object\"]}",
            "{\"candidates\":[{\"content\":\"not-an-object\"}]}",
            "{\"candidates\":[{\"content\":{\"parts\":[]}}]}",
            "{\"candidates\":[{\"content\":{\"parts\":[\"not-an-object\"]}}]}",
            "{\"candidates\":[{\"content\":{\"parts\":[{}]}}]}",
            "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"\"}]}}]}"
    })
    void generateTextThrowsWhenResponseDoesNotContainText(String responseJson) {
        respond(exchange -> writeJson(exchange, 200, responseJson));

        assertThatThrownBy(() -> service.generateText("Find hotels"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Gemini API response did not include generated text");
    }

    @Test
    void generateTextThrowsWhenApiKeyIsMissing() {
        properties.setApiKey(" ");

        assertThatThrownBy(() -> service.generateText("Find hotels"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Gemini API key is not configured");
    }

    @Test
    void generateTextThrowsWhenModelIsMissing() {
        properties.setModel(" ");

        assertThatThrownBy(() -> service.generateText("Find hotels"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Gemini model is not configured");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    void generateTextThrowsWhenPromptIsBlank(String prompt) {
        assertThatThrownBy(() -> service.generateText(prompt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Prompt is required");
    }

    @Test
    void extractTextConvertsMapKeysToStringsAndIgnoresNullKeys() throws Exception {
        Map<Object, Object> part = new LinkedHashMap<>();
        part.put(null, "ignored");
        part.put(new StringBuilder("text"), "Text from non-string key.");

        Map<Object, Object> content = new LinkedHashMap<>();
        content.put(null, "ignored");
        content.put(new StringBuilder("parts"), List.of(part));

        Map<Object, Object> candidate = new LinkedHashMap<>();
        candidate.put(null, "ignored");
        candidate.put(new StringBuilder("content"), content);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("candidates", List.of(candidate));

        assertThat(invokeExtractText(response)).contains("Text from non-string key.");
    }

    @SuppressWarnings("unchecked")
    private Optional<String> invokeExtractText(Map<String, Object> response) throws Exception {
        Method method = GeminiServiceImpl.class.getDeclaredMethod("extractText", Map.class);
        method.setAccessible(true);
        return (Optional<String>) method.invoke(service, response);
    }

    private void respond(ExchangeHandler handler) {
        server.createContext("/models/gemini-test-model:generateContent", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
    }

    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void writeJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private void writeNoContent(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(204, -1);
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
