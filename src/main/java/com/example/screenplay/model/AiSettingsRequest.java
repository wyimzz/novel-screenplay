package com.example.screenplay.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AiSettingsRequest(
        boolean enabled,
        @NotBlank(message = "API 地址不能为空")
        String baseUrl,
        String apiKey,
        @NotBlank(message = "模型名称不能为空")
        String model,
        @Min(value = 10, message = "超时时间不能少于 10 秒")
        int timeoutSeconds
) {
}
