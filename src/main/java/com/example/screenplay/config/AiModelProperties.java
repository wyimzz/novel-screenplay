package com.example.screenplay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "screenplay.ai")
public record AiModelProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String model,
        int timeoutSeconds
) {
    public boolean available() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
