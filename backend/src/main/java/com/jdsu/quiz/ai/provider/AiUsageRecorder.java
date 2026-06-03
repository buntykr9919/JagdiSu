package com.jdsu.quiz.ai.provider;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AiUsageRecorder {
    private final JdbcTemplate jdbcTemplate;

    public AiUsageRecorder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(String feature, AiProvider.AiProviderResponse response) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ai_requests
                      (provider, model_name, feature, prompt_tokens, completion_tokens, latency_ms, estimated_cost_usd, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'SUCCEEDED')
                    """,
                    response.provider(),
                    response.model(),
                    feature,
                    response.promptTokens(),
                    response.completionTokens(),
                    response.latencyMs(),
                    response.estimatedCostUsd()
            );
            jdbcTemplate.update(
                    """
                    INSERT INTO ai_cost_tracking
                      (provider, model_name, feature, prompt_tokens, completion_tokens, cost_usd)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    response.provider(),
                    response.model(),
                    feature,
                    response.promptTokens(),
                    response.completionTokens(),
                    response.estimatedCostUsd()
            );
        } catch (Exception ignored) {
            // AI responses should not fail only because analytics persistence is unavailable.
        }
    }

    public void recordFailure(String feature, String provider, String model, String message) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ai_requests
                      (provider, model_name, feature, status, error_message)
                    VALUES (?, ?, ?, 'FAILED', ?)
                    """,
                    provider,
                    model,
                    feature,
                    message == null ? "Unknown AI provider failure." : message.substring(0, Math.min(500, message.length()))
            );
        } catch (Exception ignored) {
            // Ignore analytics failures.
        }
    }
}
