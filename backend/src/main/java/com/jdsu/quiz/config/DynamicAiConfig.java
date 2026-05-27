package com.jdsu.quiz.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Component
public class DynamicAiConfig {
    private final String fallbackApiKey;

    public DynamicAiConfig(@Value("${app.ai.api-key}") String fallbackApiKey) {
        this.fallbackApiKey = fallbackApiKey;
    }

    public String apiKey() {
        return readEnvValue("OPENROUTER_API_KEY")
                .or(() -> readEnvValue("OPENAI_API_KEY"))
                .orElse(fallbackApiKey);
    }

    public boolean hasApiKey() {
        String currentApiKey = apiKey();
        return currentApiKey != null && !currentApiKey.isBlank();
    }

    private Optional<String> readEnvValue(String name) {
        for (Path envPath : List.of(Path.of(".env"), Path.of("backend", ".env"))) {
            Optional<String> value = readEnvValue(envPath, name);
            if (value.isPresent()) {
                return value;
            }
        }

        return Optional.empty();
    }

    private Optional<String> readEnvValue(Path envPath, String name) {
        if (!Files.isRegularFile(envPath)) {
            return Optional.empty();
        }

        try {
            for (String line : Files.readAllLines(envPath)) {
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    continue;
                }

                int separatorIndex = trimmedLine.indexOf('=');
                if (separatorIndex <= 0) {
                    continue;
                }

                String key = stripByteOrderMark(trimmedLine.substring(0, separatorIndex).trim());
                if (!name.equals(key)) {
                    continue;
                }

                String value = trimmedLine.substring(separatorIndex + 1).trim();
                if (
                        value.length() >= 2
                                && ((value.startsWith("\"") && value.endsWith("\""))
                                || (value.startsWith("'") && value.endsWith("'")))
                ) {
                    value = value.substring(1, value.length() - 1);
                }

                return value.isBlank() ? Optional.empty() : Optional.of(value);
            }
        } catch (IOException ignored) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    private String stripByteOrderMark(String value) {
        return value == null ? null : value.replaceFirst("^\\uFEFF", "");
    }
}
