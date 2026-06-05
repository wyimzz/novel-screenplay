package com.example.screenplay.model;

public record AiConnectionTestResult(
        boolean success,
        String message,
        long latencyMs
) {
}
