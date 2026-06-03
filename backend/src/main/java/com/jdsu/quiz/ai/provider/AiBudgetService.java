package com.jdsu.quiz.ai.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@Service
public class AiBudgetService {
    private final JdbcTemplate jdbcTemplate;
    private final BigDecimal dailyCostLimitUsd;
    private final int dailyTokenLimit;
    private final BigDecimal perFeatureCostLimitUsd;

    public AiBudgetService(
            JdbcTemplate jdbcTemplate,
            @Value("${app.ai.budget.daily-cost-usd:25}") BigDecimal dailyCostLimitUsd,
            @Value("${app.ai.budget.daily-token-limit:500000}") int dailyTokenLimit,
            @Value("${app.ai.budget.per-feature-cost-usd:10}") BigDecimal perFeatureCostLimitUsd
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.dailyCostLimitUsd = dailyCostLimitUsd;
        this.dailyTokenLimit = Math.max(1, dailyTokenLimit);
        this.perFeatureCostLimitUsd = perFeatureCostLimitUsd;
    }

    public void enforce(String feature) {
        BigDecimal dailyCost = valueOrZero(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(cost_usd), 0) FROM ai_cost_tracking WHERE created_at >= CURRENT_DATE",
                BigDecimal.class
        ));
        Integer dailyTokens = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(prompt_tokens + completion_tokens), 0) FROM ai_cost_tracking WHERE created_at >= CURRENT_DATE",
                Integer.class
        );
        BigDecimal featureCost = valueOrZero(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(cost_usd), 0) FROM ai_cost_tracking WHERE feature = ? AND created_at >= CURRENT_DATE",
                BigDecimal.class,
                feature
        ));
        if (dailyCost.compareTo(dailyCostLimitUsd) >= 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Daily AI cost budget has been reached.");
        }
        if ((dailyTokens == null ? 0 : dailyTokens) >= dailyTokenLimit) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Daily AI token budget has been reached.");
        }
        if (featureCost.compareTo(perFeatureCostLimitUsd) >= 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Daily AI feature budget has been reached.");
        }
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
