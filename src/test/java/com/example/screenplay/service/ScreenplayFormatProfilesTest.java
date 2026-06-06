package com.example.screenplay.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenplayFormatProfilesTest {

    @Test
    void definesDistinctProductionRulesForEveryFormat() {
        var film = ScreenplayFormatProfiles.profile("film");
        var television = ScreenplayFormatProfiles.profile("tv_series");
        var webSeries = ScreenplayFormatProfiles.profile("web_series");
        var stagePlay = ScreenplayFormatProfiles.profile("stage_play");

        assertThat(film.targetDurationMinutes()).isEqualTo(110);
        assertThat(film.structure()).contains("三幕");
        assertThat(television.targetDurationMinutes()).isEqualTo(45);
        assertThat(television.structure()).contains("四幕");
        assertThat(webSeries.targetDurationMinutes()).isEqualTo(12);
        assertThat(webSeries.constraints()).anyMatch(rule -> rule.contains("前 10 秒"));
        assertThat(stagePlay.targetDurationMinutes()).isEqualTo(100);
        assertThat(stagePlay.constraints()).anyMatch(rule -> rule.contains("减少换景"));

        assertThat(film).isNotEqualTo(television);
        assertThat(television).isNotEqualTo(webSeries);
        assertThat(webSeries).isNotEqualTo(stagePlay);
    }

    @Test
    void createsFormatSpecificSceneStructures() {
        var filmOpening = ScreenplayFormatProfiles.sceneDesign("film", 0, 6);
        var televisionSecondScene = ScreenplayFormatProfiles.sceneDesign("tv_series", 1, 6);
        var webOpening = ScreenplayFormatProfiles.sceneDesign("web_series", 0, 6);
        var webEnding = ScreenplayFormatProfiles.sceneDesign("web_series", 5, 6);
        var stageScene = ScreenplayFormatProfiles.sceneDesign("stage_play", 2, 6);

        assertThat(filmOpening.sectionLabel()).contains("第一幕");
        assertThat(filmOpening.productionNotes()).anyMatch(note -> note.contains("镜头"));

        assertThat(televisionSecondScene.storyLine()).isEqualTo("B_STORY");
        assertThat(televisionSecondScene.dramaticFunction()).isEqualTo("act_turn");

        assertThat(webOpening.sectionLabel()).contains("HOOK");
        assertThat(webEnding.sectionLabel()).contains("CLIFFHANGER");
        assertThat(webOpening.estimatedDurationSeconds()).isLessThanOrEqualTo(120);

        assertThat(stageScene.sectionLabel()).contains("幕").contains("场");
        assertThat(stageScene.productionNotes()).anyMatch(note -> note.contains("出入场"));

        assertThat(filmOpening).isNotEqualTo(televisionSecondScene);
        assertThat(televisionSecondScene).isNotEqualTo(webOpening);
        assertThat(webOpening).isNotEqualTo(stageScene);
    }
}
