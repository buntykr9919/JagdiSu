package com.jdsu.quiz.ai.provider;

import io.micrometer.observation.annotation.Observed;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class AiProviderRouter {
    private final List<AiProvider> providers;
    private final AiUsageRecorder usageRecorder;
    private final AiBudgetService aiBudgetService;
    private final StringRedisTemplate redisTemplate;
    private final String preferredProvider;
    private final String failoverOrder;
    private final String defaultModel;
    private final long timeoutSeconds;
    private final int failureThreshold;
    private final int coolDownSeconds;

    public AiProviderRouter(
            List<AiProvider> providers,
            AiUsageRecorder usageRecorder,
            AiBudgetService aiBudgetService,
            ObjectProvider<StringRedisTemplate> redisTemplate,
            @Value("${app.ai.provider:OPENROUTER}") String preferredProvider,
            @Value("${app.ai.failover-order:OPENROUTER,OPENAI,GEMINI,DEEPSEEK,CLAUDE}") String failoverOrder,
            @Value("${app.ai.model}") String defaultModel,
            @Value("${app.ai.timeout-seconds}") long timeoutSeconds,
            @Value("${app.ai.circuit-breaker.failure-threshold:3}") int failureThreshold,
            @Value("${app.ai.circuit-breaker.cool-down-seconds:60}") int coolDownSeconds
    ) {
        this.providers = providers;
        this.usageRecorder = usageRecorder;
        this.aiBudgetService = aiBudgetService;
        this.redisTemplate = redisTemplate.getIfAvailable();
        this.preferredProvider = normalize(preferredProvider);
        this.failoverOrder = failoverOrder == null ? "" : failoverOrder;
        this.defaultModel = defaultModel;
        this.timeoutSeconds = Math.max(20L, timeoutSeconds);
        this.failureThreshold = Math.max(1, failureThreshold);
        this.coolDownSeconds = Math.max(1, coolDownSeconds);
    }

    public boolean hasConfiguredProvider() {
        return providers.stream().anyMatch(AiProvider::isConfigured);
    }

    public String chatCompletion(String feature, Map<String, Object> payload) {
        return chatCompletion(feature, defaultModel, payload).rawBody();
    }

    @Observed(name = "jagdisu.ai.provider.route")
    public AiProvider.AiProviderResponse chatCompletion(String feature, String model, Map<String, Object> payload) {
        List<AiProvider> candidates = orderedProviders();
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No AI provider is configured.");
        }

        RuntimeException lastFailure = null;
        String selectedModel = model == null || model.isBlank() ? defaultModel : model;
        aiBudgetService.enforce(feature);
        for (AiProvider provider : candidates) {
            if (isProviderCoolingDown(provider.providerName())) {
                continue;
            }
            try {
                AiProvider.AiProviderResponse response = provider.chatCompletion(
                        new AiProvider.AiProviderRequest(feature, selectedModel, payload, timeoutSeconds)
                );
                usageRecorder.record(feature, response);
                resetProviderFailures(provider.providerName());
                return response;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                usageRecorder.recordFailure(feature, provider.providerName(), selectedModel, exception.getMessage());
                registerProviderFailure(provider.providerName());
            }
        }

        throw lastFailure == null
                ? new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No AI provider is available.")
                : lastFailure;
    }

    private boolean isProviderCoolingDown(String providerName) {
        if (redisTemplate == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey("ai:provider:cooldown:" + normalize(providerName)));
        } catch (Exception ignored) {
            return false;
        }
    }

    private void registerProviderFailure(String providerName) {
        if (redisTemplate == null) {
            return;
        }
        try {
            String failureKey = "ai:provider:failures:" + normalize(providerName);
            Long failures = redisTemplate.opsForValue().increment(failureKey);
            redisTemplate.expire(failureKey, Duration.ofMinutes(10));
            if (failures != null && failures >= failureThreshold) {
                redisTemplate.opsForValue().set("ai:provider:cooldown:" + normalize(providerName), "OPEN", Duration.ofSeconds(coolDownSeconds));
            }
        } catch (Exception ignored) {
            // Redis outage should not block failover.
        }
    }

    private void resetProviderFailures(String providerName) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete("ai:provider:failures:" + normalize(providerName));
        } catch (Exception ignored) {
            // Ignore Redis cleanup failure.
        }
    }

    public List<String> configuredProviders() {
        return providers.stream()
                .filter(AiProvider::isConfigured)
                .map(AiProvider::providerName)
                .sorted()
                .toList();
    }

    private List<AiProvider> orderedProviders() {
        List<String> order = new ArrayList<>();
        if (!preferredProvider.isBlank()) {
            order.add(preferredProvider);
        }
        Arrays.stream(failoverOrder.split(","))
                .map(this::normalize)
                .filter(value -> !value.isBlank() && !order.contains(value))
                .forEach(order::add);

        return providers.stream()
                .filter(AiProvider::isConfigured)
                .sorted(Comparator.comparingInt(provider -> {
                    int index = order.indexOf(normalize(provider.providerName()));
                    return index < 0 ? Integer.MAX_VALUE : index;
                }))
                .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
