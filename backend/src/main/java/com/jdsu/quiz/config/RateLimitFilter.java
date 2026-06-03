package com.jdsu.quiz.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private final boolean enabled;
    private final int requestsPerMinute;
    private final Clock clock;
    private final StringRedisTemplate redisTemplate;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.requests-per-minute:120}") int requestsPerMinute,
            ObjectProvider<StringRedisTemplate> redisTemplate
    ) {
        this.enabled = enabled;
        this.requestsPerMinute = Math.max(1, requestsPerMinute);
        this.clock = Clock.systemUTC();
        this.redisTemplate = redisTemplate.getIfAvailable();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled || !request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = clientKey(request);
        Integer redisCount = incrementRedisWindow(key);
        if (redisCount != null) {
            writeRateLimitHeaders(response, redisCount);
            if (redisCount > requestsPerMinute) {
                reject(response);
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        long now = clock.millis();
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || now - current.startedAtMs >= 60_000) {
                return new Window(now, 1);
            }
            return new Window(current.startedAtMs, current.count + 1);
        });

        writeRateLimitHeaders(response, window.count);

        if (window.count > requestsPerMinute) {
            reject(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Integer incrementRedisWindow(String clientKey) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            String key = "rate-limit:api:" + clientKey + ":" + (clock.millis() / 60_000);
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(70));
            }
            return count == null ? null : count.intValue();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeRateLimitHeaders(HttpServletResponse response, int count) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(requestsPerMinute));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, requestsPerMinute - count)));
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":429,\"message\":\"Too many requests. Please try again later.\"}");
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record Window(long startedAtMs, int count) {
    }
}
