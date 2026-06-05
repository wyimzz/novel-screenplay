package com.example.screenplay.service;

import com.example.screenplay.model.Chapter;
import com.example.screenplay.model.Screenplay;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Primary
@Component
public class HybridScreenplayGenerator implements ScreenplayGenerator {

    private final AiScreenplayGenerator aiGenerator;
    private final RuleBasedScreenplayGenerator ruleGenerator;

    public HybridScreenplayGenerator(
            AiScreenplayGenerator aiGenerator,
            RuleBasedScreenplayGenerator ruleGenerator
    ) {
        this.aiGenerator = aiGenerator;
        this.ruleGenerator = ruleGenerator;
    }

    @Override
    public Screenplay generate(String title, String format, List<Chapter> chapters) {
        return activeGenerator().generate(title, format, chapters);
    }

    @Override
    public String mode() {
        return activeGenerator().mode();
    }

    private ScreenplayGenerator activeGenerator() {
        return aiGenerator.available() ? aiGenerator : ruleGenerator;
    }
}
