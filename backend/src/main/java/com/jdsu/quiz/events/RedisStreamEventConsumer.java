package com.jdsu.quiz.events;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisStreamEventConsumer {
    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final String streamKey;

    public RedisStreamEventConsumer(
            ObjectProvider<StringRedisTemplate> redisTemplate,
            JdbcTemplate jdbcTemplate,
            @Value("${app.events.stream-key:jagdisu.events}") String streamKey
    ) {
        this.redisTemplate = redisTemplate.getIfAvailable();
        this.jdbcTemplate = jdbcTemplate;
        this.streamKey = streamKey;
    }

    @Scheduled(fixedDelayString = "${app.events.consumer-delay-ms:10000}")
    public void consumeRecentEvents() {
        if (redisTemplate == null) {
            return;
        }
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().range(streamKey, Range.unbounded());
            if (records == null) {
                return;
            }
            records.stream().limit(100).forEach(record -> jdbcTemplate.update(
                    """
                    INSERT INTO event_consumption_log (stream_id, event_type, consumed_at)
                    VALUES (?, ?, CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE consumed_at = VALUES(consumed_at)
                    """,
                    record.getId().getValue(),
                    String.valueOf(record.getValue().get("eventType"))
            ));
        } catch (Exception ignored) {
            // Redis event consumption is best-effort for this modular runtime.
        }
    }
}
