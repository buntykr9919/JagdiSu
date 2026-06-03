package com.jdsu.quiz.feedback;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {
    private final JdbcTemplate jdbcTemplate;

    public FeedbackController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping
    public FeedbackResponse submitFeedback(@Valid @RequestBody FeedbackRequest request) {
        jdbcTemplate.update(
                """
                INSERT INTO user_feedback (user_id, user_name, user_email, category, rating, message)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                request.userId(),
                trimToNull(request.userName()),
                normalizeEmail(request.userEmail()),
                request.category().trim(),
                request.rating(),
                request.message().trim()
        );

        return new FeedbackResponse("Feedback submitted successfully.");
    }

    private String normalizeEmail(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record FeedbackRequest(
            Long userId,
            String userName,
            String userEmail,
            @NotBlank String category,
            @Min(1) @Max(5) int rating,
            @NotBlank String message
    ) {
    }

    public record FeedbackResponse(String message) {
    }
}
