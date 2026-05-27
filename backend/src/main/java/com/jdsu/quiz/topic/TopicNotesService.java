package com.jdsu.quiz.topic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdsu.quiz.config.DynamicAiConfig;
import com.jdsu.quiz.topic.TopicNotesController.TopicNotesRequest;
import com.jdsu.quiz.topic.TopicNotesController.TopicNotesResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class TopicNotesService {
    private final DynamicAiConfig dynamicAiConfig;
    private final String model;
    private final long timeoutSeconds;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public TopicNotesService(
            DynamicAiConfig dynamicAiConfig,
            @Value("${app.ai.model}") String model,
            @Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.timeout-seconds}") long timeoutSeconds,
            ObjectMapper objectMapper
    ) {
        this.dynamicAiConfig = dynamicAiConfig;
        this.model = model;
        this.timeoutSeconds = Math.max(20L, timeoutSeconds);
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public TopicNotesResponse generate(TopicNotesRequest request) {
        if (!dynamicAiConfig.hasApiKey()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI API key is missing.");
        }

        try {
            String prompt = """
                    Create complete student-friendly study notes for the topic below.

                    Requirements:
                    - Write in this language: %s.
                    - Depth must match UPSC CSE preparation level, not school-level notes.
                    - Cover definition, conceptual background, dimensions, examples, current relevance where useful, prelims facts, mains analytical points, common mistakes, and a short revision summary.
                    - Add a dedicated "UPSC CSE Angle" section for Prelims and Mains.
                    - Add a dedicated "Memory Tricks" section with catchy, fun, desi mnemonics or Hinglish-style tricks that help students remember important points.
                    - Keep tricks respectful, simple, memorable, and exam-useful.
                    - Use clear headings and bullet points.
                    - Highlight important words or sentences by wrapping them in **double asterisks**.
                    - Keep it accurate, practical, and useful for exam preparation.
                    - Do not include markdown tables.

                    Topic: %s
                    """.formatted(request.language(), request.topic());

            Map<String, Object> payload = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", "You are JagdiSu's expert tutor. Produce accurate, structured notes for students."),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 0.25
            );

            String raw = postToProvider(payload);
            JsonNode root = objectMapper.readTree(raw);
            String notes = root.path("choices").get(0).path("message").path("content").asText();
            if (notes == null || notes.isBlank()) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Notes could not be generated. Please try again.");
            }

            return new TopicNotesResponse(request.topic(), request.language(), notes.trim());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "JagdiSu AI notes are temporarily unavailable. Please try again.");
        }
    }

    private String postToProvider(Map<String, Object> payload) {
        return webClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + dynamicAiConfig.apiKey())
                .header("HTTP-Referer", "http://localhost:1999")
                .header("X-Title", "JagdiSu Topic Notes")
                .bodyValue(payload)
                .exchangeToMono(response -> {
                    if (response.statusCode().isError()) {
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new ResponseStatusException(
                                        response.statusCode(),
                                        body.isBlank() ? "AI provider rejected the notes request." : body
                                )));
                    }
                    return response.bodyToMono(String.class);
                })
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();
    }
}
