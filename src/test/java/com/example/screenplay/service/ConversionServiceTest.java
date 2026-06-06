package com.example.screenplay.service;

import com.example.screenplay.model.ConversionRequest;
import com.example.screenplay.model.ConversionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversionServiceTest {

    @Test
    void createsYamlAndValidSceneReferences() {
        ConversionService service = new ConversionService(
                new ChapterParser(),
                new RuleBasedScreenplayGenerator(),
                new ScreenplayValidator());
        String source = """
                第一章 雨夜来客
                夜里，林舟走进旧城咖啡馆。
                林舟说道：“我来取那封信。”
                第二章 名单
                苏禾回到档案室，拿出一份名单。
                苏禾问：“你认识这个名字吗？”
                第三章 钟楼
                两人来到废弃钟楼。
                林舟喊道：“谁在那里？”
                """;

        ConversionResult result = service.convert(
                new ConversionRequest("雾城来信", source, "web_series"));

        assertThat(result.mode()).isEqualTo("RULE_BASED");
        assertThat(result.screenplay().scenes()).hasSize(3);
        assertThat(result.screenplay().project().formatProfile().targetDurationMinutes()).isEqualTo(12);
        assertThat(result.screenplay().project().formatProfile().structure()).contains("短剧");
        assertThat(result.screenplay().props())
                .extracting("name")
                .contains("名单");
        String listPropId = result.screenplay().props().stream()
                .filter(prop -> prop.name().equals("名单"))
                .findFirst()
                .orElseThrow()
                .id();
        assertThat(result.screenplay().scenes().get(1).props()).contains(listPropId);
        assertThat(result.yaml()).contains("schemaVersion: \"1.0\"");
        assertThat(result.yaml()).contains("targetDurationMinutes: 12");
        assertThat(result.validationMessages()).containsExactly("Schema 结构与跨引用语义检查通过");

        var edited = result.screenplay();
        var serialized = service.serialize(edited);
        assertThat(serialized.mode()).isEqualTo("EDITED");
        assertThat(serialized.yaml()).contains("sourceChapterCount: 3");
        assertThat(serialized.validationMessages()).containsExactly("Schema 结构与跨引用语义检查通过");
    }
}
