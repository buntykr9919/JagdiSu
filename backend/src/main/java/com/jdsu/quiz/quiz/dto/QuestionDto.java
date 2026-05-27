package com.jdsu.quiz.quiz.dto;

import java.util.List;

public record QuestionDto(
        String id,
        String question,
        List<String> options,
        int correctAnswerIndex,
        String explanation,
        String difficulty
) {
}

