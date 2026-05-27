package com.jdsu.quiz.quiz.model;

import java.util.List;

public record ExamPattern(
        String examName,
        String difficulty,
        String questionStyle,
        String patternSummary,
        QuestionFormat questionFormat,
        List<String> focusAreas,
        List<String> referenceSources,
        List<String> generationRules
) {
}
