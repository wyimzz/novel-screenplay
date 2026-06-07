package com.example.screenplay.service;

import com.example.screenplay.model.ConversionJob;
import com.example.screenplay.model.ConversionRequest;
import com.example.screenplay.model.GenerationProgress;
import com.example.screenplay.model.Screenplay;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConversionJobServiceTest {

    @Test
    void completesInBackgroundAndExposesRealProgress() throws Exception {
        ConversionService conversionService = new ConversionService(
                new ChapterParser(),
                new RuleBasedScreenplayGenerator(),
                new ScreenplayValidator());
        ConversionJobService jobs = new ConversionJobService(conversionService);
        ConversionRequest request = new ConversionRequest(
                "雾城来信",
                """
                        第一章 雨夜来客
                        夜里，林舟走进旧城咖啡馆。他说：“我来取信。”
                        第二章 档案名单
                        苏禾回到档案室，拿出名单。她问：“你认识他吗？”
                        第三章 废弃钟楼
                        两人来到废弃钟楼。林舟喊道：“谁在那里？”
                        """,
                "web_series");

        ConversionJob started = jobs.start(request);
        ConversionJob completed = started;
        for (int attempt = 0; attempt < 100 && !"COMPLETED".equals(completed.status()); attempt++) {
            Thread.sleep(20);
            completed = jobs.get(started.id());
        }

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.stage()).isEqualTo("yaml");
        assertThat(completed.percent()).isEqualTo(100);
        assertThat(completed.result()).isNotNull();
        assertThat(completed.result().yaml()).contains("sourceChapterCount: 3");
    }

    @Test
    void exposesPreviewBeforeTheFinalResultIsReady() throws Exception {
        Screenplay preview = new RuleBasedScreenplayGenerator().generate(
                "雾城来信",
                "web_series",
                List.of(
                        new com.example.screenplay.model.Chapter("chapter_001", 1, "第一章", "林舟进入咖啡馆。"),
                        new com.example.screenplay.model.Chapter("chapter_002", 2, "第二章", "苏禾找到名单。"),
                        new com.example.screenplay.model.Chapter("chapter_003", 3, "第三章", "两人来到钟楼。")));
        ScreenplayGenerator slowGenerator = new ScreenplayGenerator() {
            @Override
            public Screenplay generate(String title, String format, List<com.example.screenplay.model.Chapter> chapters) {
                return preview;
            }

            @Override
            public Screenplay generate(
                    String title,
                    String format,
                    List<com.example.screenplay.model.Chapter> chapters,
                    java.util.function.Consumer<GenerationProgress> progress
            ) {
                progress.accept(new GenerationProgress("assets", "故事圣经已完成", 25, preview));
                try {
                    Thread.sleep(150);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                return preview;
            }

            @Override
            public String mode() {
                return "TEST";
            }
        };
        ConversionJobService jobs = new ConversionJobService(new ConversionService(
                new ChapterParser(),
                slowGenerator,
                new ScreenplayValidator()));
        ConversionJob started = jobs.start(new ConversionRequest(
                "雾城来信",
                """
                        第一章 雨夜来客
                        林舟进入咖啡馆。
                        第二章 档案名单
                        苏禾找到名单。
                        第三章 废弃钟楼
                        两人来到钟楼。
                        """,
                "web_series"));

        ConversionJob running = started;
        for (int attempt = 0; attempt < 50 && running.preview() == null; attempt++) {
            Thread.sleep(10);
            running = jobs.get(started.id());
        }

        assertThat(running.status()).isEqualTo("RUNNING");
        assertThat(running.preview()).isNotNull();
        assertThat(running.result()).isNull();
        assertThat(running.stage()).isEqualTo("assets");
    }

    @Test
    void cancelsRunningConversion() throws Exception {
        ScreenplayGenerator slowGenerator = new ScreenplayGenerator() {
            @Override
            public Screenplay generate(String title, String format, List<com.example.screenplay.model.Chapter> chapters) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Screenplay generate(
                    String title,
                    String format,
                    List<com.example.screenplay.model.Chapter> chapters,
                    java.util.function.Consumer<GenerationProgress> progress
            ) {
                progress.accept(new GenerationProgress("assets", "正在分析", 15));
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("任务被中断", exception);
                }
                return new RuleBasedScreenplayGenerator().generate(title, format, chapters);
            }

            @Override
            public String mode() {
                return "TEST";
            }
        };
        ConversionJobService jobs = new ConversionJobService(new ConversionService(
                new ChapterParser(),
                slowGenerator,
                new ScreenplayValidator()));
        ConversionJob started = jobs.start(new ConversionRequest(
                "取消测试",
                """
                        第一章 开始
                        一。
                        第二章 继续
                        二。
                        第三章 结束
                        三。
                        """,
                "web_series"));

        ConversionJob canceled = jobs.cancel(started.id());
        Thread.sleep(30);

        assertThat(canceled.status()).isEqualTo("CANCELED");
        assertThat(jobs.get(started.id()).status()).isEqualTo("CANCELED");
        assertThat(jobs.get(started.id()).error()).contains("重新开始改编");
    }
}
