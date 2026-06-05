package com.example.screenplay.service;

import com.example.screenplay.config.AiModelProperties;
import com.example.screenplay.model.AiConnectionTestResult;
import com.example.screenplay.model.AiSettingsRequest;
import com.example.screenplay.model.AiSettingsView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiSettingsService {

    private final ObjectMapper objectMapper;
    private volatile RuntimeAiSettings settings;

    public AiSettingsService(AiModelProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.settings = new RuntimeAiSettings(
                properties.enabled(),
                properties.baseUrl(),
                properties.apiKey(),
                properties.model(),
                Math.max(properties.timeoutSeconds(), 10));
    }

    public RuntimeAiSettings current() {
        return settings;
    }

    public AiSettingsView view() {
        RuntimeAiSettings current = settings;
        return new AiSettingsView(
                current.enabled(),
                detectProvider(current.baseUrl()),
                current.baseUrl(),
                current.model(),
                current.timeoutSeconds(),
                current.apiKey() != null && !current.apiKey().isBlank(),
                current.available() ? "AI" : "RULE_BASED");
    }

    public synchronized AiSettingsView update(AiSettingsRequest request) {
        String key = request.apiKey() == null || request.apiKey().isBlank()
                ? settings.apiKey()
                : request.apiKey().trim();
        RuntimeAiSettings updated = new RuntimeAiSettings(
                request.enabled(),
                normalizeBaseUrl(request.baseUrl()),
                key,
                request.model().trim(),
                request.timeoutSeconds());
        settings = updated;
        persist(updated);
        return view();
    }

    public AiConnectionTestResult test(AiSettingsRequest request) {
        String key = request.apiKey() == null || request.apiKey().isBlank()
                ? settings.apiKey()
                : request.apiKey().trim();
        if (key == null || key.isBlank()) {
            return new AiConnectionTestResult(false, "请先填写 API Key", 0);
        }

        long startedAt = System.nanoTime();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", request.model().trim());
            payload.put("max_tokens", 8);
            payload.put("temperature", 0);
            payload.put("thinking", Map.of("type", "disabled"));
            payload.put("messages", List.of(Map.of("role", "user", "content", "只回复 OK")));

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(request.baseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofSeconds(Math.min(Math.max(request.timeoutSeconds(), 15), 60)))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long latency = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new AiConnectionTestResult(true, "连接成功", latency);
            }
            return new AiConnectionTestResult(false, readError(response.body(), response.statusCode()), latency);
        } catch (Exception exception) {
            long latency = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            return new AiConnectionTestResult(false, "连接失败：" + exception.getMessage(), latency);
        }
    }

    private String readError(String responseBody, int statusCode) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String message = root.path("error").path("message").asText();
            return message.isBlank() ? "HTTP " + statusCode : message;
        } catch (IOException ignored) {
            return "HTTP " + statusCode;
        }
    }

    private void persist(RuntimeAiSettings value) {
        Path envPath = Path.of(System.getProperty("user.dir"), ".env").normalize();
        String content = """
                SCREENPLAY_AI_ENABLED=%s
                SCREENPLAY_AI_BASE_URL=%s
                SCREENPLAY_AI_API_KEY=%s
                SCREENPLAY_AI_MODEL=%s
                SCREENPLAY_AI_TIMEOUT_SECONDS=%d
                """.formatted(
                value.enabled(),
                value.baseUrl(),
                value.apiKey() == null ? "" : value.apiKey(),
                value.model(),
                value.timeoutSeconds());
        try {
            Files.writeString(
                    envPath,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("AI 设置已更新，但写入 .env 失败：" + exception.getMessage(), exception);
        }
    }

    private String detectProvider(String baseUrl) {
        return baseUrl != null && baseUrl.toLowerCase().contains("deepseek") ? "DeepSeek" : "OpenAI Compatible";
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record RuntimeAiSettings(
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
}
