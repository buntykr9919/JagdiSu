package com.jdsu.quiz.platform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class StaticServiceRegistry implements ServiceRegistry {
    private final List<ServiceDescriptor> services;

    public StaticServiceRegistry(
            @Value("${app.services.auth-url:http://localhost:2000}") String authUrl,
            @Value("${app.services.user-url:http://localhost:2000}") String userUrl,
            @Value("${app.services.ai-url:http://localhost:2000}") String aiUrl,
            @Value("${app.services.exam-url:http://localhost:2000}") String examUrl,
            @Value("${app.services.payment-url:http://localhost:2000}") String paymentUrl,
            @Value("${app.services.notification-url:http://localhost:2000}") String notificationUrl,
            @Value("${app.services.community-url:http://localhost:2000}") String communityUrl
    ) {
        this.services = List.of(
                descriptor("auth-service", "Auth Service", authUrl),
                descriptor("user-service", "User Service", userUrl),
                descriptor("ai-service", "AI Service", aiUrl),
                descriptor("exam-service", "Exam Service", examUrl),
                descriptor("payment-service", "Payment Service", paymentUrl),
                descriptor("notification-service", "Notification Service", notificationUrl),
                descriptor("community-service", "Community Service", communityUrl)
        );
    }

    @Override
    public List<ServiceDescriptor> services() {
        return services;
    }

    @Override
    public Optional<ServiceDescriptor> resolve(String serviceId) {
        String normalized = normalize(serviceId);
        return services.stream().filter(service -> normalize(service.id()).equals(normalized)).findFirst();
    }

    private ServiceDescriptor descriptor(String id, String name, String baseUrl) {
        return new ServiceDescriptor(id, name, URI.create(baseUrl), "/actuator/health", isLocal(baseUrl));
    }

    private boolean isLocal(String baseUrl) {
        return baseUrl == null || baseUrl.contains("localhost:2000") || baseUrl.contains("127.0.0.1:2000");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
