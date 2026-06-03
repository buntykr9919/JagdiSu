package com.jdsu.quiz.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AsyncJobService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String workerId = UUID.randomUUID().toString();

    public AsyncJobService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public JobResponse enqueue(String jobType, Map<String, Object> payload) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO async_jobs (job_type, payload_json) VALUES (?, CAST(? AS JSON))",
                    normalizeJobType(jobType),
                    objectMapper.writeValueAsString(payload == null ? Map.of() : payload)
            );
            Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            return new JobResponse(id == null ? 0 : id, normalizeJobType(jobType), "QUEUED", Instant.now().toString());
        } catch (Exception exception) {
            throw new IllegalStateException("Job enqueue failed.", exception);
        }
    }

    @Transactional
    public List<JobRecord> claim(int limit) {
        List<Long> ids = jdbcTemplate.query(
                """
                SELECT id FROM async_jobs
                WHERE status = 'QUEUED' AND available_at <= CURRENT_TIMESTAMP AND attempts < max_attempts
                ORDER BY available_at ASC, id ASC
                LIMIT ?
                """,
                (rs, rowNum) -> rs.getLong("id"),
                Math.max(1, Math.min(20, limit))
        );
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        Object[] params = ids.toArray();
        jdbcTemplate.update(
                "UPDATE async_jobs SET status = 'RUNNING', locked_at = CURRENT_TIMESTAMP, locked_by = '" + workerId + "', attempts = attempts + 1 WHERE id IN (" + placeholders + ")",
                params
        );
        return jdbcTemplate.query(
                "SELECT id, job_type, payload_json, attempts FROM async_jobs WHERE locked_by = ? AND status = 'RUNNING' ORDER BY id ASC",
                (rs, rowNum) -> new JobRecord(
                        rs.getLong("id"),
                        rs.getString("job_type"),
                        rs.getString("payload_json"),
                        rs.getInt("attempts")
                ),
                workerId
        );
    }

    public void complete(long id) {
        jdbcTemplate.update("UPDATE async_jobs SET status = 'SUCCEEDED', locked_by = NULL WHERE id = ?", id);
    }

    public void fail(long id, String message) {
        jdbcTemplate.update(
                """
                UPDATE async_jobs
                SET status = CASE WHEN attempts >= max_attempts THEN 'FAILED' ELSE 'QUEUED' END,
                    available_at = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL LEAST(300, POW(2, attempts) * 10) SECOND),
                    locked_by = NULL,
                    error_message = ?
                WHERE id = ?
                """,
                message == null ? "Unknown failure" : message.substring(0, Math.min(500, message.length())),
                id
        );
    }

    public Map<String, Integer> counts() {
        return jdbcTemplate.query(
                "SELECT status, COUNT(*) AS total FROM async_jobs GROUP BY status",
                rs -> {
                    java.util.HashMap<String, Integer> values = new java.util.HashMap<>();
                    while (rs.next()) {
                        values.put(rs.getString("status"), rs.getInt("total"));
                    }
                    return values;
                }
        );
    }

    private String normalizeJobType(String jobType) {
        return jobType == null || jobType.isBlank() ? "GENERIC" : jobType.trim().toUpperCase();
    }

    public record JobResponse(long id, String jobType, String status, String createdAt) {
    }

    public record JobRecord(long id, String jobType, String payloadJson, int attempts) {
    }
}
