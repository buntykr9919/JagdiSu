package com.jdsu.quiz.events;

public interface EventPublisher {
    void publish(DomainEvent event);
}
