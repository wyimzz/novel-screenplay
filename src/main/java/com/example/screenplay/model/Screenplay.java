package com.example.screenplay.model;

import java.util.List;

public record Screenplay(
        String schemaVersion,
        Project project,
        List<CharacterProfile> characters,
        List<Location> locations,
        List<Prop> props,
        List<Scene> scenes,
        List<AdaptationNote> adaptationNotes
) {
    public record Project(
            String title,
            String sourceLanguage,
            String format,
            int sourceChapterCount,
            FormatProfile formatProfile
    ) {
    }

    public record FormatProfile(
            int targetDurationMinutes,
            String structure,
            String pacing,
            List<String> constraints
    ) {
    }

    public record CharacterProfile(
            String id,
            String name,
            String description,
            String age,
            String gender,
            String clothing,
            int visualWeight,
            String firstAppearance
    ) {
    }

    public record Location(
            String id,
            String name,
            String description,
            String timeOfDay,
            String lightingMood,
            int visualWeight
    ) {
    }

    public record Prop(
            String id,
            String name,
            String description,
            String storyFunction,
            String firstAppearance
    ) {
    }

    public record Scene(
            String id,
            List<String> sourceChapterIds,
            Heading heading,
            String purpose,
            List<String> characters,
            List<String> props,
            List<Beat> beats,
            Continuity continuity,
            SourceFidelity sourceFidelity
    ) {
    }

    public record Heading(String setting, String location, String time) {
    }

    public record Beat(
            String id,
            String type,
            String characterId,
            String parenthetical,
            String text
    ) {
    }

    public record Continuity(String previousSceneId, String nextSceneId) {
    }

    public record SourceFidelity(double confidence, boolean inventedContent, String evidence) {
    }

    public record AdaptationNote(String sceneId, String type, String description) {
    }
}
