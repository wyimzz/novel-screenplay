package com.example.screenplay.service;

import com.example.screenplay.model.Chapter;
import com.example.screenplay.model.Screenplay;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RuleBasedScreenplayGenerator implements ScreenplayGenerator {

    private static final Pattern SPEAKER = Pattern.compile(
            "([\\p{IsHan}]{2,4})(?:低声|大声|轻声|冷冷地|笑着|问|答|喊|说道|说|道)[：:]?[“\"]");
    private static final Pattern DIALOGUE = Pattern.compile("[“\"]([^”\"\\n]{2,120})[”\"]");
    private static final Pattern LOCATION = Pattern.compile(
            "(?:来到|走进|进入|回到|赶到|站在)([\\p{IsHan}]{2,10}(?:室|厅|店|馆|院|楼|房|街|站|场|园|门|办公室|咖啡馆|餐厅))");
    private static final List<String> PROP_DICTIONARY = List.of(
            "名单", "钥匙", "照片", "文件", "档案", "手机", "项链",
            "戒指", "手表", "箱子", "笔记", "地图", "信");

    @Override
    public Screenplay generate(String title, String format, List<Chapter> chapters) {
        Map<String, String> characterIds = discoverCharacters(chapters);
        Map<String, String> locationIds = discoverLocations(chapters);
        Map<String, String> propIds = discoverProps(chapters);
        if (locationIds.isEmpty()) {
            locationIds.put("未明确地点", "loc_unspecified");
        }

        List<Screenplay.CharacterProfile> characters = characterIds.entrySet().stream()
                .map(entry -> new Screenplay.CharacterProfile(
                        entry.getValue(),
                        entry.getKey(),
                        "根据小说对白自动识别的人物",
                        null,
                        null,
                        null,
                        3,
                        firstAppearance(entry.getKey(), chapters)))
                .toList();
        List<Screenplay.Location> locations = locationIds.entrySet().stream()
                .map(entry -> new Screenplay.Location(
                        entry.getValue(),
                        entry.getKey(),
                        "根据章节内容自动识别的场景",
                        null,
                        null,
                        3))
                .toList();
        List<Screenplay.Prop> props = propIds.entrySet().stream()
                .map(entry -> new Screenplay.Prop(
                        entry.getValue(),
                        entry.getKey(),
                        "根据小说内容自动识别的关键道具",
                        "推动情节或承载线索",
                        firstAppearance(entry.getKey(), chapters)))
                .toList();

        List<Screenplay.Scene> scenes = new ArrayList<>();
        for (int i = 0; i < chapters.size(); i++) {
            Chapter chapter = chapters.get(i);
            String sceneId = "scene_%03d".formatted(i + 1);
            String previous = i == 0 ? null : "scene_%03d".formatted(i);
            String next = i == chapters.size() - 1 ? null : "scene_%03d".formatted(i + 2);
            String location = detectLocation(chapter.content(), locationIds.keySet());
            List<String> sceneCharacters = characterIds.entrySet().stream()
                    .filter(entry -> chapter.content().contains(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .toList();
            List<String> sceneProps = propIds.entrySet().stream()
                    .filter(entry -> chapter.content().contains(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .toList();

            scenes.add(new Screenplay.Scene(
                    sceneId,
                    List.of(chapter.id()),
                    new Screenplay.Heading(detectSetting(chapter.content()), location, detectTime(chapter.content())),
                    summarize(chapter.content()),
                    sceneCharacters,
                    sceneProps,
                    buildBeats(chapter, characterIds),
                    new Screenplay.Continuity(previous, next),
                    new Screenplay.SourceFidelity(
                            0.72,
                            false,
                            shorten(chapter.content(), 80))));
        }

        return new Screenplay(
                "1.0",
                new Screenplay.Project(
                        title,
                        "zh-CN",
                        format == null || format.isBlank() ? "web_series" : format,
                        chapters.size()),
                characters,
                locations,
                props,
                scenes,
                List.of(new Screenplay.AdaptationNote(
                        "all",
                        "rule_based_draft",
                        "当前内容由离线规则模式生成，建议配置 AI 模型后进一步润色对白与场景目的。")));
    }

    private Map<String, String> discoverCharacters(List<Chapter> chapters) {
        Set<String> names = new LinkedHashSet<>();
        for (Chapter chapter : chapters) {
            Matcher matcher = SPEAKER.matcher(chapter.content());
            while (matcher.find()) {
                String candidate = matcher.group(1);
                if (!candidate.endsWith("地") && !candidate.contains("时候")) {
                    names.add(candidate);
                }
            }
        }
        Map<String, String> ids = new LinkedHashMap<>();
        int index = 1;
        for (String name : names) {
            ids.put(name, "char_%03d".formatted(index++));
        }
        return ids;
    }

    private Map<String, String> discoverLocations(List<Chapter> chapters) {
        Set<String> names = new LinkedHashSet<>();
        for (Chapter chapter : chapters) {
            Matcher matcher = LOCATION.matcher(chapter.content());
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        Map<String, String> ids = new LinkedHashMap<>();
        int index = 1;
        for (String name : names) {
            ids.put(name, "loc_%03d".formatted(index++));
        }
        return ids;
    }

    private Map<String, String> discoverProps(List<Chapter> chapters) {
        Set<String> names = new LinkedHashSet<>();
        for (Chapter chapter : chapters) {
            for (String candidate : PROP_DICTIONARY) {
                if (chapter.content().contains(candidate)) {
                    names.add(candidate);
                }
            }
        }
        Map<String, String> ids = new LinkedHashMap<>();
        int index = 1;
        for (String name : names) {
            ids.put(name, "prop_%03d".formatted(index++));
        }
        return ids;
    }

    private List<Screenplay.Beat> buildBeats(Chapter chapter, Map<String, String> characterIds) {
        List<Screenplay.Beat> beats = new ArrayList<>();
        String[] paragraphs = chapter.content().split("\\n+");
        int index = 1;
        for (String paragraph : paragraphs) {
            String text = paragraph.trim();
            if (text.isBlank()) {
                continue;
            }

            Matcher dialogueMatcher = DIALOGUE.matcher(text);
            int cursor = 0;
            while (dialogueMatcher.find() && beats.size() < 12) {
                String action = text.substring(cursor, dialogueMatcher.start()).trim();
                if (!action.isBlank()) {
                    beats.add(beat(chapter, index++, "action", null, shorten(action, 180)));
                }
                String speaker = findSpeaker(text.substring(0, dialogueMatcher.start()), characterIds);
                beats.add(beat(chapter, index++, "dialogue", speaker, dialogueMatcher.group(1).trim()));
                cursor = dialogueMatcher.end();
            }
            if (cursor < text.length() && beats.size() < 12) {
                String action = text.substring(cursor).trim();
                if (!action.isBlank()) {
                    beats.add(beat(chapter, index++, "action", null, shorten(action, 180)));
                }
            }
            if (beats.size() >= 12) {
                break;
            }
        }
        if (beats.isEmpty()) {
            beats.add(beat(chapter, 1, "action", null, shorten(chapter.content(), 180)));
        }
        return beats;
    }

    private Screenplay.Beat beat(Chapter chapter, int index, String type, String characterId, String text) {
        return new Screenplay.Beat(
                chapter.id().replace("chapter", "beat") + "_%02d".formatted(index),
                type,
                characterId,
                null,
                text);
    }

    private String findSpeaker(String prefix, Map<String, String> characterIds) {
        String selected = null;
        int latest = -1;
        for (Map.Entry<String, String> entry : characterIds.entrySet()) {
            int position = prefix.lastIndexOf(entry.getKey());
            if (position > latest) {
                selected = entry.getValue();
                latest = position;
            }
        }
        return selected;
    }

    private String firstAppearance(String name, List<Chapter> chapters) {
        return chapters.stream()
                .filter(chapter -> chapter.content().contains(name))
                .map(Chapter::id)
                .findFirst()
                .orElse(chapters.getFirst().id());
    }

    private String detectLocation(String content, Set<String> knownLocations) {
        return knownLocations.stream()
                .filter(content::contains)
                .findFirst()
                .orElse("未明确地点");
    }

    private String detectSetting(String content) {
        return content.contains("广场") || content.contains("街") || content.contains("院子") ? "EXT" : "INT";
    }

    private String detectTime(String content) {
        if (content.contains("清晨") || content.contains("早晨")) {
            return "DAWN";
        }
        if (content.contains("夜") || content.contains("晚上")) {
            return "NIGHT";
        }
        return "DAY";
    }

    private String summarize(String content) {
        String plain = content.replaceAll("[“”\"\\s]+", " ").trim();
        String firstSentence = plain.split("[。！？!?]", 2)[0];
        return shorten(firstSentence, 80);
    }

    private String shorten(String text, int maxLength) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "…";
    }

    @Override
    public String mode() {
        return "RULE_BASED";
    }
}
