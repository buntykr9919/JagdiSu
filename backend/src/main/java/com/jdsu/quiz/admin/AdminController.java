package com.jdsu.quiz.admin;

import com.jdsu.quiz.payment.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final JdbcTemplate jdbcTemplate;
    private final PaymentService paymentService;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminToken;

    public AdminController(
            JdbcTemplate jdbcTemplate,
            PaymentService paymentService,
            @Value("${app.admin.email}") String adminEmail,
            @Value("${app.admin.password}") String adminPassword,
            @Value("${app.admin.token}") String adminToken
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.paymentService = paymentService;
        this.adminEmail = normalize(adminEmail);
        this.adminPassword = adminPassword == null ? "" : adminPassword.trim();
        this.adminToken = adminToken == null ? "" : adminToken.trim();
    }

    @PostMapping("/login")
    public AdminLoginResponse login(@RequestBody AdminLoginRequest request) {
        String email = normalize(request.email());
        String password = request.password() == null ? "" : request.password().trim();
        if (!adminEmail.equals(email) || !adminPassword.equals(password)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid superadmin credentials.");
        }

        return new AdminLoginResponse("SUPERADMIN", adminEmail, adminToken);
    }

    @GetMapping("/dashboard")
    public AdminDashboardResponse dashboard(@RequestHeader("X-Admin-Token") String token) {
        requireAdmin(token);

        int totalUsers = count("SELECT COUNT(*) FROM users");
        int freeUsers = count("SELECT COUNT(*) FROM users WHERE plan = 'FREE'");
        int proUsers = count("SELECT COUNT(*) FROM users WHERE plan = 'PRO'");
        int advancedUsers = count("SELECT COUNT(*) FROM users WHERE plan = 'ADVANCED'");
        int totalFeedback = count("SELECT COUNT(*) FROM user_feedback");
        int newFeedback = count("SELECT COUNT(*) FROM user_feedback WHERE status = 'NEW'");
        double averageRating = averageRating();

        List<UserSummary> recentUsers = jdbcTemplate.query(
                """
                SELECT id, name, email, plan, created_at
                FROM users
                ORDER BY created_at DESC
                LIMIT 8
                """,
                (rs, rowNum) -> new UserSummary(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("plan"),
                        toInstant(rs.getTimestamp("created_at"))
                )
        );

        List<FeedbackSummary> feedback = jdbcTemplate.query(
                """
                SELECT id, user_name, user_email, category, rating, message, status, created_at
                FROM user_feedback
                ORDER BY created_at DESC
                LIMIT 20
                """,
                (rs, rowNum) -> new FeedbackSummary(
                        rs.getLong("id"),
                        rs.getString("user_name"),
                        rs.getString("user_email"),
                        rs.getString("category"),
                        rs.getInt("rating"),
                        rs.getString("message"),
                        rs.getString("status"),
                        toInstant(rs.getTimestamp("created_at"))
                )
        );

        return new AdminDashboardResponse(
                new AdminMetrics(totalUsers, freeUsers, proUsers, advancedUsers, totalFeedback, newFeedback, averageRating),
                recentUsers,
                feedback
        );
    }

    @PatchMapping("/feedback/{id}/status")
    public FeedbackStatusResponse updateFeedbackStatus(
            @RequestHeader("X-Admin-Token") String token,
            @PathVariable long id,
            @RequestBody FeedbackStatusRequest request
    ) {
        requireAdmin(token);
        String status = request.status() == null ? "" : request.status().trim().toUpperCase();
        if (!List.of("NEW", "REVIEWING", "RESOLVED").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported feedback status.");
        }

        int updated = jdbcTemplate.update("UPDATE user_feedback SET status = ? WHERE id = ?", status, id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found.");
        }

        return new FeedbackStatusResponse(id, status);
    }

    @GetMapping("/ai-cost")
    public List<Map<String, Object>> aiCost(@RequestHeader("X-Admin-Token") String token) {
        requireAdmin(token);
        return jdbcTemplate.queryForList(
                """
                SELECT provider, model_name, feature,
                       ROUND(SUM(cost_usd), 6) AS costUsd,
                       SUM(prompt_tokens + completion_tokens) AS tokens
                FROM ai_cost_tracking
                WHERE created_at >= CURRENT_DATE - INTERVAL 30 DAY
                GROUP BY provider, model_name, feature
                ORDER BY costUsd DESC
                LIMIT 50
                """
        );
    }

    @GetMapping("/provider-usage")
    public List<Map<String, Object>> providerUsage(@RequestHeader("X-Admin-Token") String token) {
        requireAdmin(token);
        return jdbcTemplate.queryForList(
                """
                SELECT provider, model_name, status, COUNT(*) AS requests,
                       ROUND(AVG(latency_ms), 0) AS avgLatencyMs
                FROM ai_requests
                WHERE created_at >= CURRENT_DATE - INTERVAL 7 DAY
                GROUP BY provider, model_name, status
                ORDER BY requests DESC
                LIMIT 50
                """
        );
    }

    @GetMapping("/provider-failures")
    public List<Map<String, Object>> providerFailures(@RequestHeader("X-Admin-Token") String token) {
        requireAdmin(token);
        return jdbcTemplate.queryForList(
                """
                SELECT provider, model_name, feature, COUNT(*) AS failures, MAX(error_message) AS lastError
                FROM ai_requests
                WHERE status = 'FAILED' AND created_at >= CURRENT_DATE - INTERVAL 7 DAY
                GROUP BY provider, model_name, feature
                ORDER BY failures DESC
                LIMIT 50
                """
        );
    }

    @GetMapping("/system-health")
    public Map<String, Object> systemHealth(@RequestHeader("X-Admin-Token") String token) {
        requireAdmin(token);
        return Map.of(
                "users", count("SELECT COUNT(*) FROM users"),
                "activeSessions", count("SELECT COUNT(*) FROM auth_sessions WHERE revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP"),
                "queuedJobs", count("SELECT COUNT(*) FROM async_jobs WHERE status = 'QUEUED'"),
                "queuedSearchDocuments", count("SELECT COUNT(*) FROM search_index_queue WHERE status = 'QUEUED'"),
                "pendingNotifications", count("SELECT COUNT(*) FROM notifications WHERE status = 'PENDING'"),
                "recentEvents", count("SELECT COUNT(*) FROM domain_events WHERE published_at >= CURRENT_TIMESTAMP - INTERVAL 1 HOUR")
        );
    }

    @GetMapping("/queue")
    public List<Map<String, Object>> queueMonitoring(@RequestHeader("X-Admin-Token") String token) {
        requireAdmin(token);
        return jdbcTemplate.queryForList(
                """
                SELECT job_type, status, COUNT(*) AS jobs, MIN(created_at) AS oldestCreatedAt
                FROM async_jobs
                GROUP BY job_type, status
                UNION ALL
                SELECT CONCAT('search:', entity_type) AS job_type, status, COUNT(*) AS jobs, MIN(created_at) AS oldestCreatedAt
                FROM search_index_queue
                GROUP BY entity_type, status
                """
        );
    }

    @GetMapping("/payments")
    public List<Map<String, Object>> paymentMonitoring(@RequestHeader("X-Admin-Token") String token) {
        requireAdmin(token);
        return jdbcTemplate.queryForList(
                """
                SELECT provider, status, currency, COUNT(*) AS payments, ROUND(SUM(amount), 2) AS revenue
                FROM payments
                WHERE created_at >= CURRENT_DATE - INTERVAL 30 DAY
                GROUP BY provider, status, currency
                ORDER BY revenue DESC
                """
        );
    }

    @GetMapping("/billing-summary")
    public PaymentService.AdminBillingSummary billingSummary(@RequestHeader("X-Admin-Token") String token) {
        requireAdmin(token);
        return paymentService.adminBillingSummary();
    }

    private void requireAdmin(String token) {
        if (token == null || !adminToken.equals(token.trim())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Superadmin access is required.");
        }
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private double averageRating() {
        Double value = jdbcTemplate.queryForObject("SELECT AVG(rating) FROM user_feedback", Double.class);
        return value == null ? 0 : Math.round(value * 10.0) / 10.0;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.now().toString() : timestamp.toInstant().toString();
    }

    public record AdminLoginRequest(String email, String password) {
    }

    public record AdminLoginResponse(String role, String email, String token) {
    }

    public record AdminDashboardResponse(AdminMetrics metrics, List<UserSummary> recentUsers, List<FeedbackSummary> feedback) {
    }

    public record AdminMetrics(
            int totalUsers,
            int freeUsers,
            int proUsers,
            int advancedUsers,
            int totalFeedback,
            int newFeedback,
            double averageRating
    ) {
    }

    public record UserSummary(Long id, String name, String email, String plan, String createdAt) {
    }

    public record FeedbackSummary(
            Long id,
            String userName,
            String userEmail,
            String category,
            int rating,
            String message,
            String status,
            String createdAt
    ) {
    }

    public record FeedbackStatusRequest(String status) {
    }

    public record FeedbackStatusResponse(long id, String status) {
    }
}
