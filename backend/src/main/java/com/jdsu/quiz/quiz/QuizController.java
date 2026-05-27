package com.jdsu.quiz.quiz;

import com.jdsu.quiz.quiz.dto.QuizGenerateRequest;
import com.jdsu.quiz.quiz.dto.QuizResponse;
import com.jdsu.quiz.quiz.service.QuizGenerationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quizzes")
public class QuizController {
    private final QuizGenerationService quizGenerationService;

    public QuizController(QuizGenerationService quizGenerationService) {
        this.quizGenerationService = quizGenerationService;
    }

    @PostMapping("/generate")
    public QuizResponse generate(@Valid @RequestBody QuizGenerateRequest request) {
        return quizGenerationService.generate(request);
    }
}

