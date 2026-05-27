package com.jdsu.quiz.quiz.dto;

import java.util.List;

public record QuizResponse(
        String quizId,
        String subject,
        String examName,
        String chapter,
        String difficulty,
        String examPatternSummary,
        List<QuestionDto> questions
) {
}

