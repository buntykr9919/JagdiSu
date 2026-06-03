package com.jdsu.quiz.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {
    @Test
    void createsAndParsesSignedJwt() {
        JwtService jwtService = new JwtService(new ObjectMapper(), "test-secret-that-is-long-enough", 300);

        String token = jwtService.createAccessToken(42L, "student@example.com", List.of("STUDENT"));
        JwtService.JwtPrincipal principal = jwtService.parse(token);

        assertEquals(42L, principal.userId());
        assertEquals("student@example.com", principal.email());
        assertEquals(List.of("STUDENT"), principal.roles());
    }

    @Test
    void rejectsTamperedJwt() {
        JwtService jwtService = new JwtService(new ObjectMapper(), "test-secret-that-is-long-enough", 300);
        String token = jwtService.createAccessToken(42L, "student@example.com", List.of("STUDENT"));

        assertThrows(IllegalArgumentException.class, () -> jwtService.parse(token + "tampered"));
    }
}
