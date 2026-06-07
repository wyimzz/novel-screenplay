package com.example.screenplay.service;

import com.example.screenplay.config.AiModelProperties;
import com.example.screenplay.model.AiConnectionTestResult;
import com.example.screenplay.model.AiSettingsRequest;
import com.example.screenplay.model.AiSettingsView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final boolean persistSettings;
    private volatile RuntimeAiSettings settings;

    @Autowired
    public AiSettingsService(AiModelProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, true);
    }

    AiSettingsService(
            AiModelProperties properties,
            ObjectMapper objectMapper,
            boolean persistSettings
    ) {
        this.objectMapper = objectMapper;
        this.persistSettings = persistSettings;
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
                detectProviderId(current.baseUrl()),
                detectProvider(current.baseUrl()),
                current.baseUrl(),
                current.model(),
                current.timeoutSeconds(),
                current.apiKey() != null && !current.apiKey().isBlank(),
                current.available() ? "AI" : "RULE_BASED");
    }

    public synchronized AiSettingsView update(AiSettingsRequest request) {
        String normalizedBaseUrl = normalizeBaseUrl(request.baseUrl());
        String key = resolveKey(request, normalizedBaseUrl);
        RuntimeAiSettings updated = new RuntimeAiSettings(
                request.enabled(),
                normalizedBaseUrl,
                key,
                request.model().trim(),
                request.timeoutSeconds());
        settings = updated;
        if (persistSettings) {
            persist(updated);
        }
        return view();
    }

    public AiConnectionTestResult test(AiSettingsRequest request) {
        String normalizedBaseUrl = normalizeBaseUrl(request.baseUrl());
        String key = resolveKey(request, normalizedBaseUrl);
        if ((key == null || key.isBlank()) && !isLocalEndpoint(normalizedBaseUrl)) {
            return new AiConnectionTestResult(false, "请先填写 API Key", 0);
        }

        long startedAt = System.nanoTime();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", request.model().trim());
            payload.put("max_tokens", 8);
            payload.put("temperature", 0);
            addProviderOptions(payload, request.baseUrl(), request.model());
            payload.put("messages", List.of(Map.of("role", "user", "content", "只回复 OK")));

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();
            HttpRequest.Builder httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(normalizedBaseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(Math.min(Math.max(request.timeoutSeconds(), 15), 60)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
            if (!key.isBlank()) {
                httpRequest.header("Authorization", "Bearer " + key);
            }
            HttpResponse<String> response = client.send(httpRequest.build(), HttpResponse.BodyHandlers.ofString());
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

    public AiConnectionTestResult testCurrent() {
        RuntimeAiSettings current = settings;
        if (!current.available()) {
            return new AiConnectionTestResult(false, "当前为离线模式或尚未配置 API Key", 0);
        }
        return test(new AiSettingsRequest(
                true,
                detectProviderId(current.baseUrl()),
                current.baseUrl(),
                "",
                current.model(),
                current.timeoutSeconds()));
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
        return switch (detectProviderId(baseUrl)) {
            case "deepseek" -> "DeepSeek";
            case "zhipu" -> "智谱 GLM";
            case "qwen" -> "通义千问";
            case "moonshot" -> "Moonshot / Kimi";
            case "openai" -> "OpenAI";
            case "ollama" -> "Ollama 本地模型";
            default -> "OpenAI Compatible";
        };
    }

    private String resolveKey(AiSettingsRequest request, String normalizedBaseUrl) {
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            return request.apiKey().trim();
        }
        boolean providerChanged = !normalizedBaseUrl.equalsIgnoreCase(settings.baseUrl());
        return providerChanged ? "" : settings.apiKey();
    }

    private boolean isLocalEndpoint(String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.toLowerCase();
        return value.contains("localhost") || value.contains("127.0.0.1");
    }

    private String detectProviderId(String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.toLowerCase();
        if (value.contains("deepseek")) return "deepseek";
        if (value.contains("bigmodel.cn")) return "zhipu";
        if (value.contains("dashscope")) return "qwen";
        if (value.contains("moonshot")) return "moonshot";
        if (value.contains("api.openai.com")) return "openai";
        if (value.contains("localhost:11434") || value.contains("127.0.0.1:11434")) return "ollama";
        return "custom";
    }

    void addProviderOptions(Map<String, Object> payload, String baseUrl, String model) {
        String providerId = detectProviderId(baseUrl);
        if (providerId.equals("deepseek")
                && model != null
                && model.toLowerCase().startsWith("deepseek")) {
            payload.put("thinking", Map.of("type", "disabled"));
        }
        if (providerId.equals("zhipu")) {
            payload.put("thinking", Map.of("type", "disabled"));
        }
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
            String url = baseUrl == null ? "" : baseUrl.toLowerCase();
            boolean local = url.contains("localhost") || url.contains("127.0.0.1");
            return enabled && (local || apiKey != null && !apiKey.isBlank());
        }
    }
}
