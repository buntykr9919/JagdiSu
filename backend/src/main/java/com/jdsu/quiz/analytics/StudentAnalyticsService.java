package com.jdsu.quiz.analytics;

import com.jdsu.quiz.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
public class StudentAnalyticsService {
    private final JdbcTemplate jdbcTemplate;

    public StudentAnalyticsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public SessionResponse recordSession(JwtService.JwtPrincipal principal, SessionRequest request) {
        long userId = userId(principal);
        int duration = Math.max(1, request.durationSeconds());
        String subject = fallback(request.subject(), "General");
        String topic = fallback(request.topic(), "General");
        jdbcTemplate.update(
                """
                INSERT INTO learning_sessions (user_id, subject, topic, activity_type, duration_seconds, completed_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                userId,
                subject,
                topic,
                fallback(request.activityType(), "STUDY"),
                duration
        );
        jdbcTemplate.update(
                """
                INSERT INTO topic_mastery (user_id, subject, topic, mastery_percent, attempts, last_activity_at)
                VALUES (?, ?, ?, ?, 1, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                  mastery_percent = LEAST(100, ROUND((mastery_percent * attempts + ?) / (attempts + 1), 2)),
                  attempts = attempts + 1,
                  last_activity_at = CURRENT_TIMESTAMP
                """,
                userId,
                subject,
                topic,
                Math.max(0, Math.min(100, request.masteryDelta())),
                Math.max(0, Math.min(100, request.masteryDelta()))
        );
        upsertStreak(userId);
        return new SessionResponse("RECORDED", duration);
    }

    public InsightsResponse insights(JwtService.JwtPrincipal principal) {
        long userId = userId(principal);
        Integer minutes = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(duration_seconds), 0) / 60 FROM learning_sessions WHERE user_id = ? AND completed_at >= CURRENT_DATE - INTERVAL 7 DAY",
                Integer.class,
                userId
        );
        Integer streak = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(current_streak_days), 0) FROM study_streaks WHERE user_id = ?",
                Integer.class,
                userId
        );
        List<TopicMetric> weakTopics = weakTopics(principal);
        List<String> recommendedTests = weakTopics.stream()
                .map(topic -> "Practice test: " + topic.subject() + " - " + topic.topic())
                .limit(5)
                .toList();
        return new InsightsResponse(minutes == null ? 0 : minutes, streak == null ? 0 : streak, weakTopics, recommendedTests);
    }

    public List<TopicMetric> weakTopics(JwtService.JwtPrincipal principal) {
        return jdbcTemplate.query(
                """
                SELECT subject, topic, mastery_percent
                FROM topic_mastery
                WHERE user_id = ?
                ORDER BY mastery_percent ASC, last_activity_at DESC
                LIMIT 10
                """,
                (rs, rowNum) -> new TopicMetric(rs.getString("subject"), rs.getString("topic"), rs.getDouble("mastery_percent")),
                userId(principal)
        );
    }

    public List<String> recommendedTests(JwtService.JwtPrincipal principal) {
        return insights(principal).recommendedTests();
    }

    public List<RevisionItem> revisionPlanner(JwtService.JwtPrincipal principal) {
        long userId = userId(principal);
        List<RevisionItem> items = jdbcTemplate.query(
                """
                SELECT topic, subject, next_revision_on
                FROM revision_history
                WHERE user_id = ? AND next_revision_on >= CURRENT_DATE
                ORDER BY next_revision_on ASC
                LIMIT 20
                """,
                (rs, rowNum) -> new RevisionItem(rs.getString("subject"), rs.getString("topic"), rs.getDate("next_revision_on").toLocalDate().toString()),
                userId
        );
        if (!items.isEmpty()) {
            return items;
        }
        return weakTopics(principal).stream()
                .map(topic -> new RevisionItem(topic.subject(), topic.topic(), LocalDate.now().plusDays(1).toString()))
                .toList();
    }

    private void upsertStreak(long userId) {
        jdbcTemplate.update(
                """
                INSERT INTO study_streaks (user_id, streak_date, current_streak_days, longest_streak_days)
                VALUES (?, CURRENT_DATE, 1, 1)
                ON DUPLICATE KEY UPDATE
                  current_streak_days = current_streak_days + 1,
                  longest_streak_days = GREATEST(longest_streak_days, current_streak_days + 1),
                  updated_at = CURRENT_TIMESTAMP
                """,
                userId
        );
    }

    private long userId(JwtService.JwtPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required.");
        }
        return principal.userId();
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record SessionRequest(String subject, String topic, String activityType, int durationSeconds, int masteryDelta) {
    }

    public record SessionResponse(String status, int durationSeconds) {
    }

    public record InsightsResponse(int weeklyStudyMinutes, int currentStreakDays, List<TopicMetric> weakTopics, List<String> recommendedTests) {
    }

    public record TopicMetric(String subject, String topic, double masteryPercent) {
    }

    public record RevisionItem(String subject, String topic, String nextRevisionOn) {
    }
}
