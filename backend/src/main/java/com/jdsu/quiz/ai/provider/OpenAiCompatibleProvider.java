package com.jdsu.quiz.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

public abstract class OpenAiCompatibleProvider implements AiProvider {
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;

    protected OpenAiCompatibleProvider(ObjectMapper objectMapper, String baseUrl, String apiKey) {
        this.objectMapper = objectMapper;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isBlank() && !baseUrl.isBlank();
    }

    @Override
    @Retry(name = "aiProvider")
    @CircuitBreaker(name = "aiProvider")
    public AiProviderResponse chatCompletion(AiProviderRequest request) {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(503), providerName() + " is not configured.");
        }

        long startedAt = System.nanoTime();
        String raw = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .post()
                .uri(chatCompletionsPath())
                .headers(headers -> applyHeaders(headers, request))
                .bodyValue(request.payload())
                .exchangeToMono(response -> {
                    if (response.statusCode().isError()) {
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new ResponseStatusException(
                                        response.statusCode(),
                                        extractProviderErrorMessage(body, response.statusCode().value())
                                )));
                    }
                    return response.bodyToMono(String.class);
                })
                .timeout(Duration.ofSeconds(Math.max(20L, request.timeoutSeconds())))
                .block();

        int latencyMs = (int) Math.max(1, Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        Usage usage = extractUsage(raw);
        return new AiProviderResponse(
                providerName(),
                request.model(),
                raw,
                usage.promptTokens(),
                usage.completionTokens(),
                latencyMs,
                estimateCostUsd(request.model(), usage.promptTokens(), usage.completionTokens())
        );
    }

    protected String chatCompletionsPath() {
        return "/chat/completions";
    }

    protected void applyHeaders(HttpHeaders headers, AiProviderRequest request) {
        headers.setBearerAuth(apiKey);
        headers.set("HTTP-Referer", "http://localhost:1999");
        headers.set("X-Title", "JagdiSu " + request.feature());
    }

    protected double estimateCostUsd(String model, int promptTokens, int completionTokens) {
        int totalTokens = Math.max(0, promptTokens) + Math.max(0, completionTokens);
        if (totalTokens == 0) {
            return 0;
        }
        double perMillion = model != null && model.toLowerCase().contains("gpt-4") ? 0.25 : 0.10;
        return Math.round((totalTokens / 1_000_000.0) * perMillion * 1_000_000.0) / 1_000_000.0;
    }

    protected String extractProviderErrorMessage(String body, int statusCode) {
        try {
            JsonNode error = objectMapper.readTree(body).path("error");
            String message = error.path("message").asText("");
            if (!message.isBlank()) {
                return providerName() + ": " + message;
            }
        } catch (Exception ignored) {
            // Use concise fallback below.
        }
        return providerName() + " returned HTTP " + statusCode + ".";
    }

    private Usage extractUsage(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Usage(0, 0);
        }
        try {
            JsonNode usage = objectMapper.readTree(raw).path("usage");
            return new Usage(
                    Math.max(0, usage.path("prompt_tokens").asInt(0)),
                    Math.max(0, usage.path("completion_tokens").asInt(0))
            );
        } catch (Exception ignored) {
            return new Usage(0, 0);
        }
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.endsWith("/")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private record Usage(int promptTokens, int completionTokens) {
    }
}
