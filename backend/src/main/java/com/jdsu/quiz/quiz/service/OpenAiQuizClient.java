package com.jdsu.quiz.quiz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdsu.quiz.ai.provider.AiProviderRouter;
import com.jdsu.quiz.config.DynamicAiConfig;
import com.jdsu.quiz.quiz.dto.QuestionDto;
import com.jdsu.quiz.quiz.dto.QuizGenerateRequest;
import com.jdsu.quiz.quiz.dto.QuizResponse;
import com.jdsu.quiz.quiz.model.ExamPattern;
import com.jdsu.quiz.quiz.model.RetrievalContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Component
public class OpenAiQuizClient {
    private static final Object PROVIDER_RATE_LIMIT_LOCK = new Object();
    private static long nextAllowedRequestAtMillis = 0L;

    private final DynamicAiConfig dynamicAiConfig;
    private final AiProviderRouter aiProviderRouter;
    private final String model;
    private final long minRequestIntervalMillis;
    private final int retryMaxAttempts;
    private final long retryInitialDelayMillis;
    private final long timeoutSeconds;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OpenAiQuizClient(
            DynamicAiConfig dynamicAiConfig,
            AiProviderRouter aiProviderRouter,
            @Value("${app.ai.model}") String model,
            @Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.min-request-interval-ms}") long minRequestIntervalMillis,
            @Value("${app.ai.retry-max-attempts}") int retryMaxAttempts,
            @Value("${app.ai.retry-initial-delay-ms}") long retryInitialDelayMillis,
            @Value("${app.ai.timeout-seconds}") long timeoutSeconds,
            ObjectMapper objectMapper
    ) {
        this.dynamicAiConfig = dynamicAiConfig;
        this.aiProviderRouter = aiProviderRouter;
        this.model = model;
        this.minRequestIntervalMillis = Math.max(0L, minRequestIntervalMillis);
        this.retryMaxAttempts = Math.max(1, retryMaxAttempts);
        this.retryInitialDelayMillis = Math.max(0L, retryInitialDelayMillis);
        this.timeoutSeconds = Math.max(20L, timeoutSeconds);
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Optional<QuizResponse> generate(QuizGenerateRequest request, ExamPattern pattern, RetrievalContext retrievalContext) {
        if (!aiProviderRouter.hasConfiguredProvider()) {
            return Optional.empty();
        }

        try {
            String trainingInstructions = readTrainingInstructions();
            String targetDifficulty = resolveTargetDifficulty(request, pattern);
            String fullTestInstructions = request.fullTestMode() ? """
                    Full test intelligence mode:
                    You are an Advanced Exam Intelligence Engine.
                    First silently analyze the exam structure, total questions, total marks, sections, duration, negative marking, question types, subject distribution, topic distribution, difficulty mix, trends, current-affairs relevance, and cognitive skill mix.
                    Then generate this batch as part of one full-length mock test that follows the actual exam pattern.
                    Match real subject distribution, topic distribution, difficulty, time pressure, and question style.
                    Include recent trends and current affairs only where the exam requires them.
                    Avoid repetitive questions and create realistic distractors.
                    Output JSON compatible with the app. You may include exam_analysis, exam_dna, blueprint, timer_strategy, and adaptive_insights, but the current batch questions must be in either "questions" or "mock_test".
                    Each generated question must include question text, four options, correct answer, difficulty, subject/topic where possible, and explanation.
                    Never generate random questions; always base the batch on the analyzed pattern.
                    """ : """
                    Custom quiz mode:
                    Generate only the requested custom practice quiz for the supplied subject and chapter.
                    """;
            String prompt = """
                    Generate an exam-style quiz as strict JSON only.
                    Schema:
                    {"questions":[{"question":"...","options":["A","B","C","D"],"correctAnswerIndex":0,"explanation":"...","difficulty":"...","subject":"...","topic":"..."}]}

                    Core task:
                    - Analyse the requested exam, subject, chapter, and supplied pattern profile.
                    - Create fresh questions inspired by recent previous-year themes and official/reputed sources.
                    - Do not copy any previous-year question or source text verbatim.

                    Question format rule:
                    - Required format: %s.
                    - If STATEMENT_BASED, use "Consider the following statements" and combination options.
                    - Every STATEMENT_BASED question must contain at least 4 numbered statements.
                    - Each numbered statement must start on a new line in the question text, like:
                      Consider the following statements:
                      1. First statement.
                      2. Second statement.
                      3. Third statement.
                      4. Fourth statement.
                    - If ONE_LINER, keep questions direct, compact, and speed-test oriented.
                    - If MIXED, blend one-liners, application questions, and statement-based questions in the same ratio typical for the exam. Any statement-based item inside MIXED must also have at least 4 numbered statements on separate lines.

                    Quality rules:
                    - Every option must be almost equal in length, detail, grammar, and difficulty.
                    - Avoid obvious clues such as longest option, extreme words, repeated wording, or unmatched units.
                    - Distractors must be realistic mistakes from the same concept level.
                    - Match the exam's real difficulty and pattern instead of making generic school-level MCQs.
                    - Use the requested difficulty level for this quiz. If the requested level is "Exam Pattern", use the real difficulty level of the exam profile.
                    - Explanations should briefly mention why the right option is right and why distractors fail.
                    - Always include subject and topic for each question so the result screen can identify weak areas.
                    - If this is not the first batch, do not repeat, rephrase, or closely mirror any already generated question.
                    - Treat every batch as part of one continuous exam paper with varied subtopics.

                    Mode instructions:
                    %s

                    Editable training instructions:
                    %s

                    Retrieval context from the local question-bank index:
                    %s

                    Retrieval instruction:
                    %s

                    Subject: %s
                    Exam: %s
                    Chapter: %s
                    Language: %s
                    Number of questions: %d
                    Total quiz length requested by user: %d
                    Current batch number: %d
                    Already generated questions to avoid:
                    %s
                    Pattern: %s
                    Difficulty: %s
                    Reference guidance: %s
                    Generation rules: %s
                    """.formatted(
                    pattern.questionFormat(),
                    fullTestInstructions,
                    trainingInstructions,
                    retrievalContext.corpusSummary(),
                    retrievalContext.generationInstruction(),
                    request.subject(),
                    request.examName(),
                    request.chapter() == null ? "" : request.chapter(),
                    request.language() == null ? "English" : request.language(),
                    request.numberOfQuestions(),
                    request.totalQuestions() == null ? request.numberOfQuestions() : request.totalQuestions(),
                    request.batchNumber() == null ? 1 : request.batchNumber(),
                    request.previousQuestionSummaries() == null || request.previousQuestionSummaries().isEmpty()
                            ? "None. This is the first batch."
                            : String.join("\n", request.previousQuestionSummaries()),
                    pattern.patternSummary(),
                    targetDifficulty,
                    String.join("; ", pattern.referenceSources()),
                    String.join("; ", pattern.generationRules())
            );

            Map<String, Object> payload = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", """
                                    You are an expert Indian exam paper-setter.
                                    You produce original, exam-pattern MCQs only.
                                    You never reveal chain-of-thought and never copy protected source questions verbatim.
                                    Before answering, silently check that all options are balanced in length and plausibility.
                                    """),
                            Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 0.35,
                    "response_format", Map.of("type", "json_object")
            );

            String raw = generateWithOpenAi(payload);

            return parseResponse(raw, request, pattern, targetDifficulty);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception ignored) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "JagdiSu AI is temporarily unavailable. Please try again later."
            );
        }
    }

    private void waitForProviderSlot() {
        synchronized (PROVIDER_RATE_LIMIT_LOCK) {
            long now = System.currentTimeMillis();
            long waitMillis = nextAllowedRequestAtMillis - now;

            if (waitMillis > 0) {
                try {
                    Thread.sleep(waitMillis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "JagdiSu AI request was interrupted. Please try again."
                    );
                }
            }

            nextAllowedRequestAtMillis = System.currentTimeMillis() + minRequestIntervalMillis;
        }
    }

    private String callProviderWithRetry(Supplier<String> providerCall) {
        RuntimeException lastException = null;
        long delayMillis = retryInitialDelayMillis;

        for (int attempt = 1; attempt <= retryMaxAttempts; attempt++) {
            try {
                return providerCall.get();
            } catch (RuntimeException exception) {
                if (!isTransientProviderException(exception) || attempt == retryMaxAttempts) {
                    throw exception;
                }

                lastException = exception;
                sleepBeforeRetry(delayMillis);
                delayMillis = Math.max(delayMillis * 2L, retryInitialDelayMillis);
            }
        }

        throw lastException == null
                ? new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "JagdiSu AI is temporarily unavailable. Please try again later.")
                : lastException;
    }

    private void sleepBeforeRetry(long delayMillis) {
        if (delayMillis <= 0) {
            return;
        }

        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "JagdiSu AI request was interrupted. Please try again."
            );
        }
    }

    private boolean isTransientProviderException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ResponseStatusException responseException) {
                int status = responseException.getStatusCode().value();
                return status == 408 || status == 502 || status == 503 || status == 504;
            }

            current = current.getCause();
        }

        return false;
    }

    private String generateWithOpenAi(Map<String, Object> payload) {
        return callProviderWithRetry(() -> aiProviderRouter.chatCompletion("QUIZ_GENERATION", model, payload).rawBody());
    }

    private String postToOpenAi(Map<String, Object> payload) {
        waitForProviderSlot();
        return webClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + dynamicAiConfig.apiKey())
                .header("HTTP-Referer", "http://localhost:1999")
                .header("X-Title", "JagdiSu AI Quiz")
                .bodyValue(payload)
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
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();
    }

    private Optional<QuizResponse> parseResponse(String raw, QuizGenerateRequest request, ExamPattern pattern, String targetDifficulty) throws Exception {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        JsonNode root = objectMapper.readTree(raw);
        String content = root.path("choices").get(0).path("message").path("content").asText();
        JsonNode generated = objectMapper.readTree(content);
        List<QuestionDto> questions = new ArrayList<>();
        JsonNode generatedQuestions = generated.path("questions");
        if (!generatedQuestions.isArray() || generatedQuestions.isEmpty()) {
            generatedQuestions = generated.path("mock_test");
        }

        int index = 1;
        for (JsonNode node : generatedQuestions) {
            List<String> options = new ArrayList<>();
            if (node.path("options").isArray()) {
                node.path("options").forEach(option -> options.add(option.asText()));
            } else {
                Stream.of("Option A", "Option B", "Option C", "Option D", "optionA", "optionB", "optionC", "optionD")
                        .map(node::path)
                        .filter(option -> !option.asText("").isBlank())
                        .forEach(option -> options.add(option.asText()));
            }
            questions.add(new QuestionDto(
                    "ai-" + index++,
                    node.path("question").asText(node.path("Question").asText()),
                    options,
                    resolveCorrectAnswerIndex(node),
                    node.path("explanation").asText(node.path("Explanation").asText()),
                    node.path("difficulty").asText(node.path("Difficulty").asText(targetDifficulty)),
                    resolveQuestionField(node, "subject", "Subject", "section", "Section"),
                    resolveQuestionField(node, "topic", "Topic", "chapter", "Chapter")
            ));
        }

        if (questions.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new QuizResponse(
                UUID.randomUUID().toString(),
                request.subject(),
                request.examName(),
                request.chapter() == null ? "" : request.chapter(),
                targetDifficulty,
                pattern.patternSummary(),
                questions
        ));
    }

    private String resolveQuestionField(JsonNode node, String... fieldNames) {
        return Stream.of(fieldNames)
                .map(field -> node.path(field).asText(""))
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("");
    }

    private int resolveCorrectAnswerIndex(JsonNode node) {
        if (node.has("correctAnswerIndex")) {
            return Math.max(0, Math.min(3, node.path("correctAnswerIndex").asInt()));
        }

        String correctAnswer = Stream.of("Correct Answer", "correctAnswer", "correct_answer", "answer")
                .map(field -> node.path(field).asText(""))
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse("");

        String normalized = correctAnswer.trim().toUpperCase();
        if (normalized.startsWith("A") || normalized.equals("0")) {
            return 0;
        }
        if (normalized.startsWith("B") || normalized.equals("1")) {
            return 1;
        }
        if (normalized.startsWith("C") || normalized.equals("2")) {
            return 2;
        }
        if (normalized.startsWith("D") || normalized.equals("3")) {
            return 3;
        }
        return 0;
    }

    private String resolveTargetDifficulty(QuizGenerateRequest request, ExamPattern pattern) {
        String requestedDifficulty = request.difficultyLevel();
        if (requestedDifficulty == null || requestedDifficulty.isBlank() || "Exam Pattern".equalsIgnoreCase(requestedDifficulty)) {
            return pattern.difficulty();
        }
        return requestedDifficulty.trim() + " for " + request.examName();
    }

    private String readTrainingInstructions() {
        return Stream.of(
                        Path.of("training", "system-prompt.md"),
                        Path.of("training", "question-format-rules.md"),
                        Path.of("training", "difficulty-rules.md"),
                        Path.of("backend", "training", "system-prompt.md"),
                        Path.of("backend", "training", "question-format-rules.md"),
                        Path.of("backend", "training", "difficulty-rules.md")
                )
                .filter(Files::isRegularFile)
                .map(path -> {
                    try {
                        return "## " + path.getFileName() + "\n" + Files.readString(path);
                    } catch (Exception ignored) {
                        return "";
                    }
                })
                .filter(content -> !content.isBlank())
                .distinct()
                .reduce("", (current, content) -> current.isBlank() ? content : current + "\n\n" + content);
    }

    private String extractProviderErrorMessage(String body, int statusCode) {
        try {
            JsonNode error = objectMapper.readTree(body).path("error");
            String message = error.path("message").asText("");
            String type = error.path("type").asText("");
            String code = error.path("code").asText("");
            if (!message.isBlank()) {
                String suffix = Stream.of(type, code)
                        .filter(value -> value != null && !value.isBlank() && !"null".equalsIgnoreCase(value))
                        .distinct()
                        .reduce("", (current, value) -> current.isBlank() ? value : current + ", " + value);
                return suffix.isBlank() ? message : message + " (" + suffix + ")";
            }
        } catch (Exception ignored) {
            // Fall through to concise status-specific messages.
        }

        return switch (statusCode) {
            case 401, 403 -> "AI provider rejected the API key. Check the key, credits, and model access.";
            case 429 -> "AI provider rate limit or quota exceeded. Check usage, credits, and limits.";
            default -> "AI provider returned HTTP " + statusCode + ". Please check provider status and configuration.";
        };
    }
}
