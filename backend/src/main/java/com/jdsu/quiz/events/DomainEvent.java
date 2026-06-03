package com.jdsu.quiz.events;

import java.time.Instant;
import java.util.Map;

public interface DomainEvent {
    String eventId();

    String eventType();

    Instant occurredAt();

    Map<String, Object> payload();
}
