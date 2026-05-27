package com.jdsu.quiz.quiz.model;

public record SourceQuestion(
        String examName,
        String subject,
        String chapter,
        int year,
        QuestionFormat format,
        String sourceName,
        String questionPattern,
        String difficultySignal
) {
}

