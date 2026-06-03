package com.jdsu.quiz.notes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdsu.quiz.ai.provider.AiProviderRouter;
import com.jdsu.quiz.config.DynamicAiConfig;
import com.jdsu.quiz.events.EventPublisher;
import com.jdsu.quiz.events.PlatformEvent;
import com.jdsu.quiz.notes.NotesEvaluationController.NotesEvaluationResponse;
import com.jdsu.quiz.notes.NotesEvaluationController.NotesExtractionResponse;
import com.jdsu.quiz.quiz.dto.QuestionDto;
import com.jdsu.quiz.quiz.dto.QuizResponse;
import io.micrometer.observation.annotation.Observed;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

@Service
public class NotesEvaluationService {
    private static final int MAX_PDF_PAGES_FOR_OCR = 20;

    private final DynamicAiConfig dynamicAiConfig;
    private final AiProviderRouter aiProviderRouter;
    private final String model;
    private final long timeoutSeconds;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final EventPublisher eventPublisher;

    public NotesEvaluationService(
            DynamicAiConfig dynamicAiConfig,
            AiProviderRouter aiProviderRouter,
            @Value("${app.ai.model}") String model,
            @Value("${app.ai.base-url}") String baseUrl,
            @Value("${app.ai.timeout-seconds}") long timeoutSeconds,
            ObjectMapper objectMapper,
            EventPublisher eventPublisher
    ) {
        this.dynamicAiConfig = dynamicAiConfig;
        this.aiProviderRouter = aiProviderRouter;
        this.model = model;
        this.timeoutSeconds = Math.max(20L, timeoutSeconds);
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Observed(name = "jagdisu.ocr.answer.evaluate")
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
        if (!aiProviderRouter.hasConfiguredProvider()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI API key is missing.");
        }

        try {
            String prompt = """
                    You are JagdiSu's strict but helpful exam evaluator.

                    Task:
                    - If an image is attached, read the handwritten answer and convert it into clean text.
                    - Read the question paper/context and the student's answer.
                    - Find factual mistakes, missing points, unclear wording, and exam-specific scoring issues.
                    - Give a realistic score according to the exam's expected answer quality.
                    - Clearly separate strengths, weaknesses, and practical improvement steps.
                    - Write one polished demo/model answer that fixes the listed mistakes and weaknesses for the given question.
                    - Keep feedback practical and student-friendly.

                    Return strict JSON only:
                    {
                      "extractedText": "clean read text or pasted answer text",
                      "mistakes": ["mistake 1", "mistake 2"],
                      "strengths": ["strength 1", "strength 2"],
                      "weaknesses": ["weakness 1", "weakness 2"],
                      "improvements": ["specific next step 1", "specific next step 2"],
                      "score": 0,
                      "maxScore": 100,
                      "feedback": "short coaching feedback",
                      "idealAnswer": "a corrected demo answer for this question, written in the selected language and exam style"
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
                    answerText == null || answerText.isBlank() ? "Read from uploaded image." : answerText.trim()
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

            String raw = aiProviderRouter.chatCompletion("ANSWER_EVALUATION", model, payload).rawBody();
            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("choices").get(0).path("message").path("content").asText();
            JsonNode evaluation = objectMapper.readTree(content);

            NotesEvaluationResponse response = new NotesEvaluationResponse(
                    examName.trim(),
                    textOrFallback(evaluation.path("extractedText").asText(), answerText),
                    toList(evaluation.path("mistakes"), "No major mistake detected from the provided answer."),
                    toList(evaluation.path("strengths"), "Answer submitted for checking."),
                    toList(evaluation.path("weaknesses"), "Add more exam-specific keywords, structure, and supporting points where needed."),
                    toList(evaluation.path("improvements"), "Revise the weak points, then rewrite the answer in a clearer exam format."),
                    clamp(evaluation.path("score").asInt(0), 0, 100),
                    Math.max(1, evaluation.path("maxScore").asInt(100)),
                    textOrFallback(evaluation.path("feedback").asText(), "Revise the weak points and write a more exam-focused answer."),
                    textOrFallback(evaluation.path("idealAnswer").asText(), buildFallbackIdealAnswer(questionPaperText, answerText, language))
            );
            eventPublisher.publish(PlatformEvent.of("AnswerEvaluated", Map.of(
                    "examName", response.examName(),
                    "score", response.score(),
                    "maxScore", response.maxScore()
            )));
            return response;
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
                .header("X-Title", "JagdiSu Notes Checker")
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

    @Observed(name = "jagdisu.ocr.extract")
    public NotesExtractionResponse extract(String language, MultipartFile file, MultipartFile questionFile, MultipartFile answerFile) {
        if ((file == null || file.isEmpty())
                && (questionFile == null || questionFile.isEmpty())
                && (answerFile == null || answerFile.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a PDF or image containing the question and answer.");
        }
        if (!aiProviderRouter.hasConfiguredProvider()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI API key is missing.");
        }

        try {
            String prompt = """
                    Read the uploaded exam PDF/images and return strict JSON only.

                    Return:
                    {
                      "questionText": "question/prompt text only",
                      "answerText": "student answer text only"
                    }

                    Rules:
                    - A single uploaded PDF/image may contain both question and answer.
                    - Detect headings such as Question, Q., Answer, Ans., Solution, or the layout boundary.
                    - Put only the question in questionText and only the student's answer in answerText.
                    - If separation is unclear, infer the most likely split from exam format.
                    - Keep the original meaning.
                    - Fix obvious line breaks and spacing.
                    - Do not add answers or explanations.
                    - Language preference: %s
                    """.formatted(language == null || language.isBlank() ? "English" : language.trim());

            List<Map<String, Object>> userContent = new ArrayList<>();
            userContent.add(Map.of("type", "text", "text", prompt));
            addDocumentContent(userContent, file, "Uploaded PDF/image containing question and answer:");
            addImageContent(userContent, questionFile, "Question page image:");
            addImageContent(userContent, answerFile, "Answer page image:");

            Map<String, Object> payload = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", "You read handwritten exam pages and return compact JSON."),
                            Map.of("role", "user", "content", userContent)
                    ),
                    "temperature", 0.1,
                    "response_format", Map.of("type", "json_object")
            );

            String raw = aiProviderRouter.chatCompletion("OCR_EXTRACTION", model, payload).rawBody();
            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("choices").get(0).path("message").path("content").asText();
            JsonNode extracted = objectMapper.readTree(content);

            return new NotesExtractionResponse(
                    textOrFallback(extracted.path("questionText").asText(), ""),
                    textOrFallback(extracted.path("answerText").asText(), "")
            );
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Uploaded pages could not be read. Please type the text manually.");
        }
    }

    @Observed(name = "jagdisu.ai.notes.quiz.generate")
    public QuizResponse generateNotesQuiz(
            String language,
            int numberOfQuestions,
            int totalQuestions,
            int batchNumber,
            List<String> previousQuestionSummaries,
            List<MultipartFile> files
    ) {
        if (files == null || files.isEmpty() || files.stream().allMatch(MultipartFile::isEmpty)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload notes before starting the quiz.");
        }
        if (!aiProviderRouter.hasConfiguredProvider()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI API key is missing.");
        }

        int safeQuestionCount = Math.max(1, Math.min(5, numberOfQuestions));
        int safeBatch = Math.max(1, batchNumber);
        int startPage = (safeBatch - 1) * 3;

        try {
            String prompt = """
                    Generate a notes-based quiz from only the uploaded notes pages/images attached in this request.
                    Return strict JSON only:
                    {"questions":[{"question":"...","options":["A","B","C","D"],"correctAnswerIndex":0,"explanation":"...","difficulty":"Notes Based","subject":"Uploaded Notes","topic":"...","sourceQuote":"exact words copied from the uploaded notes","sourcePage":1}]}

                    Rules:
                    - Generate up to %d MCQs. Fewer is better than unsupported questions.
                    - Every question must be answerable from an exact sentence, phrase, formula, table row, or visible fact in the attached notes chunk.
                    - For every question, sourceQuote is mandatory and must copy exact visible words from the notes. Do not paraphrase sourceQuote.
                    - If you cannot provide an exact sourceQuote from the notes for a question, do not include that question.
                    - Do not use outside knowledge, exam memory, common facts, or assumptions.
                    - Do not create questions from file name, subject name, or topic guesses.
                    - Keep options balanced and plausible.
                    - If this is a later batch, avoid repeating previous questions.
                    - Language: %s
                    - Total requested test length: %d
                    - Current batch: %d
                    Previous questions to avoid:
                    %s
                    """.formatted(
                    safeQuestionCount,
                    language == null || language.isBlank() ? "English" : language.trim(),
                    Math.max(1, totalQuestions),
                    safeBatch,
                    previousQuestionSummaries == null || previousQuestionSummaries.isEmpty()
                            ? "None"
                            : String.join("\n", previousQuestionSummaries)
            );

            List<Map<String, Object>> userContent = new ArrayList<>();
            userContent.add(Map.of("type", "text", "text", prompt));
            for (MultipartFile file : files.stream().filter(item -> !item.isEmpty()).limit(5).toList()) {
                addDocumentContent(userContent, file, "Notes chunk from uploaded file:", startPage, 3);
            }

            Map<String, Object> payload = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", "You create MCQs only from uploaded notes and return compact JSON."),
                            Map.of("role", "user", "content", userContent)
                    ),
                    "temperature", 0.05,
                    "response_format", Map.of("type", "json_object")
            );

            String raw = aiProviderRouter.chatCompletion("NOTES_QUIZ", model, payload).rawBody();
            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("choices").get(0).path("message").path("content").asText();
            JsonNode generated = objectMapper.readTree(content);
            JsonNode generatedQuestions = generated.path("questions");
            List<QuestionDto> questions = new ArrayList<>();

            int index = 1;
            for (JsonNode node : generatedQuestions) {
                String sourceQuote = node.path("sourceQuote").asText("");
                String questionText = node.path("question").asText("");
                if (sourceQuote.isBlank() || questionText.isBlank()) {
                    continue;
                }
                List<String> options = new ArrayList<>();
                node.path("options").forEach(option -> options.add(option.asText()));
                if (options.size() < 4) {
                    continue;
                }
                int sourcePage = Math.max(1, node.path("sourcePage").asInt(startPage + 1));
                questions.add(new QuestionDto(
                        "notes-" + safeBatch + "-" + index++,
                        questionText,
                        options.subList(0, 4),
                        Math.max(0, Math.min(3, node.path("correctAnswerIndex").asInt(0))),
                        textOrFallback(node.path("explanation").asText(), "Answer is based on the uploaded notes.")
                                + "\nSource page " + sourcePage + ": " + sourceQuote,
                        textOrFallback(node.path("difficulty").asText(), "Notes Based"),
                        textOrFallback(node.path("subject").asText(), "Uploaded Notes"),
                        textOrFallback(node.path("topic").asText(), "Notes Chunk " + safeBatch)
                ));
            }

            if (questions.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No source-backed questions could be generated from this notes chunk. Upload clearer notes or try fewer questions.");
            }

            return new QuizResponse(
                    UUID.randomUUID().toString(),
                    "Uploaded Notes",
                    "Notes Based Quiz",
                    "Notes chunk " + safeBatch,
                    "Notes Based",
                    "Questions are generated progressively from uploaded notes chunks.",
                    questions
            );
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Notes based quiz could not be generated. Please try a clearer PDF or image.");
        }
    }

    private void addImageContent(List<Map<String, Object>> userContent, MultipartFile file, String label) throws Exception {
        if (file == null || file.isEmpty()) {
            return;
        }
        if (!isImage(file)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF, PNG, or JPG uploads are supported.");
        }
        userContent.add(Map.of("type", "text", "text", label));
        String imageData = Base64.getEncoder().encodeToString(file.getBytes());
        userContent.add(Map.of(
                "type", "image_url",
                "image_url", Map.of("url", "data:" + file.getContentType() + ";base64," + imageData)
        ));
    }

    private void addDocumentContent(List<Map<String, Object>> userContent, MultipartFile file, String label) throws Exception {
        addDocumentContent(userContent, file, label, 0, MAX_PDF_PAGES_FOR_OCR);
    }

    private void addDocumentContent(List<Map<String, Object>> userContent, MultipartFile file, String label, int startPage, int maxPages) throws Exception {
        if (file == null || file.isEmpty()) {
            return;
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType();
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (isImage(file)) {
            addImageContent(userContent, file, label);
            return;
        }
        if (isDocx(contentType, filename)) {
            userContent.add(Map.of("type", "text", "text", label));
            try (XWPFDocument document = new XWPFDocument(file.getInputStream())) {
                String extractedText = document.getParagraphs()
                        .stream()
                        .map(paragraph -> paragraph.getText() == null ? "" : paragraph.getText().trim())
                        .filter(text -> !text.isBlank())
                        .reduce("", (current, text) -> current.isBlank() ? text : current + "\n" + text);
                if (extractedText.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "DOCX file does not contain readable text.");
                }
                userContent.add(Map.of("type", "text", "text", "DOCX extracted text:\n" + extractedText));
            }
            return;
        }
        if (!contentType.equalsIgnoreCase("application/pdf") && !filename.endsWith(".pdf")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF, DOCX, PNG, JPG, or WEBP uploads are supported.");
        }

        userContent.add(Map.of("type", "text", "text", label));
        try (PDDocument document = PDDocument.load(file.getBytes())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int fromPage = Math.min(Math.max(0, startPage), Math.max(0, document.getNumberOfPages() - 1));
            int toPage = Math.min(document.getNumberOfPages(), fromPage + Math.max(1, maxPages));
            for (int pageIndex = fromPage; pageIndex < toPage; pageIndex += 1) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, 150, ImageType.RGB);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(image, "png", output);
                String imageData = Base64.getEncoder().encodeToString(output.toByteArray());
                userContent.add(Map.of("type", "text", "text", "PDF page " + (pageIndex + 1) + ":"));
                userContent.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", "data:image/png;base64," + imageData)
                ));
            }
            if (toPage < document.getNumberOfPages()) {
                userContent.add(Map.of(
                        "type",
                        "text",
                        "text",
                        "Only pages " + (fromPage + 1) + "-" + toPage + " were read from this PDF chunk. Later quiz batches can read later pages."
                ));
            }
        }
    }

    private boolean isImage(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null && contentType.startsWith("image/")) {
            return true;
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        return filename.endsWith(".png") || filename.endsWith(".jpg") || filename.endsWith(".jpeg") || filename.endsWith(".webp");
    }

    private boolean isDocx(String contentType, String filename) {
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(contentType)
                || filename.endsWith(".docx");
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

    private String buildFallbackIdealAnswer(String questionPaperText, String answerText, String language) {
        String selectedLanguage = language == null || language.isBlank() ? "English" : language.trim();
        String question = questionPaperText == null || questionPaperText.isBlank()
                ? "the given question"
                : questionPaperText.trim();
        String base = answerText == null || answerText.isBlank()
                ? "Use a clear introduction, relevant keywords, logically ordered points, and a concise conclusion."
                : answerText.trim();
        return "Demo answer (" + selectedLanguage + "):\n\n"
                + "For " + question + ", write the answer in a clear exam structure. "
                + "Start with a direct introduction, explain the core concept with accurate facts, add relevant examples or keywords, "
                + "and close with a brief conclusion.\n\n"
                + "Improved answer draft:\n" + base;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
