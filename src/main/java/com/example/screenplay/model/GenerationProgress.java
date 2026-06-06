package com.example.screenplay.model;

public record GenerationProgress(
        String stage,
        String message,
        int percent
) {
}
