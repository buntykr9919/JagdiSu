package com.jdsu.quiz.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OpenAiProvider extends OpenAiCompatibleProvider {
    public OpenAiProvider(
            ObjectMapper objectMapper,
            @Value("${app.ai.providers.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.ai.providers.openai.api-key:}") String apiKey
    ) {
        super(objectMapper, baseUrl, apiKey);
    }

    @Override
    public String providerName() {
        return "OPENAI";
    }
}
