package com.example.screenplay.model;

public record ConversionJob(
        String id,
        String status,
        String stage,
        String message,
        int percent,
        Screenplay preview,
        ConversionResult result,
        String error
) {
}
