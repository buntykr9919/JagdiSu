package com.jdsu.quiz.notes;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/notes")
public class NotesEvaluationController {
    private final NotesEvaluationService notesEvaluationService;

    public NotesEvaluationController(NotesEvaluationService notesEvaluationService) {
        this.notesEvaluationService = notesEvaluationService;
    }

    @PostMapping("/evaluate")
    public NotesEvaluationResponse evaluate(
            @RequestParam String examName,
            @RequestParam(defaultValue = "") String questionPaperText,
            @RequestParam(defaultValue = "") String answerText,
            @RequestParam(defaultValue = "English") String language,
            @RequestParam(required = false) MultipartFile file
    ) {
        return notesEvaluationService.evaluate(examName, questionPaperText, answerText, language, file);
    }

    public record NotesEvaluationResponse(
            String examName,
            String extractedText,
            List<String> mistakes,
            List<String> strengths,
            int score,
            int maxScore,
            String feedback
    ) {
    }
}
