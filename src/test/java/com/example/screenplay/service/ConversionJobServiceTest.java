package com.example.screenplay.service;

import com.example.screenplay.model.ConversionJob;
import com.example.screenplay.model.ConversionRequest;
import org.junit.jupiter.api.Test;

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
}
