package com.jdsu.quiz.ai;

import com.jdsu.quiz.ai.provider.AiProviderRouter;
import com.jdsu.quiz.config.DynamicAiConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai-model")
public class AiModelController {
    private final DynamicAiConfig dynamicAiConfig;
    private final AiProviderRouter aiProviderRouter;
    private final String aiBaseUrl;
    private final String aiModel;

    public AiModelController(
            DynamicAiConfig dynamicAiConfig,
            AiProviderRouter aiProviderRouter,
            @Value("${app.ai.base-url}") String aiBaseUrl,
            @Value("${app.ai.model}") String aiModel
    ) {
        this.dynamicAiConfig = dynamicAiConfig;
        this.aiProviderRouter = aiProviderRouter;
        this.aiBaseUrl = aiBaseUrl;
        this.aiModel = aiModel;
    }

    @GetMapping("/status")
    public AiModelStatus status() {
        return new AiModelStatus(
                "RAG_PROMPT_MODEL",
                hasAiKey() ? "READY" : "NEEDS_AI_KEY",
                List.of(
                        "Exam pattern classifier",
                        "Seed question-bank retriever",
                        "Server-side AI generation client",
                        "Balanced-option guardrail",
                        "No-demo enforcement"
                ),
                List.of(
                        "Provider type: ROUTED_MULTI_PROVIDER",
                        "Provider: " + aiBaseUrl,
                        "Model: " + aiModel,
                        "Configured providers: " + String.join(", ", aiProviderRouter.configuredProviders()),
                        "Crawler and document-reading pipeline scaffolded as ingestion contract",
                        "Live web crawling disabled until source URLs and permissions are configured",
                        "Fine-tuning requires curated dataset and GPU/hosted training",
                        hasAiKey()
                                ? "Question generation is connected to the server-side AI provider"
                                : "Question generation requires OPENAI_API_KEY"
                )
        );
    }

    @PostMapping("/ingestion-jobs")
    public IngestionJobResponse createIngestionJob(@Valid @RequestBody IngestionJobRequest request) {
        return new IngestionJobResponse(
                UUID.randomUUID().toString(),
                "QUEUED_FOR_REVIEW",
                "Source accepted as a future crawl/document-reading/index job. Configure a crawler worker before automatic downloading.",
                request.sourceUrl(),
                request.examName()
        );
    }

    private boolean hasAiKey() {
        return aiProviderRouter.hasConfiguredProvider();
    }

    public record AiModelStatus(
            String modelType,
            String status,
            List<String> activeComponents,
            List<String> notes
    ) {
    }

    public record IngestionJobRequest(
            @NotBlank String sourceUrl,
            @NotBlank String examName,
            String subject,
            String sourceType
    ) {
    }

    public record IngestionJobResponse(
            String jobId,
            String status,
            String message,
            String sourceUrl,
            String examName
    ) {
    }
}
