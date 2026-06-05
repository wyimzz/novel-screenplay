package com.example.screenplay.service;

import com.example.screenplay.config.AiModelProperties;
import com.example.screenplay.model.Screenplay;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiScreenplayGeneratorTest {

    @Test
    void repairsTextContinuityIntoSceneReferences() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AiSettingsService settingsService = new AiSettingsService(
                new AiModelProperties(false, "https://api.deepseek.com", "", "deepseek-chat", 30),
                objectMapper);
        AiScreenplayGenerator generator = new AiScreenplayGenerator(settingsService, objectMapper);

        String content = """
                {
                  "schemaVersion": "1.0",
                  "project": "雾城来信",
                  "characters": [],
                  "locations": [{"id":"loc_001","name":"房间","description":""}],
                  "props": [],
                  "scenes": [
                    {
                      "id": "scene_001",
                      "sourceChapterIds": ["chapter_01"],
                      "heading": {"setting":"INT","location":"房间","time":"DAY"},
                      "purpose": "开场",
                      "characters": [],
                      "props": [],
                      "beats": [{"id":"beat_001","type":"action","text":"门打开。"}],
                      "continuity": "开篇场景",
                      "sourceFidelity": {"confidence":0.9,"inventedContent":false}
                    },
                    {
                      "id": "scene_002",
                      "sourceChapterIds": ["chapter_02"],
                      "heading": {"setting":"INT","location":"房间","time":"NIGHT"},
                      "purpose": "冲突",
                      "characters": [],
                      "props": [],
                      "beats": [{"id":"beat_002","type":"action","text":"灯熄灭。"}],
                      "continuity": "承接上一场",
                      "sourceFidelity": {"confidence":0.8,"inventedContent":false}
                    }
                  ],
                  "adaptationNotes": [
                    "为增强视觉表现，场景一中增加了咖啡馆内部环境描写及人物定位动作。"
                  ]
                }
                """;

        Screenplay screenplay = generator.parseScreenplayContent(content);

        assertThat(screenplay.project().title()).isEqualTo("未命名作品");
        assertThat(screenplay.project().sourceLanguage()).isEqualTo("zh-CN");
        assertThat(screenplay.project().format()).isEqualTo("web_series");
        assertThat(screenplay.project().sourceChapterCount()).isEqualTo(3);
        assertThat(screenplay.scenes().get(0).continuity().previousSceneId()).isNull();
        assertThat(screenplay.scenes().get(0).continuity().nextSceneId()).isEqualTo("scene_002");
        assertThat(screenplay.scenes().get(0).sourceFidelity().evidence()).isEmpty();
        assertThat(screenplay.scenes().get(1).continuity().previousSceneId()).isEqualTo("scene_001");
        assertThat(screenplay.scenes().get(1).continuity().nextSceneId()).isNull();
        assertThat(screenplay.adaptationNotes()).hasSize(1);
        assertThat(screenplay.adaptationNotes().getFirst().sceneId()).isEqualTo("scene_001");
        assertThat(screenplay.adaptationNotes().getFirst().type()).isEqualTo("ai_adaptation");
        assertThat(screenplay.adaptationNotes().getFirst().description()).contains("增强视觉表现");
    }

    @Test
    void rebuildsProjectAndRepairsShorthandSceneFields() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AiSettingsService settingsService = new AiSettingsService(
                new AiModelProperties(false, "https://api.deepseek.com", "", "deepseek-chat", 30),
                objectMapper);
        AiScreenplayGenerator generator = new AiScreenplayGenerator(settingsService, objectMapper);

        String content = """
                {
                  "project": "模型擅自返回的标题",
                  "characters": [{
                    "id":"char_001",
                    "name":"林舟",
                    "description":"",
                    "age":"未知",
                    "gender":"未知",
                    "clothing":"未说明",
                    "visualWeight":3,
                    "firstAppearance":"chapter_01"
                  }],
                  "scenes": [{
                    "heading": "旧城咖啡馆",
                    "sourceChapterIds": "chapter_01",
                    "purpose": "林舟取信",
                    "characters": "char_001",
                    "beats": "林舟推门进入咖啡馆。",
                    "sourceFidelity": "忠于原文"
                  }],
                  "adaptationNotes": "无"
                }
                """;

        Screenplay screenplay = generator.parseScreenplayContent(
                content,
                "雾城来信",
                "film",
                4);

        assertThat(screenplay.project().title()).isEqualTo("雾城来信");
        assertThat(screenplay.project().format()).isEqualTo("film");
        assertThat(screenplay.project().sourceChapterCount()).isEqualTo(4);
        assertThat(screenplay.scenes()).hasSize(1);
        assertThat(screenplay.scenes().getFirst().heading().location()).isEqualTo("旧城咖啡馆");
        assertThat(screenplay.scenes().getFirst().sourceChapterIds()).containsExactly("chapter_01");
        assertThat(screenplay.scenes().getFirst().characters()).containsExactly("char_001");
        assertThat(screenplay.scenes().getFirst().beats().getFirst().text()).contains("林舟推门");
        assertThat(screenplay.scenes().getFirst().sourceFidelity().confidence()).isEqualTo(0.5);
        assertThat(screenplay.adaptationNotes()).isEmpty();
    }

    @Test
    void mapsObjectAndNameReferencesToStableIds() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AiSettingsService settingsService = new AiSettingsService(
                new AiModelProperties(false, "https://api.deepseek.com", "", "deepseek-chat", 30),
                objectMapper);
        AiScreenplayGenerator generator = new AiScreenplayGenerator(settingsService, objectMapper);

        String content = """
                {
                  "characters": [{"id":"char_001","name":"萧炎","description":""}],
                  "locations": [{"id":"loc_001","name":"广场","description":"","visualWeight":3}],
                  "props": [{"id":"prop_001","name":"魔石碑","description":"","storyFunction":"","firstAppearance":"chapter_01"}],
                  "scenes": [{
                    "sourceChapterIds": [{"id":"chapter_01"}],
                    "heading": {"setting":"EXT","locationId":"loc_001","time":"DAY"},
                    "purpose": "测试结果公布",
                    "characters": [{"id":"char_001","name":"萧炎"}],
                    "props": [{"name":"魔石碑"}],
                    "beats": [{
                      "type":"action",
                      "action_description":"萧炎盯着石碑，手指缓缓收紧。"
                    },{
                      "type":"dialogue",
                      "speaker":{"name":"萧炎"},
                      "content":"三十年河东，三十年河西。"
                    }]
                  }]
                }
                """;

        Screenplay screenplay = generator.parseScreenplayContent(content, "测试", "tv_series", 3);

        assertThat(screenplay.scenes().getFirst().sourceChapterIds()).containsExactly("chapter_01");
        assertThat(screenplay.scenes().getFirst().characters()).containsExactly("char_001");
        assertThat(screenplay.scenes().getFirst().props()).containsExactly("prop_001");
        assertThat(screenplay.scenes().getFirst().beats().getFirst().text()).contains("手指缓缓收紧");
        assertThat(screenplay.scenes().getFirst().beats().get(1).characterId()).isEqualTo("char_001");
        assertThat(screenplay.scenes().getFirst().beats().get(1).text()).contains("三十年河东");
        assertThat(screenplay.scenes().getFirst().heading().location()).isEqualTo("广场");
    }
}
