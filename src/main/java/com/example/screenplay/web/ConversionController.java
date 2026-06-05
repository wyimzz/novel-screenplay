package com.example.screenplay.web;

import com.example.screenplay.model.ConversionRequest;
import com.example.screenplay.model.ConversionResult;
import com.example.screenplay.model.Screenplay;
import com.example.screenplay.service.ConversionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/screenplays")
public class ConversionController {

    private final ConversionService conversionService;

    public ConversionController(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @PostMapping("/convert")
    public ConversionResult convert(@Valid @RequestBody ConversionRequest request) {
        return conversionService.convert(request);
    }

    @PostMapping("/serialize")
    public ConversionResult serialize(@RequestBody Screenplay screenplay) {
        return conversionService.serialize(screenplay);
    }

    @PostMapping(value = "/export", produces = "application/yaml")
    public ResponseEntity<byte[]> export(@Valid @RequestBody ConversionRequest request) {
        ConversionResult result = conversionService.convert(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"screenplay.yaml\"")
                .contentType(MediaType.parseMediaType("application/yaml"))
                .body(result.yaml().getBytes(StandardCharsets.UTF_8));
    }
}
