package com.jdsu.quiz.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SearchService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final boolean elasticEnabled;
    private final String indexPrefix;

    public SearchService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${app.elasticsearch.enabled:false}") boolean elasticEnabled,
            @Value("${app.elasticsearch.url:http://localhost:9200}") String elasticUrl,
            @Value("${app.elasticsearch.username:}") String username,
            @Value("${app.elasticsearch.password:}") String password,
            @Value("${app.elasticsearch.index-prefix:jagdisu}") String indexPrefix
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.elasticEnabled = elasticEnabled;
        this.indexPrefix = indexPrefix == null || indexPrefix.isBlank() ? "jagdisu" : indexPrefix.trim();
        WebClient.Builder builder = WebClient.builder().baseUrl(elasticUrl);
        if (username != null && !username.isBlank()) {
            builder.defaultHeaders(headers -> headers.setBasicAuth(username, password == null ? "" : password));
        }
        this.webClient = builder.defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json").build();
    }

    public SearchResponse search(String type, String query, int limit) {
        String safeQuery = query == null ? "" : query.trim();
        int safeLimit = Math.max(1, Math.min(50, limit));
        if (elasticEnabled && !safeQuery.isBlank()) {
            List<SearchResult> elasticResults = elasticSearch(type, safeQuery, safeLimit);
            if (!elasticResults.isEmpty()) {
                return new SearchResponse(type, safeQuery, "ELASTICSEARCH", elasticResults);
            }
        }
        return new SearchResponse(type, safeQuery, "MYSQL_FALLBACK", mysqlSearch(type, safeQuery, safeLimit));
    }

    private List<SearchResult> elasticSearch(String type, String query, int limit) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "size", limit,
                    "query", Map.of("multi_match", Map.of(
                            "query", query,
                            "fields", List.of("title^3", "name^2", "body", "summary", "description", "prompt", "topic", "subject")
                    ))
            ));
            String raw = webClient.post()
                    .uri("/{index}/_search", indexPrefix + "-" + type)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode hits = objectMapper.readTree(raw).path("hits").path("hits");
            List<SearchResult> results = new ArrayList<>();
            for (JsonNode hit : hits) {
                JsonNode source = hit.path("_source");
                results.add(new SearchResult(
                        hit.path("_id").asText(),
                        text(source, "title", text(source, "name", text(source, "prompt", "Untitled"))),
                        text(source, "summary", text(source, "description", text(source, "body", ""))),
                        type,
                        hit.path("_score").asDouble(0)
                ));
            }
            return results;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<SearchResult> mysqlSearch(String type, String query, int limit) {
        String like = "%" + query + "%";
        try {
            return switch (type) {
                case "questions" -> jdbcTemplate.query(
                        """
                        SELECT id, prompt AS title, COALESCE(explanation, subject) AS snippet
                        FROM questions
                        WHERE prompt LIKE ? OR subject LIKE ? OR topic LIKE ?
                        ORDER BY created_at DESC
                        LIMIT ?
                        """,
                        (rs, rowNum) -> row(rs.getString("id"), rs.getString("title"), rs.getString("snippet"), type),
                        like, like, like, limit
                );
                case "courses" -> jdbcTemplate.query(
                        """
                        SELECT id, title, COALESCE(description, status) AS snippet
                        FROM courses
                        WHERE title LIKE ? OR description LIKE ?
                        ORDER BY created_at DESC
                        LIMIT ?
                        """,
                        (rs, rowNum) -> row(rs.getString("id"), rs.getString("title"), rs.getString("snippet"), type),
                        like, like, limit
                );
                case "community" -> jdbcTemplate.query(
                        """
                        SELECT id, title, body AS snippet
                        FROM community_questions
                        WHERE title LIKE ? OR body LIKE ? OR subject LIKE ? OR topic LIKE ?
                        ORDER BY created_at DESC
                        LIMIT ?
                        """,
                        (rs, rowNum) -> row(rs.getString("id"), rs.getString("title"), rs.getString("snippet"), type),
                        like, like, like, like, limit
                );
                case "current-affairs" -> jdbcTemplate.query(
                        """
                        SELECT id, title, summary AS snippet
                        FROM current_affairs
                        WHERE title LIKE ? OR summary LIKE ? OR exam_relevance LIKE ?
                        ORDER BY published_at DESC, created_at DESC
                        LIMIT ?
                        """,
                        (rs, rowNum) -> row(rs.getString("id"), rs.getString("title"), rs.getString("snippet"), type),
                        like, like, like, limit
                );
                default -> jdbcTemplate.query(
                        """
                        SELECT id, deck_title AS title, CONCAT(front, ' ', back) AS snippet
                        FROM flashcards
                        WHERE deck_title LIKE ? OR front LIKE ? OR back LIKE ?
                        ORDER BY created_at DESC
                        LIMIT ?
                        """,
                        (rs, rowNum) -> row(rs.getString("id"), rs.getString("title"), rs.getString("snippet"), "notes"),
                        like, like, like, limit
                );
            };
        } catch (DataAccessException ignored) {
            return List.of();
        }
    }

    private SearchResult row(String id, String title, String snippet, String type) {
        return new SearchResult(id, title, snippet == null ? "" : snippet, type, 0);
    }

    private String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("");
        return value.isBlank() ? fallback : value;
    }

    public record SearchResponse(String type, String query, String source, List<SearchResult> results) {
    }

    public record SearchResult(String id, String title, String snippet, String type, double score) {
    }
}
