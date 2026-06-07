package com.example.screenplay.web;

import com.example.screenplay.model.ConversionRequest;
import com.example.screenplay.model.ConversionResult;
import com.example.screenplay.model.ConversionJob;
import com.example.screenplay.model.Screenplay;
import com.example.screenplay.service.ConversionJobService;
import com.example.screenplay.service.ConversionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/screenplays")
public class ConversionController {

    private final ConversionService conversionService;
    private final ConversionJobService conversionJobService;

    public ConversionController(
            ConversionService conversionService,
            ConversionJobService conversionJobService
    ) {
        this.conversionService = conversionService;
        this.conversionJobService = conversionJobService;
    }

    @PostMapping("/convert")
    public ConversionResult convert(@Valid @RequestBody ConversionRequest request) {
        return conversionService.convert(request);
    }

    @PostMapping("/jobs")
    public ConversionJob startJob(@Valid @RequestBody ConversionRequest request) {
        return conversionJobService.start(request);
    }

    @GetMapping("/jobs/{id}")
    public ConversionJob getJob(@PathVariable String id) {
        return conversionJobService.get(id);
    }

    @DeleteMapping("/jobs/{id}")
    public ConversionJob cancelJob(@PathVariable String id) {
        return conversionJobService.cancel(id);
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
