package com.jdsu.quiz.quiz.service;

import com.jdsu.quiz.quiz.dto.QuizGenerateRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExamPatternServiceTest {
    private final ExamPatternService service = new ExamPatternService();

    @Test
    void resolvesJeePattern() {
        var pattern = service.resolvePattern(new QuizGenerateRequest("Physics", "JEE Main", "Optics", "English", 5, "Exam Pattern", true, 0.25, null, null, null));

        assertThat(pattern.difficulty()).contains("Medium");
        assertThat(pattern.patternSummary()).contains("JEE");
    }
}
