package com.jdsu.quiz.platform;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StaticServiceRegistryTest {
    @Test
    void registersExtractableServiceBoundaries() {
        StaticServiceRegistry registry = new StaticServiceRegistry(
                "http://localhost:2000",
                "http://localhost:2000",
                "http://localhost:2000",
                "http://localhost:2000",
                "http://localhost:2000",
                "http://localhost:2000",
                "http://localhost:2000"
        );

        assertThat(registry.services()).hasSize(7);
        assertThat(registry.resolve("ai-service")).isPresent();
        assertThat(registry.resolve("payment-service").orElseThrow().localModule()).isTrue();
    }
}
