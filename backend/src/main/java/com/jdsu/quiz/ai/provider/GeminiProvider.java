package com.jdsu.quiz.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GeminiProvider extends OpenAiCompatibleProvider {
    public GeminiProvider(
            ObjectMapper objectMapper,
            @Value("${app.ai.providers.gemini.base-url:https://generativelanguage.googleapis.com/v1beta/openai}") String baseUrl,
            @Value("${app.ai.providers.gemini.api-key:}") String apiKey
    ) {
        super(objectMapper, baseUrl, apiKey);
    }

    @Override
    public String providerName() {
        return "GEMINI";
    }
}
