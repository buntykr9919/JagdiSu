package com.jdsu.quiz.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DeepSeekProvider extends OpenAiCompatibleProvider {
    public DeepSeekProvider(
            ObjectMapper objectMapper,
            @Value("${app.ai.providers.deepseek.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${app.ai.providers.deepseek.api-key:}") String apiKey
    ) {
        super(objectMapper, baseUrl, apiKey);
    }

    @Override
    public String providerName() {
        return "DEEPSEEK";
    }
}
