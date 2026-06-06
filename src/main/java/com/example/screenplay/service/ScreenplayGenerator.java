package com.example.screenplay.service;

import com.example.screenplay.model.Chapter;
import com.example.screenplay.model.GenerationProgress;
import com.example.screenplay.model.Screenplay;

import java.util.List;
import java.util.function.Consumer;

public interface ScreenplayGenerator {

    Screenplay generate(String title, String format, List<Chapter> chapters);

    default Screenplay generate(
            String title,
            String format,
            List<Chapter> chapters,
            Consumer<GenerationProgress> progress
    ) {
        progress.accept(new GenerationProgress("scenes", "正在生成结构化剧本", 55));
        return generate(title, format, chapters);
    }

    String mode();
}
