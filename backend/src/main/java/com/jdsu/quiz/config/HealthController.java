package com.jdsu.quiz.config;

import com.jdsu.quiz.ai.provider.AiProviderRouter;
import com.jdsu.quiz.jobs.AsyncJobService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final AiProviderRouter aiProviderRouter;
    private final AsyncJobService asyncJobService;

    public HealthController(
            JdbcTemplate jdbcTemplate,
            ObjectProvider<StringRedisTemplate> redisTemplate,
            AiProviderRouter aiProviderRouter,
            AsyncJobService asyncJobService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate.getIfAvailable();
        this.aiProviderRouter = aiProviderRouter;
        this.asyncJobService = asyncJobService;
    }

    @GetMapping
    public ResponseEntity<HealthStatus> health() {
        String database = databaseStatus();
        String redis = redisStatus();
        List<String> aiProviders = aiProviderRouter.configuredProviders();
        Map<String, Integer> jobs = asyncJobService.counts();
        boolean healthy = "UP".equals(database);
        return ResponseEntity
                .status(healthy ? 200 : 503)
                .body(new HealthStatus("UP", database, redis, aiProviders, jobs, Instant.now().toString()));
    }

    private String databaseStatus() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1 ? "UP" : "DOWN";
        } catch (Exception ignored) {
            return "DOWN";
        }
    }

    private String redisStatus() {
        if (redisTemplate == null) {
            return "NOT_CONFIGURED";
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey("__health_probe__")) || redisTemplate.getConnectionFactory() != null
                    ? "UP"
                    : "DOWN";
        } catch (Exception ignored) {
            return "DOWN";
        }
    }

    public record HealthStatus(
            String backend,
            String database,
            String redis,
            List<String> aiProviders,
            Map<String, Integer> jobs,
            String timestamp
    ) {
    }
}
