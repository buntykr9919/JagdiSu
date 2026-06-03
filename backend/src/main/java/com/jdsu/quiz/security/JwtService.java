package com.jdsu.quiz.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {
    private final ObjectMapper objectMapper;
    private final String secret;
    private final long accessTokenTtlSeconds;

    public JwtService(
            ObjectMapper objectMapper,
            @Value("${app.security.jwt.secret:${SUPERADMIN_TOKEN:jagdisu-dev-secret-change-me}}") String secret,
            @Value("${app.security.jwt.access-token-ttl-seconds:900}") long accessTokenTtlSeconds
    ) {
        this.objectMapper = objectMapper;
        this.secret = secret == null || secret.isBlank() ? UUID.randomUUID().toString() : secret;
        this.accessTokenTtlSeconds = Math.max(60, accessTokenTtlSeconds);
    }

    public String createAccessToken(long userId, String email, List<String> roles) {
        Instant now = Instant.now();
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = Map.of(
                "sub", String.valueOf(userId),
                "email", email == null ? "" : email,
                "roles", roles == null ? List.of("STUDENT") : roles,
                "iat", now.getEpochSecond(),
                "exp", now.plusSeconds(accessTokenTtlSeconds).getEpochSecond(),
                "jti", UUID.randomUUID().toString()
        );
        String encodedHeader = encodeJson(header);
        String encodedPayload = encodeJson(payload);
        String signature = sign(encodedHeader + "." + encodedPayload);
        return encodedHeader + "." + encodedPayload + "." + signature;
    }

    public JwtPrincipal parse(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT format.");
            }
            String expectedSignature = sign(parts[0] + "." + parts[1]);
            if (!constantTimeEquals(expectedSignature, parts[2])) {
                throw new IllegalArgumentException("Invalid JWT signature.");
            }
            Map<String, Object> payload = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]),
                    new TypeReference<>() {
                    }
            );
            long exp = ((Number) payload.getOrDefault("exp", 0)).longValue();
            if (Instant.now().getEpochSecond() >= exp) {
                throw new IllegalArgumentException("JWT expired.");
            }
            long userId = Long.parseLong(String.valueOf(payload.get("sub")));
            String email = String.valueOf(payload.getOrDefault("email", ""));
            List<String> roles = objectMapper.convertValue(payload.getOrDefault("roles", List.of("STUDENT")), new TypeReference<>() {
            });
            return new JwtPrincipal(userId, email, roles);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid JWT.", exception);
        }
    }

    public long accessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new IllegalStateException("JWT serialization failed.", exception);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("JWT signing failed.", exception);
        }
    }

    private boolean constantTimeEquals(String first, String second) {
        return MessageDigestUtil.constantTimeEquals(first, second);
    }

    public record JwtPrincipal(long userId, String email, List<String> roles) {
    }
}
