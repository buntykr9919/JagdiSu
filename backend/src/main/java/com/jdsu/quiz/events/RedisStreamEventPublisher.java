package com.jdsu.quiz.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RedisStreamEventPublisher implements EventPublisher {
    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String streamKey;

    public RedisStreamEventPublisher(
            ObjectProvider<StringRedisTemplate> redisTemplate,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${app.events.stream-key:jagdisu.events}") String streamKey
    ) {
        this.redisTemplate = redisTemplate.getIfAvailable();
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.streamKey = streamKey;
    }

    @Override
    public void publish(DomainEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event.payload());
            if (redisTemplate != null) {
                MapRecord<String, String, String> record = StreamRecords
                        .mapBacked(Map.of(
                                "eventId", event.eventId(),
                                "eventType", event.eventType(),
                                "occurredAt", event.occurredAt().toString(),
                                "payload", payload
                        ))
                        .withStreamKey(streamKey);
                redisTemplate.opsForStream().add(record);
            }
            jdbcTemplate.update(
                    "INSERT INTO domain_events (event_id, event_type, payload_json) VALUES (?, ?, CAST(? AS JSON))",
                    event.eventId(),
                    event.eventType(),
                    payload
            );
        } catch (Exception ignored) {
            // Domain actions should not fail only because event dispatch is unavailable.
        }
    }
}
