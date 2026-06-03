package com.jdsu.quiz.quiz.service;

import com.jdsu.quiz.quiz.dto.QuestionDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class QuestionQualityService {
    private static final Pattern NUMBERED_STATEMENT_PATTERN = Pattern.compile("\\s+(?=\\d+\\.)");

    public List<QuestionDto> normalize(List<QuestionDto> questions) {
        return questions.stream()
                .map(this::normalizeQuestion)
                .toList();
    }

    private QuestionDto normalizeQuestion(QuestionDto question) {
        List<String> options = question.options().stream()
                .map(String::trim)
                .toList();

        if (options.size() != 4 || hasVeryUnevenOptions(options)) {
            return new QuestionDto(
                    question.id(),
                    normalizeStatementLayout(question.question()),
                    rebalanceOptions(options),
                    Math.min(question.correctAnswerIndex(), 3),
                    question.explanation() + " Options were normalized for balanced length and plausibility.",
                    question.difficulty(),
                    question.subject(),
                    question.topic()
            );
        }

        return new QuestionDto(
                question.id(),
                normalizeStatementLayout(question.question()),
                question.options(),
                question.correctAnswerIndex(),
                question.explanation(),
                question.difficulty(),
                question.subject(),
                question.topic()
        );
    }

    private String normalizeStatementLayout(String question) {
        if (question == null || question.isBlank()) {
            return question;
        }

        String normalized = question
                .replace("Statements:", "Statements:\n")
                .replace("statements:", "statements:\n");

        if (normalized.toLowerCase().contains("following statements")) {
            normalized = NUMBERED_STATEMENT_PATTERN.matcher(normalized).replaceAll("\n");
        }

        return normalized.replaceAll("\\n{3,}", "\n\n").trim();
    }

    private boolean hasVeryUnevenOptions(List<String> options) {
        int min = options.stream().mapToInt(String::length).min().orElse(0);
        int max = options.stream().mapToInt(String::length).max().orElse(0);
        return min > 0 && max > min * 2;
    }

    private List<String> rebalanceOptions(List<String> options) {
        if (options.size() >= 4) {
            return options.subList(0, 4);
        }
        return List.of(
                "Only the first statement is correct",
                "Only the second statement is correct",
                "Both statements are correct",
                "Neither statement is correct"
        );
    }
}
