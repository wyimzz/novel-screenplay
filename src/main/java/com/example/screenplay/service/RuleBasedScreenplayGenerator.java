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

    private static final String COMMON_SURNAMES =
            "赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜"
                    + "戚谢邹喻柏水窦章云苏潘葛奚范彭郎鲁韦昌马苗凤花方俞任袁柳"
                    + "鲍史唐费廉岑薛雷贺倪汤滕殷罗毕郝邬安常乐于时傅皮卞齐康"
                    + "伍余元卜顾孟平黄和穆萧尹姚邵湛汪祁毛禹狄米贝明臧计伏成"
                    + "戴谈宋茅庞熊纪舒屈项祝董梁杜阮蓝闵席季麻强贾路娄危江童"
                    + "颜郭梅盛林刁钟徐邱骆高夏蔡田樊胡凌霍虞万支柯管卢莫经房"
                    + "裘缪干解应宗丁宣邓郁单杭洪包诸左石崔吉龚程邢滑裴陆荣翁"
                    + "荀羊甄曲封芮储靳汲邴糜松井段富巫乌焦巴弓牧隗山谷车侯宓"
                    + "蓬全郗班仰秋仲伊宫宁仇栾暴甘钭厉戎祖武符刘景詹束龙叶幸"
                    + "司韶郜黎蓟薄印宿白怀蒲邰从鄂索咸籍赖卓蔺屠蒙池乔阴胥能"
                    + "苍双闻莘党翟谭贡劳逄姬申扶堵冉宰郦雍郤璩桑桂濮牛寿通边"
                    + "扈燕冀郏浦尚农温别庄晏柴瞿阎充慕连茹习宦艾鱼容向古易慎"
                    + "戈廖庾终暨居衡步都耿满弘匡国文寇广禄阙东欧利蔚越夔隆师"
                    + "巩厍聂晁勾敖融冷訾辛阚那简饶空曾毋沙乜养鞠须丰巢关蒯相"
                    + "查后荆红游竺权逯盖益桓公";
    private static final String PERSON_TOKEN =
            "(?:欧阳|司马|上官|诸葛|夏侯|东方|皇甫|尉迟|公孙|慕容|司徒|令狐|宇文|长孙|司空|南宫"
                    + "|[" + COMMON_SURNAMES + "])[\\p{IsHan}]{1,2}?";
    private static final Pattern SPEAKER_NAME = Pattern.compile(
            "(?:^|[。！？!?，,；;\\n])(" + PERSON_TOKEN + ")"
                    + "(?=[^，。！？!?；;“”\"\\n]{0,12}(?:说道|说|问|喊|答|道|叫)[：:]?[“\"])",
            Pattern.MULTILINE);
    private static final Pattern DIALOGUE = Pattern.compile("[“\"]([^”\"\\n]{2,120})[”\"]");
    private static final Pattern LOCATION = Pattern.compile(
            "(?:来到|走进|进入|回到|赶到|站在)([\\p{IsHan}]{2,10}(?:室|厅|店|馆|院|楼|房|街|站|场|园|门|办公室|咖啡馆|餐厅))");
    private static final List<String> LOCATION_DICTIONARY = List.of(
            "广场", "山崖", "树林", "房间", "大厅", "卧室", "客厅", "书房", "院子",
            "街道", "办公室", "咖啡馆", "餐厅", "车站", "校园", "医院", "公园",
            "酒吧", "酒店", "仓库", "天台", "码头", "机场", "教室", "会议室");
    private static final Set<String> INVALID_CHARACTER_NAMES = Set.of(
            "萧家", "林中", "林间", "林外", "王者", "王族", "主人", "人物", "老人",
            "少年", "少女", "男子", "女子", "父亲", "母亲", "先生", "小姐");
    private static final List<String> NAME_SUFFIXES = List.of(
            "哥哥", "姐姐", "弟弟", "妹妹", "先生", "小姐", "少爷", "长老", "族长", "管家");
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
                    ScreenplayFormatProfiles.sceneDesign(format, i, chapters.size()),
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
                        ScreenplayFormatProfiles.normalize(format),
                        chapters.size(),
                        ScreenplayFormatProfiles.profile(format)),
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
            Matcher matcher = SPEAKER_NAME.matcher(chapter.content());
            while (matcher.find()) {
                String candidate = normalizeCharacterName(matcher.group(1));
                if (isLikelyCharacterName(candidate)) {
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
            for (String location : LOCATION_DICTIONARY) {
                if (chapter.content().contains(location)) {
                    names.add(location);
                }
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
                String prefix = text.substring(0, dialogueMatcher.start());
                String suffix = text.substring(dialogueMatcher.end());
                String speaker = findSpeaker(prefix, suffix, characterIds);
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

    private String findSpeaker(String prefix, String suffix, Map<String, String> characterIds) {
        String selected = null;
        int latest = -1;
        for (Map.Entry<String, String> entry : characterIds.entrySet()) {
            int position = prefix.lastIndexOf(entry.getKey());
            if (position > latest) {
                selected = entry.getValue();
                latest = position;
            }
        }
        if (selected != null && prefix.length() - latest <= 40) {
            return selected;
        }
        String nearbySuffix = suffix.substring(0, Math.min(suffix.length(), 30));
        for (Map.Entry<String, String> entry : characterIds.entrySet()) {
            if (nearbySuffix.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return selected;
    }

    private String normalizeCharacterName(String candidate) {
        String normalized = candidate;
        for (String suffix : NAME_SUFFIXES) {
            if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
                normalized = normalized.substring(0, normalized.length() - suffix.length());
            } else if (normalized.endsWith(suffix.substring(0, 1)) && normalized.length() > 2) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
        }
        return normalized;
    }

    private boolean isLikelyCharacterName(String candidate) {
        return candidate.length() >= 2
                && candidate.length() <= 4
                && !INVALID_CHARACTER_NAMES.contains(candidate)
                && !candidate.endsWith("家")
                && !candidate.endsWith("城")
                && !candidate.endsWith("国")
                && !candidate.endsWith("族");
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
