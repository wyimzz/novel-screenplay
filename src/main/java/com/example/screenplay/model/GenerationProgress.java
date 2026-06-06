package com.example.screenplay.model;

public record GenerationProgress(
        String stage,
        String message,
        int percent,
        Screenplay preview
) {
    public GenerationProgress(String stage, String message, int percent) {
        this(stage, message, percent, null);
    }
}
