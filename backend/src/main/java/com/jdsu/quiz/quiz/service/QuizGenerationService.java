package com.jdsu.quiz.quiz.service;

import com.jdsu.quiz.events.EventPublisher;
import com.jdsu.quiz.events.PlatformEvent;
import com.jdsu.quiz.quiz.dto.QuizGenerateRequest;
import com.jdsu.quiz.quiz.dto.QuizResponse;
import com.jdsu.quiz.quiz.model.ExamPattern;
import com.jdsu.quiz.quiz.model.RetrievalContext;
import io.micrometer.observation.annotation.Observed;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class QuizGenerationService {
    private final ExamPatternService examPatternService;
    private final OpenAiQuizClient openAiQuizClient;
    private final SourceRetrievalService sourceRetrievalService;
    private final QuestionQualityService questionQualityService;
    private final EventPublisher eventPublisher;

    public QuizGenerationService(
            ExamPatternService examPatternService,
            OpenAiQuizClient openAiQuizClient,
            SourceRetrievalService sourceRetrievalService,
            QuestionQualityService questionQualityService,
            EventPublisher eventPublisher
    ) {
        this.examPatternService = examPatternService;
        this.openAiQuizClient = openAiQuizClient;
        this.sourceRetrievalService = sourceRetrievalService;
        this.questionQualityService = questionQualityService;
        this.eventPublisher = eventPublisher;
    }

    @Observed(name = "jagdisu.ai.quiz.generate")
    public QuizResponse generate(QuizGenerateRequest request) {
        ExamPattern pattern = examPatternService.resolvePattern(request);
        RetrievalContext retrievalContext = sourceRetrievalService.retrieve(request);
        QuizResponse response = openAiQuizClient.generate(request, pattern, retrievalContext)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "AI API key is missing. Add OPENROUTER_API_KEY in backend/.env and restart backend."
                ));

        QuizResponse normalized = new QuizResponse(
                response.quizId(),
                response.subject(),
                response.examName(),
                response.chapter(),
                response.difficulty(),
                response.examPatternSummary(),
                questionQualityService.normalize(response.questions())
        );
        eventPublisher.publish(PlatformEvent.of("QuizGenerated", Map.of(
                "quizId", normalized.quizId(),
                "subject", normalized.subject(),
                "examName", normalized.examName(),
                "questionCount", normalized.questions().size()
        )));
        return normalized;
    }
}
