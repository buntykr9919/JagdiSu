package com.jdsu.quiz.quiz.service;

import com.jdsu.quiz.quiz.dto.QuizGenerateRequest;
import com.jdsu.quiz.quiz.model.ExamPattern;
import com.jdsu.quiz.quiz.model.QuestionFormat;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class ExamPatternService {
    @Cacheable(cacheNames = "examPatterns", key = "#request.examName() + ':' + #request.subject() + ':' + #request.difficultyLevel()")
    public ExamPattern resolvePattern(QuizGenerateRequest request) {
        String exam = request.examName().toLowerCase(Locale.ROOT);

        if (exam.contains("jee")) {
            return new ExamPattern(
                    request.examName(),
                    "Medium to Hard",
                    "Conceptual numericals and application MCQs",
                    "JEE style: concept depth, calculation speed, previous-year inspired application, mixed easy-medium-hard distribution.",
                    QuestionFormat.MIXED,
                    List.of("concept clarity", "formula application", "multi-step reasoning", "previous-year pattern adaptation"),
                    List.of("NTA JEE information bulletin", "NCERT textbooks", "recent previous-year JEE papers"),
                    List.of(
                            "Prefer numerical or concept-application one-liners unless the topic naturally needs assertion/reason.",
                            "Distractors must be plausible calculation or concept mistakes.",
                            "Keep all options similar in length and specificity."
                    )
            );
        }

        if (exam.contains("neet")) {
            return new ExamPattern(
                    request.examName(),
                    "Medium",
                    "NCERT-focused factual and application MCQs",
                    "NEET style: high accuracy, NCERT wording, direct concepts, assertion-like distractors when common in the chapter.",
                    QuestionFormat.MIXED,
                    List.of("NCERT facts", "diagram-based ideas", "elimination", "previous-year factual traps"),
                    List.of("NTA NEET information bulletin", "NCERT textbooks", "recent previous-year NEET papers"),
                    List.of(
                            "Use one-liner factual/application MCQs mostly, with occasional statement/assertion style.",
                            "Do not create options that differ wildly in length.",
                            "Distractors should represent common NCERT misreadings."
                    )
            );
        }

        if (exam.contains("upsc") || exam.contains("civil")) {
            return new ExamPattern(
                    request.examName(),
                    "Hard",
                    "Analytical statements and current-context MCQs",
                    "UPSC style: conceptual breadth, statement analysis, current linkage, and balanced distractors.",
                    QuestionFormat.STATEMENT_BASED,
                    List.of("static concepts", "current linkage", "statement evaluation", "previous-year theme adaptation"),
                    List.of("UPSC syllabus", "NCERT textbooks", "Drishti IAS explainers", "recent previous-year UPSC papers"),
                    List.of(
                            "Prefer 'Consider the following statements' format.",
                            "Options should be combinations such as 1 only, 2 only, 1 and 2 only, or all/none.",
                            "Statements must be independently meaningful and close in difficulty."
                    )
            );
        }

        if (exam.contains("ssc") || exam.contains("bank") || exam.contains("railway")) {
            return new ExamPattern(
                    request.examName(),
                    "Easy to Medium",
                    "Speed-based objective questions",
                    "Competitive exam style: short questions, time pressure, direct scoring, and previous-year inspired patterns.",
                    QuestionFormat.ONE_LINER,
                    List.of("speed", "accuracy", "standard patterns", "repeated PYQ patterns"),
                    List.of("official exam syllabus/notification", "Testbook exam analysis", "recent previous-year papers"),
                    List.of(
                            "Use direct one-liner questions.",
                            "Avoid long statement blocks unless the real exam commonly uses them.",
                            "Options must be close enough that guessing by length is difficult."
                    )
            );
        }

        return new ExamPattern(
                request.examName(),
                "Adaptive",
                "Exam-oriented MCQs",
                "General adaptive pattern: questions are balanced across recall, concept, application, and previous-year style.",
                QuestionFormat.MIXED,
                List.of("core concepts", "exam relevance", "practical application", "previous-year theme adaptation"),
                List.of("official syllabus", "NCERT or standard textbooks", "reputed exam preparation analysis", "recent previous-year papers"),
                List.of(
                        "Infer whether the exam uses statement-based or one-liner questions and mirror that style.",
                        "Do not copy any previous-year question verbatim.",
                        "Keep all options nearly equal in length, grammar, and technical level."
                )
        );
    }
}
