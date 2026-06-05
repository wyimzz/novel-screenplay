package com.example.screenplay.service;

import com.example.screenplay.model.Chapter;
import com.example.screenplay.model.ConversionRequest;
import com.example.screenplay.model.ConversionResult;
import com.example.screenplay.model.Screenplay;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConversionService {

    private final ChapterParser chapterParser;
    private final ScreenplayGenerator generator;
    private final ScreenplayValidator validator;
    private final ObjectMapper yamlMapper;

    public ConversionService(
            ChapterParser chapterParser,
            ScreenplayGenerator generator,
            ScreenplayValidator validator
    ) {
        this.chapterParser = chapterParser;
        this.generator = generator;
        this.validator = validator;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.findAndRegisterModules();
    }

    public ConversionResult convert(ConversionRequest request) {
        List<Chapter> chapters = chapterParser.parse(request.content());
        Screenplay screenplay = generator.generate(request.title(), request.format(), chapters);
        List<String> messages = validator.validate(screenplay);
        return new ConversionResult(generator.mode(), screenplay, toYaml(screenplay), messages);
    }

    public ConversionResult serialize(Screenplay screenplay) {
        Screenplay normalized = normalizeEditedScreenplay(screenplay);
        List<String> messages = validator.validate(normalized);
        return new ConversionResult("EDITED", normalized, toYaml(normalized), messages);
    }

    private Screenplay normalizeEditedScreenplay(Screenplay screenplay) {
        List<Screenplay.Scene> sourceScenes = screenplay.scenes() == null
                ? List.of()
                : screenplay.scenes();
        Map<String, String> sceneIdMap = new LinkedHashMap<>();
        for (int index = 0; index < sourceScenes.size(); index++) {
            String oldId = sourceScenes.get(index).id();
            sceneIdMap.put(oldId == null ? "scene_at_" + index : oldId, "scene_%03d".formatted(index + 1));
        }

        List<Screenplay.Scene> scenes = new ArrayList<>();
        for (int index = 0; index < sourceScenes.size(); index++) {
            Screenplay.Scene scene = sourceScenes.get(index);
            String sceneId = "scene_%03d".formatted(index + 1);
            List<Screenplay.Beat> beats = new ArrayList<>();
            List<Screenplay.Beat> sourceBeats = scene.beats() == null ? List.of() : scene.beats();
            for (int beatIndex = 0; beatIndex < sourceBeats.size(); beatIndex++) {
                Screenplay.Beat beat = sourceBeats.get(beatIndex);
                beats.add(new Screenplay.Beat(
                        "beat_%03d_%02d".formatted(index + 1, beatIndex + 1),
                        beat.type(),
                        beat.characterId(),
                        beat.parenthetical(),
                        beat.text()));
            }
            scenes.add(new Screenplay.Scene(
                    sceneId,
                    scene.sourceChapterIds() == null ? List.of() : scene.sourceChapterIds(),
                    scene.heading(),
                    scene.purpose(),
                    scene.characters() == null ? List.of() : scene.characters(),
                    scene.props() == null ? List.of() : scene.props(),
                    beats,
                    new Screenplay.Continuity(
                            index == 0 ? null : "scene_%03d".formatted(index),
                            index == sourceScenes.size() - 1 ? null : "scene_%03d".formatted(index + 2)),
                    scene.sourceFidelity()));
        }

        List<Screenplay.AdaptationNote> notes = screenplay.adaptationNotes() == null
                ? List.of()
                : screenplay.adaptationNotes().stream()
                .map(note -> new Screenplay.AdaptationNote(
                        sceneIdMap.getOrDefault(
                                note.sceneId(),
                                scenes.isEmpty() ? "scene_001" : scenes.getFirst().id()),
                        note.type(),
                        note.description()))
                .toList();

        return new Screenplay(
                screenplay.schemaVersion(),
                screenplay.project(),
                screenplay.characters() == null ? List.of() : screenplay.characters(),
                screenplay.locations() == null ? List.of() : screenplay.locations(),
                screenplay.props() == null ? List.of() : screenplay.props(),
                scenes,
                notes);
    }

    private String toYaml(Screenplay screenplay) {
        try {
            return yamlMapper.writeValueAsString(screenplay);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("YAML 序列化失败", exception);
        }
    }
}
