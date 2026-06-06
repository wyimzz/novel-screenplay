package com.example.screenplay.service;

import com.example.screenplay.config.AiModelProperties;
import com.example.screenplay.model.AiSettingsRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiSettingsServiceTest {

    @Test
    void detectsGlmAndClearsKeyWhenProviderChanges() {
        AiSettingsService service = new AiSettingsService(
                new AiModelProperties(
                        true,
                        "https://api.deepseek.com",
                        "deepseek-key",
                        "deepseek-chat",
                        180),
                new ObjectMapper(),
                false);

        var view = service.update(new AiSettingsRequest(
                true,
                "zhipu",
                "https://open.bigmodel.cn/api/paas/v4",
                "",
                "glm-4.5-air",
                180));

        assertThat(view.providerId()).isEqualTo("zhipu");
        assertThat(view.provider()).isEqualTo("智谱 GLM");
        assertThat(view.apiKeyConfigured()).isFalse();
        assertThat(view.activeMode()).isEqualTo("RULE_BASED");
    }

    @Test
    void allowsLocalOllamaWithoutApiKey() {
        AiSettingsService service = new AiSettingsService(
                new AiModelProperties(
                        true,
                        "http://localhost:11434/v1",
                        "",
                        "qwen3:8b",
                        180),
                new ObjectMapper(),
                false);

        assertThat(service.view().providerId()).isEqualTo("ollama");
        assertThat(service.view().activeMode()).isEqualTo("AI");
        assertThat(service.current().available()).isTrue();
    }
}
