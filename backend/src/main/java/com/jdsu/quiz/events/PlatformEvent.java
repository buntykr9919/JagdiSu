package com.jdsu.quiz.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PlatformEvent(String eventId, String eventType, Instant occurredAt, Map<String, Object> payload) implements DomainEvent {
    public static PlatformEvent of(String eventType, Map<String, Object> payload) {
        return new PlatformEvent(UUID.randomUUID().toString(), eventType, Instant.now(), payload == null ? Map.of() : payload);
    }
}
