package com.jdsu.quiz.notes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdsu.quiz.config.DynamicAiConfig;
import com.jdsu.quiz.notes.NotesEvaluationController.NotesEvaluationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class NotesEvaluationService {
    private final DynamicAiConfig dynamicAiConfig;
    private final String model;
    private final long timeoutSeconds;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public NotesEvaluationService(
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

    public NotesEvaluationResponse evaluate(
            String examName,
            String questionPaperText,
            String answerText,
            String language,
            MultipartFile file
    ) {
        if (examName == null || examName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exam name is required.");
        }
        if ((answerText == null || answerText.isBlank()) && (file == null || file.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload handwritten notes or paste answer text.");
        }
        if (!dynamicAiConfig.hasApiKey()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI API key is missing.");
        }

        try {
            String prompt = """
                    You are JagdiSu's strict but helpful exam evaluator.

                    Task:
                    - If an image is attached, perform OCR on the handwritten answer and convert it into clean text.
                    - Read the question paper/context and the student's answer.
                    - Find factual mistakes, missing points, unclear wording, and exam-specific scoring issues.
                    - Give a realistic score according to the exam's expected answer quality.
                    - Keep feedback practical and student-friendly.

                    Return strict JSON only:
                    {
                      "extractedText": "clean OCR text or pasted answer text",
                      "mistakes": ["mistake 1", "mistake 2"],
                      "strengths": ["strength 1", "strength 2"],
                      "score": 0,
                      "maxScore": 100,
                      "feedback": "short coaching feedback"
                    }

                    Exam: %s
                    Language: %s
                    Question paper / question context:
                    %s

                    Typed or corrected answer text:
                    %s
                    """.formatted(
                    examName.trim(),
                    language == null || language.isBlank() ? "English" : language.trim(),
                    questionPaperText == null || questionPaperText.isBlank() ? "Not provided" : questionPaperText.trim(),
                    answerText == null || answerText.isBlank() ? "Use OCR from uploaded image." : answerText.trim()
            );

            List<Map<String, Object>> userContent = new ArrayList<>();
            userContent.add(Map.of("type", "text", "text", prompt));
            if (file != null && !file.isEmpty() && file.getContentType() != null && file.getContentType().startsWith("image/")) {
                String imageData = Base64.getEncoder().encodeToString(file.getBytes());
                userContent.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", "data:" + file.getContentType() + ";base64," + imageData)
                ));
            }

            Map<String, Object> payload = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", "You evaluate handwritten exam answers and return compact JSON."),
                            Map.of("role", "user", "content", userContent)
                    ),
                    "temperature", 0.2,
                    "response_format", Map.of("type", "json_object")
            );

            String raw = postToProvider(payload);
            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("choices").get(0).path("message").path("content").asText();
            JsonNode evaluation = objectMapper.readTree(content);

            return new NotesEvaluationResponse(
                    examName.trim(),
                    textOrFallback(evaluation.path("extractedText").asText(), answerText),
                    toList(evaluation.path("mistakes"), "No major mistake detected from the provided answer."),
                    toList(evaluation.path("strengths"), "Answer submitted for checking."),
                    clamp(evaluation.path("score").asInt(0), 0, 100),
                    Math.max(1, evaluation.path("maxScore").asInt(100)),
                    textOrFallback(evaluation.path("feedback").asText(), "Revise the weak points and write a more exam-focused answer.")
            );
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "JagdiSu notes checker is temporarily unavailable. Please try again.");
        }
    }

    private String postToProvider(Map<String, Object> payload) {
        return webClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + dynamicAiConfig.apiKey())
                .header("HTTP-Referer", "http://localhost:1999")
                .header("X-Title", "JagdiSu Notes OCR")
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

    private List<String> toList(JsonNode node, String fallback) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                String value = item.asText("");
                if (!value.isBlank()) {
                    values.add(value);
                }
            });
        }
        return values.isEmpty() ? List.of(fallback) : values;
    }

    private String textOrFallback(String value, String fallback) {
        if (value != null && !value.isBlank()) {
            return value.trim();
        }
        return fallback == null || fallback.isBlank() ? "" : fallback.trim();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
