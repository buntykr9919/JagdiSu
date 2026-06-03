package com.jdsu.quiz.ai.provider;

import java.util.Map;

public interface AiProvider {
    String providerName();

    boolean isConfigured();

    AiProviderResponse chatCompletion(AiProviderRequest request);

    default boolean supports(String providerName) {
        return providerName() != null && providerName().equalsIgnoreCase(providerName);
    }

    record AiProviderRequest(String feature, String model, Map<String, Object> payload, long timeoutSeconds) {
    }

    record AiProviderResponse(
            String provider,
            String model,
            String rawBody,
            int promptTokens,
            int completionTokens,
            int latencyMs,
            double estimatedCostUsd
    ) {
    }
}
