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
}
