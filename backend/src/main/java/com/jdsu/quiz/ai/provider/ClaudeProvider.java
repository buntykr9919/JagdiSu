package com.jdsu.quiz.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClaudeProvider extends OpenAiCompatibleProvider {
    public ClaudeProvider(
            ObjectMapper objectMapper,
            @Value("${app.ai.providers.claude.base-url:https://api.anthropic.com/v1}") String baseUrl,
            @Value("${app.ai.providers.claude.api-key:}") String apiKey
    ) {
        super(objectMapper, baseUrl, apiKey);
    }

    @Override
    public String providerName() {
        return "CLAUDE";
    }
}
