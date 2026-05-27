package com.jdsu.quiz.quiz.service;

import com.jdsu.quiz.quiz.dto.QuizGenerateRequest;
import com.jdsu.quiz.quiz.model.QuestionFormat;
import com.jdsu.quiz.quiz.model.RetrievalContext;
import com.jdsu.quiz.quiz.model.SourceQuestion;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class SourceRetrievalService {
    private final List<SourceQuestion> seedCorpus = List.of(
            new SourceQuestion(
                    "UPSC CSE",
                    "Polity",
                    "Parliament",
                    2023,
                    QuestionFormat.STATEMENT_BASED,
                    "UPSC previous-year pattern + NCERT/standard polity notes",
                    "Consider the following statements... Which of the statements given above is/are correct?",
                    "Hard: conceptual exception and statement validation"
            ),
            new SourceQuestion(
                    "UPSC CSE",
                    "History",
                    "Modern India",
                    2022,
                    QuestionFormat.STATEMENT_BASED,
                    "UPSC previous-year pattern + NCERT",
                    "With reference to an event/person, evaluate 2-3 close statements.",
                    "Hard: factual accuracy plus context"
            ),
            new SourceQuestion(
                    "NEET",
                    "Biology",
                    "Human Physiology",
                    2024,
                    QuestionFormat.MIXED,
                    "NCERT + NTA previous-year pattern",
                    "Direct NCERT fact or assertion-like application with close distractors.",
                    "Medium: NCERT wording and conceptual trap"
            ),
            new SourceQuestion(
                    "JEE Main",
                    "Physics",
                    "Modern Physics",
                    2024,
                    QuestionFormat.MIXED,
                    "NTA previous-year pattern + NCERT",
                    "Numerical/application MCQ where distractors are common calculation mistakes.",
                    "Medium to Hard: multi-step application"
            ),
            new SourceQuestion(
                    "SSC CGL",
                    "Quantitative Aptitude",
                    "Algebra",
                    2023,
                    QuestionFormat.ONE_LINER,
                    "SSC previous-year pattern + Testbook-style exam analysis",
                    "Short speed-based one-liner with four close numerical/options.",
                    "Easy to Medium: time-bound pattern recognition"
            ),
            new SourceQuestion(
                    "Bank PO",
                    "Reasoning",
                    "Syllogism",
                    2023,
                    QuestionFormat.ONE_LINER,
                    "Banking previous-year pattern + reputed test prep analysis",
                    "Compact logical prompt with options that differ by a small condition.",
                    "Medium: careful condition reading"
            )
    );

    public RetrievalContext retrieve(QuizGenerateRequest request) {
        String exam = normalize(request.examName());
        String subject = normalize(request.subject());
        String chapter = normalize(request.chapter());

        List<SourceQuestion> examples = seedCorpus.stream()
                .sorted(Comparator.comparingInt(example -> -score(example, exam, subject, chapter)))
                .limit(4)
                .toList();

        String corpusSummary = examples.stream()
                .map(example -> "- " + example.examName() + " " + example.year()
                        + " | " + example.sourceName()
                        + " | Pattern: " + example.questionPattern()
                        + " | Difficulty: " + example.difficultySignal())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("No exact seed match; infer from official syllabus and recent previous-year style.");

        String instruction = """
                Use retrieved examples only as style signals.
                Create original questions; do not copy any source wording.
                Match format, option balance, difficulty, and topic distribution from the closest examples.
                If exact chapter examples are missing, infer from subject/exam pattern and keep output conservative.
                """;

        return new RetrievalContext(examples, corpusSummary, instruction);
    }

    private int score(SourceQuestion example, String exam, String subject, String chapter) {
        int score = 0;
        String exampleExam = normalize(example.examName());
        String exampleSubject = normalize(example.subject());
        String exampleChapter = normalize(example.chapter());

        if (!exam.isBlank() && (exampleExam.contains(exam) || exam.contains(exampleExam))) {
            score += 8;
        }
        if (!subject.isBlank() && (exampleSubject.contains(subject) || subject.contains(exampleSubject))) {
            score += 5;
        }
        if (!chapter.isBlank() && (exampleChapter.contains(chapter) || chapter.contains(exampleChapter))) {
            score += 4;
        }
        return score;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }
}

