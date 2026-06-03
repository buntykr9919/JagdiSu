package com.jdsu.quiz.notes;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.jdsu.quiz.quiz.dto.QuizResponse;

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

    @PostMapping("/extract")
    public NotesExtractionResponse extract(
            @RequestParam(defaultValue = "English") String language,
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) MultipartFile questionFile,
            @RequestParam(required = false) MultipartFile answerFile
    ) {
        return notesEvaluationService.extract(language, file, questionFile, answerFile);
    }

    @PostMapping("/quiz")
    public QuizResponse generateNotesQuiz(
            @RequestParam(defaultValue = "English") String language,
            @RequestParam(defaultValue = "5") int numberOfQuestions,
            @RequestParam(defaultValue = "20") int totalQuestions,
            @RequestParam(defaultValue = "1") int batchNumber,
            @RequestParam(required = false) List<String> previousQuestionSummaries,
            @RequestParam("files") List<MultipartFile> files
    ) {
        return notesEvaluationService.generateNotesQuiz(
                language,
                numberOfQuestions,
                totalQuestions,
                batchNumber,
                previousQuestionSummaries,
                files
        );
    }

    public record NotesEvaluationResponse(
            String examName,
            String extractedText,
            List<String> mistakes,
            List<String> strengths,
            List<String> weaknesses,
            List<String> improvements,
            int score,
            int maxScore,
            String feedback,
            String idealAnswer
    ) {
    }

    public record NotesExtractionResponse(
            String questionText,
            String answerText
    ) {
    }
}
