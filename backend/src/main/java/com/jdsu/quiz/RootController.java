package com.jdsu.quiz;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/")
public class RootController {
    @GetMapping
    public RootStatus rootStatus() {
        return new RootStatus(
                "JDSU AI Exam Quiz Backend",
                "Running",
                "Backend API is available at /api/ai-model/status and /api/quizzes/generate",
                List.of(
                        "/api/ai-model/status",
                        "/api/quizzes/generate"
                )
        );
    }

    public record RootStatus(
            String service,
            String status,
            String message,
            List<String> endpoints
    ) {
    }
}
