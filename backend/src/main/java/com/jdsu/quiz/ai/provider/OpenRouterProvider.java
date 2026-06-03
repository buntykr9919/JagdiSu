package com.jdsu.quiz.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jdsu.quiz.config.DynamicAiConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenRouterProvider extends OpenAiCompatibleProvider {
    public OpenRouterProvider(
            ObjectMapper objectMapper,
            DynamicAiConfig dynamicAiConfig,
            @Value("${app.ai.base-url:https://openrouter.ai/api/v1}") String legacyBaseUrl,
            @Value("${app.ai.providers.openrouter.base-url:}") String baseUrl,
            @Value("${app.ai.providers.openrouter.api-key:}") String apiKey
    ) {
        super(
                objectMapper,
                baseUrl == null || baseUrl.isBlank() ? legacyBaseUrl : baseUrl,
                apiKey == null || apiKey.isBlank() ? dynamicAiConfig.apiKey() : apiKey
        );
    }

    @Override
    public String providerName() {
        return "OPENROUTER";
    }
}
