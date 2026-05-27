package com.jdsu.quiz.quiz.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record QuizGenerateRequest(
        @NotBlank String subject,
        @NotBlank String examName,
        String chapter,
        String language,
        @Min(1) @Max(5) int numberOfQuestions,
        String difficultyLevel,
        boolean hintsEnabled,
        double negativeMarking,
        @Min(1) @Max(150) Integer totalQuestions,
        @Min(1) Integer batchNumber,
        List<String> previousQuestionSummaries
) {
}
