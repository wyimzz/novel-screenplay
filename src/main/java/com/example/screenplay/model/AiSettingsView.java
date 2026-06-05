package com.example.screenplay.model;

public record AiSettingsView(
        boolean enabled,
        String provider,
        String baseUrl,
        String model,
        int timeoutSeconds,
        boolean apiKeyConfigured,
        String activeMode
) {
}
