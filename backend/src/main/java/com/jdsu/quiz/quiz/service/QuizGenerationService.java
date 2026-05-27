package com.jdsu.quiz.quiz.service;

import com.jdsu.quiz.quiz.dto.QuizGenerateRequest;
import com.jdsu.quiz.quiz.dto.QuizResponse;
import com.jdsu.quiz.quiz.model.ExamPattern;
import com.jdsu.quiz.quiz.model.RetrievalContext;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class QuizGenerationService {
    private final ExamPatternService examPatternService;
    private final OpenAiQuizClient openAiQuizClient;
    private final SourceRetrievalService sourceRetrievalService;
    private final QuestionQualityService questionQualityService;

    public QuizGenerationService(
            ExamPatternService examPatternService,
            OpenAiQuizClient openAiQuizClient,
            SourceRetrievalService sourceRetrievalService,
            QuestionQualityService questionQualityService
    ) {
        this.examPatternService = examPatternService;
        this.openAiQuizClient = openAiQuizClient;
        this.sourceRetrievalService = sourceRetrievalService;
        this.questionQualityService = questionQualityService;
    }

    public QuizResponse generate(QuizGenerateRequest request) {
        ExamPattern pattern = examPatternService.resolvePattern(request);
        RetrievalContext retrievalContext = sourceRetrievalService.retrieve(request);
        QuizResponse response = openAiQuizClient.generate(request, pattern, retrievalContext)
                .orElseThrow(() -> new ResponseStatusException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "AI API key is missing. Add OPENROUTER_API_KEY in backend/.env and restart backend."
                ));

        return new QuizResponse(
                response.quizId(),
                response.subject(),
                response.examName(),
                response.chapter(),
                response.difficulty(),
                response.examPatternSummary(),
                questionQualityService.normalize(response.questions())
        );
    }
}
