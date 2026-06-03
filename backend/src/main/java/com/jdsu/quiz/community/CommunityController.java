package com.jdsu.quiz.community;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/community")
public class CommunityController {
    private final JdbcTemplate jdbcTemplate;

    public CommunityController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/questions")
    public List<QuestionResponse> questions() {
        List<QuestionRow> questions = jdbcTemplate.query(
                """
                        SELECT q.id, q.user_id, q.user_name, q.title, q.body, q.exam_type, q.subject, q.topic, q.created_at,
                               COUNT(a.id) AS answer_count
                        FROM community_questions q
                        LEFT JOIN community_answers a ON a.question_id = q.id
                        GROUP BY q.id, q.user_id, q.user_name, q.title, q.body, q.exam_type, q.subject, q.topic, q.created_at
                        ORDER BY q.created_at DESC
                        LIMIT 50
                        """,
                (rs, rowNum) -> new QuestionRow(
                        rs.getLong("id"),
                        rs.getObject("user_id", Long.class),
                        rs.getString("user_name"),
                        rs.getString("title"),
                        rs.getString("body"),
                        rs.getString("exam_type"),
                        rs.getString("subject"),
                        rs.getString("topic"),
                        rs.getObject("created_at", LocalDateTime.class),
                        rs.getInt("answer_count")
                )
        );

        return questions.stream()
                .map(question -> new QuestionResponse(
                        question.id(),
                        question.userId(),
                        question.userName(),
                        question.title(),
                        question.body(),
                        question.examType(),
                        question.subject(),
                        question.topic(),
                        question.createdAt(),
                        question.answerCount(),
                        answersForQuestion(question.id())
                ))
                .toList();
    }

    @PostMapping("/questions")
    public QuestionResponse createQuestion(@RequestBody QuestionRequest request) {
        String title = required(request.title(), "Question title is required.");
        String body = required(request.body(), "Question detail is required.");
        String userName = fallback(request.userName(), "Student");
        String examType = fallback(request.examType(), "General");
        String subject = fallback(request.subject(), "General");
        String topic = fallback(request.topic(), "");

        jdbcTemplate.update(
                """
                        INSERT INTO community_questions (user_id, user_name, title, body, exam_type, subject, topic)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                request.userId(),
                userName,
                title,
                body,
                examType,
                subject,
                topic
        );

        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return questionById(id == null ? 0L : id);
    }

    @PostMapping("/questions/{questionId}/answers")
    public AnswerResponse createAnswer(@PathVariable long questionId, @RequestBody AnswerRequest request) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM community_questions WHERE id = ?",
                Integer.class,
                questionId
        );
        if (exists == null || exists == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found.");
        }

        String body = required(request.body(), "Answer is required.");
        String userName = fallback(request.userName(), "Student");

        jdbcTemplate.update(
                "INSERT INTO community_answers (question_id, user_id, user_name, body) VALUES (?, ?, ?, ?)",
                questionId,
                request.userId(),
                userName,
                body
        );

        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return answerById(id == null ? 0L : id);
    }

    @PostMapping("/answers/{answerId}/like")
    public AnswerResponse likeAnswer(@PathVariable long answerId) {
        int updated = jdbcTemplate.update(
                "UPDATE community_answers SET upvotes = upvotes + 1 WHERE id = ?",
                answerId
        );
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Answer not found.");
        }
        return answerById(answerId);
    }

    private QuestionResponse questionById(long id) {
        QuestionRow question = jdbcTemplate.queryForObject(
                """
                        SELECT q.id, q.user_id, q.user_name, q.title, q.body, q.exam_type, q.subject, q.topic, q.created_at,
                               COUNT(a.id) AS answer_count
                        FROM community_questions q
                        LEFT JOIN community_answers a ON a.question_id = q.id
                        WHERE q.id = ?
                        GROUP BY q.id, q.user_id, q.user_name, q.title, q.body, q.exam_type, q.subject, q.topic, q.created_at
                        """,
                (rs, rowNum) -> new QuestionRow(
                        rs.getLong("id"),
                        rs.getObject("user_id", Long.class),
                        rs.getString("user_name"),
                        rs.getString("title"),
                        rs.getString("body"),
                        rs.getString("exam_type"),
                        rs.getString("subject"),
                        rs.getString("topic"),
                        rs.getObject("created_at", LocalDateTime.class),
                        rs.getInt("answer_count")
                ),
                id
        );

        return new QuestionResponse(
                question.id(),
                question.userId(),
                question.userName(),
                question.title(),
                question.body(),
                question.examType(),
                question.subject(),
                question.topic(),
                question.createdAt(),
                question.answerCount(),
                answersForQuestion(question.id())
        );
    }

    private List<AnswerResponse> answersForQuestion(long questionId) {
        return jdbcTemplate.query(
                """
                        SELECT id, question_id, user_id, user_name, body, upvotes, created_at
                        FROM community_answers
                        WHERE question_id = ?
                        ORDER BY upvotes DESC, created_at ASC
                        """,
                (rs, rowNum) -> new AnswerResponse(
                        rs.getLong("id"),
                        rs.getLong("question_id"),
                        rs.getObject("user_id", Long.class),
                        rs.getString("user_name"),
                        rs.getString("body"),
                        rs.getInt("upvotes"),
                        rs.getObject("created_at", LocalDateTime.class)
                ),
                questionId
        );
    }

    private AnswerResponse answerById(long id) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT id, question_id, user_id, user_name, body, upvotes, created_at
                        FROM community_answers
                        WHERE id = ?
                        """,
                (rs, rowNum) -> new AnswerResponse(
                        rs.getLong("id"),
                        rs.getLong("question_id"),
                        rs.getObject("user_id", Long.class),
                        rs.getString("user_name"),
                        rs.getString("body"),
                        rs.getInt("upvotes"),
                        rs.getObject("created_at", LocalDateTime.class)
                ),
                id
        );
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record QuestionRequest(
            Long userId,
            String userName,
            String title,
            String body,
            String examType,
            String subject,
            String topic
    ) {
    }

    public record AnswerRequest(Long userId, String userName, String body) {
    }

    public record QuestionResponse(
            Long id,
            Long userId,
            String userName,
            String title,
            String body,
            String examType,
            String subject,
            String topic,
            LocalDateTime createdAt,
            int answerCount,
            List<AnswerResponse> answers
    ) {
    }

    public record AnswerResponse(
            Long id,
            Long questionId,
            Long userId,
            String userName,
            String body,
            int upvotes,
            LocalDateTime createdAt
    ) {
    }

    private record QuestionRow(
            Long id,
            Long userId,
            String userName,
            String title,
            String body,
            String examType,
            String subject,
            String topic,
            LocalDateTime createdAt,
            int answerCount
    ) {
    }
}
