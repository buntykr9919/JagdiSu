package com.jdsu.quiz.ai.provider;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiBudgetServiceTest {
    @Test
    void rejectsWhenDailyCostLimitReached() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(eq("SELECT COALESCE(SUM(cost_usd), 0) FROM ai_cost_tracking WHERE created_at >= CURRENT_DATE"), eq(BigDecimal.class)))
                .thenReturn(new BigDecimal("25.00"));
        when(jdbcTemplate.queryForObject(eq("SELECT COALESCE(SUM(prompt_tokens + completion_tokens), 0) FROM ai_cost_tracking WHERE created_at >= CURRENT_DATE"), eq(Integer.class)))
                .thenReturn(100);
        when(jdbcTemplate.queryForObject(any(String.class), eq(BigDecimal.class), any()))
                .thenReturn(BigDecimal.ZERO);

        AiBudgetService service = new AiBudgetService(jdbcTemplate, new BigDecimal("25.00"), 500000, new BigDecimal("10.00"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.enforce("QUIZ"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatusCode());
    }
}
