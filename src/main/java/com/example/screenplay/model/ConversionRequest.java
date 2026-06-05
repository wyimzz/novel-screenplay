package com.example.screenplay.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConversionRequest(
        @NotBlank(message = "作品名称不能为空")
        String title,

        @NotBlank(message = "小说正文不能为空")
        @Size(min = 100, message = "小说正文至少需要 100 个字符")
        String content,

        String format
) {
}
