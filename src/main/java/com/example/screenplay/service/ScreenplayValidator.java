package com.example.screenplay.service;

import com.example.screenplay.model.Screenplay;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class ScreenplayValidator {

    public List<String> validate(Screenplay screenplay) {
        List<String> messages = new ArrayList<>();
        if (screenplay == null) {
            return List.of("剧本对象不能为空");
        }
        if (!"1.0".equals(screenplay.schemaVersion())) {
            messages.add("schemaVersion 必须为 1.0");
        }
        if (screenplay.project() == null) {
            messages.add("缺少 project 对象");
        } else if (screenplay.project().sourceChapterCount() < 3) {
            messages.add("源小说必须至少包含 3 个章节");
        }

        Set<String> characterIds = new HashSet<>();
        safeList(screenplay.characters()).stream()
                .filter(Objects::nonNull)
                .forEach(character -> addRequiredId(character.id(), "人物", characterIds, messages));
        Set<String> propIds = new HashSet<>();
        safeList(screenplay.props()).stream()
                .filter(Objects::nonNull)
                .forEach(prop -> addRequiredId(prop.id(), "道具", propIds, messages));
        Set<String> locationIds = new HashSet<>();
        safeList(screenplay.locations()).stream()
                .filter(Objects::nonNull)
                .forEach(location -> addRequiredId(location.id(), "地点", locationIds, messages));

        if (safeList(screenplay.scenes()).isEmpty()) {
            messages.add("剧本至少需要一个场景");
        }

        Set<String> sceneIds = new HashSet<>();
        for (Screenplay.Scene scene : safeList(screenplay.scenes())) {
            if (scene == null) {
                messages.add("场景列表中存在空对象");
                continue;
            }
            if (scene.id() == null || scene.id().isBlank()) {
                messages.add("场景 ID 不能为空");
            } else if (!sceneIds.add(scene.id())) {
                messages.add("场景 ID 重复：" + scene.id());
            }
            if (scene.heading() == null) {
                messages.add("场景 " + scene.id() + " 缺少 heading 对象");
            }
            if (scene.sourceFidelity() == null) {
                messages.add("场景 " + scene.id() + " 缺少 sourceFidelity 对象");
            }
            if (safeList(scene.beats()).isEmpty()) {
                messages.add("场景 " + scene.id() + " 至少需要一个 beat");
            }
            for (String characterId : safeList(scene.characters())) {
                if (!characterIds.contains(characterId)) {
                    messages.add("场景 " + scene.id() + " 引用了不存在的人物：" + characterId);
                }
            }
            for (String propId : safeList(scene.props())) {
                if (!propIds.contains(propId)) {
                    messages.add("场景 " + scene.id() + " 引用了不存在的道具：" + propId);
                }
            }
            safeList(scene.beats()).stream()
                    .filter(Objects::nonNull)
                    .map(Screenplay.Beat::characterId)
                    .filter(Objects::nonNull)
                    .filter(id -> !characterIds.contains(id))
                    .forEach(id -> messages.add("场景 " + scene.id() + " 的对白引用了不存在的人物：" + id));
        }

        Set<String> allSceneIds = Set.copyOf(sceneIds);
        for (Screenplay.Scene scene : safeList(screenplay.scenes())) {
            if (scene == null) {
                continue;
            }
            Screenplay.Continuity continuity = scene.continuity();
            if (continuity == null) {
                messages.add("场景 " + scene.id() + " 缺少 continuity 对象");
                continue;
            }
            if (continuity.previousSceneId() != null && !allSceneIds.contains(continuity.previousSceneId())) {
                messages.add("场景 " + scene.id() + " 的 previousSceneId 无效");
            }
            if (continuity.nextSceneId() != null && !allSceneIds.contains(continuity.nextSceneId())) {
                messages.add("场景 " + scene.id() + " 的 nextSceneId 无效");
            }
        }

        if (messages.isEmpty()) {
            messages.add("Schema 结构与跨引用语义检查通过");
        }
        return messages;
    }

    private void addRequiredId(String id, String type, Set<String> ids, List<String> messages) {
        if (id == null || id.isBlank()) {
            messages.add(type + " ID 不能为空");
        } else if (!ids.add(id)) {
            messages.add(type + " ID 重复：" + id);
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }
}
