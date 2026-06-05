package com.example.screenplay.model;

import java.util.List;

public record ConversionResult(
        String mode,
        Screenplay screenplay,
        String yaml,
        List<String> validationMessages
) {
}
