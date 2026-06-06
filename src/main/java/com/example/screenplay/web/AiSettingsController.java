package com.example.screenplay.web;

import com.example.screenplay.model.AiConnectionTestResult;
import com.example.screenplay.model.AiSettingsRequest;
import com.example.screenplay.model.AiSettingsView;
import com.example.screenplay.service.AiSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings/ai")
public class AiSettingsController {

    private final AiSettingsService settingsService;

    public AiSettingsController(AiSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public AiSettingsView getSettings() {
        return settingsService.view();
    }

    @PutMapping
    public AiSettingsView updateSettings(@Valid @RequestBody AiSettingsRequest request) {
        return settingsService.update(request);
    }

    @PostMapping("/test")
    public AiConnectionTestResult testConnection(@Valid @RequestBody AiSettingsRequest request) {
        return settingsService.test(request);
    }

    @PostMapping("/test-current")
    public AiConnectionTestResult testCurrentConnection() {
        return settingsService.testCurrent();
    }
}
