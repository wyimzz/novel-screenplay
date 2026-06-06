package com.example.screenplay.service;

import com.example.screenplay.model.Screenplay;

import java.util.List;

public final class ScreenplayFormatProfiles {

    private ScreenplayFormatProfiles() {
    }

    public static String normalize(String format) {
        return switch (format == null ? "" : format) {
            case "film", "tv_series", "web_series", "stage_play" -> format;
            default -> "web_series";
        };
    }

    public static Screenplay.FormatProfile profile(String format) {
        return switch (normalize(format)) {
            case "film" -> new Screenplay.FormatProfile(
                    110,
                    "三幕式长片：建置、对抗、高潮与收束",
                    "层层升级，允许较长的情绪积累与视觉段落",
                    List.of(
                            "每章规划 2-4 场，避免碎片化切场",
                            "每场建议 8-18 个 Beat，动作与潜台词充分展开",
                            "强化可视化动作、空间关系和电影化转场",
                            "结尾完成主冲突闭环，并保留人物余韵"));
            case "tv_series" -> new Screenplay.FormatProfile(
                    45,
                    "单集四幕式：冷开场、发展、中点升级、集尾悬念",
                    "信息与人物线并行推进，每幕都有转折",
                    List.of(
                            "每章规划 2-5 场，场景服务 A/B 线推进",
                            "每场建议 7-16 个 Beat",
                            "在约四分之一、二分之一、四分之三位置设置幕转折",
                            "结尾形成可驱动下一集的悬念或关系变化"));
            case "stage_play" -> new Screenplay.FormatProfile(
                    100,
                    "分幕舞台剧：以有限场地承载连续冲突",
                    "对白、停顿和演员调度主导，减少不可执行的外景跳切",
                    List.of(
                            "每章规划 1-3 场，优先复用地点并减少换景",
                            "每场建议 10-22 个 Beat，允许较完整的对白交锋",
                            "动作必须可在舞台上执行，写清出入场、站位和必要音效",
                            "避免依赖特写、快速蒙太奇和大量真实外景"));
            default -> new Screenplay.FormatProfile(
                    12,
                    "竖屏短剧单集：强钩子、快速升级、连续反转、卡点收尾",
                    "短场景、高信息密度，前置冲突并减少铺垫",
                    List.of(
                            "每章规划 3-6 场，单场目标单一且快速完成",
                            "每场建议 5-12 个 Beat，动作和对白简短有力",
                            "开场前 10 秒必须出现异常、目标或直接冲突",
                            "每 1-2 场设置一次信息反转，结尾使用强悬念卡点"));
        };
    }

    public static String prompt(String format) {
        Screenplay.FormatProfile profile = profile(format);
        return """
                目标媒介：%s
                目标成片时长：约 %d 分钟
                结构：%s
                节奏：%s
                强制媒介规则：
                - %s
                """.formatted(
                normalize(format),
                profile.targetDurationMinutes(),
                profile.structure(),
                profile.pacing(),
                String.join("\n- ", profile.constraints()));
    }

    public static Screenplay.FormatDesign sceneDesign(String format, int index, int totalScenes) {
        int safeTotal = Math.max(totalScenes, 1);
        int safeIndex = Math.max(0, Math.min(index, safeTotal - 1));
        int duration = Math.max(15, profile(format).targetDurationMinutes() * 60 / safeTotal);
        double position = safeTotal == 1 ? 0 : safeIndex / (double) (safeTotal - 1);

        return switch (normalize(format)) {
            case "film" -> new Screenplay.FormatDesign(
                    position < 0.25 ? "第一幕 · 建置"
                            : position < 0.75 ? "第二幕 · 对抗"
                            : "第三幕 · 高潮与收束",
                    "MAIN",
                    position == 0 ? "setup"
                            : safeIndex == safeTotal - 1 ? "resolution"
                            : position < 0.75 ? "escalation" : "climax",
                    duration,
                    List.of("以可视化动作和空间关系推进", "允许镜头、声音与电影化转场设计"));
            case "tv_series" -> new Screenplay.FormatDesign(
                    televisionSection(safeIndex, safeTotal),
                    safeIndex % 3 == 1 ? "B_STORY" : "A_STORY",
                    safeIndex == 0 ? "hook"
                            : safeIndex == safeTotal - 1 ? "cliffhanger" : "act_turn",
                    duration,
                    List.of("标明 A/B 故事线归属", "场尾形成幕转折或推动下一条人物线"));
            case "stage_play" -> {
                int act = position < 0.5 ? 1 : 2;
                int sceneInAct = act == 1 ? safeIndex + 1 : safeIndex - (safeTotal / 2) + 1;
                yield new Screenplay.FormatDesign(
                        "第" + chineseNumber(act) + "幕 · 第" + chineseNumber(Math.max(sceneInAct, 1)) + "场",
                        "ENSEMBLE",
                        safeIndex == 0 ? "setup"
                                : safeIndex == safeTotal - 1 ? "resolution" : "confrontation",
                        duration,
                        List.of("动作必须能在舞台上执行", "标明出入场、站位、灯光、音效或换景需求"));
            }
            default -> new Screenplay.FormatDesign(
                    safeIndex == 0 ? "HOOK · 前10秒"
                            : safeIndex == safeTotal - 1 ? "CLIFFHANGER · 卡点"
                            : safeIndex % 2 == 0 ? "REVERSAL · 反转" : "ESCALATION · 升级",
                    "MAIN",
                    safeIndex == 0 ? "hook"
                            : safeIndex == safeTotal - 1 ? "cliffhanger"
                            : safeIndex % 2 == 0 ? "reversal" : "escalation",
                    Math.min(duration, 120),
                    List.of("首屏直接进入目标、异常或冲突", "对白短句化，场尾保留信息反转或卡点"));
        };
    }

    public static String sceneDesignPrompt(String format) {
        return switch (normalize(format)) {
            case "film" -> """
                    场景 formatDesign 必须体现电影长片结构：
                    - sectionLabel 使用“第一幕 · 建置 / 第二幕 · 对抗 / 第三幕 · 高潮与收束”
                    - storyLine 通常为 MAIN；dramaticFunction 使用 setup、inciting_incident、escalation、midpoint、climax、resolution
                    - productionNotes 写摄影可见的空间、镜头、声音或转场重点，不写电视剧幕尾和舞台调度
                    """;
            case "tv_series" -> """
                    场景 formatDesign 必须体现电视剧单集结构：
                    - sectionLabel 使用 TEASER、ACT ONE、ACT TWO、ACT THREE、ACT FOUR、TAG
                    - storyLine 必须标为 A_STORY、B_STORY 或 C_STORY
                    - dramaticFunction 强调 hook、act_turn、reveal、cliffhanger；每幕末场必须形成明确转折
                    - productionNotes 说明本场如何推进故事线及如何接入下一幕或另一条线
                    """;
            case "stage_play" -> """
                    场景 formatDesign 必须体现舞台剧结构：
                    - sectionLabel 使用“第一幕 · 第一场”一类幕/场标记
                    - storyLine 使用 ENSEMBLE 或人物线名称
                    - dramaticFunction 强调 entrance、confrontation、reversal、exit、resolution
                    - productionNotes 必须包含可执行的出入场、站位、灯光、音效或换景提示，禁止依赖特写和蒙太奇
                    """;
            default -> """
                    场景 formatDesign 必须体现竖屏短剧结构：
                    - sectionLabel 从 HOOK · 前10秒、ESCALATION · 升级、REVERSAL · 反转、CLIFFHANGER · 卡点中选择
                    - storyLine 通常为 MAIN；dramaticFunction 使用 hook、escalation、reversal、payoff、cliffhanger
                    - estimatedDurationSeconds 建议 15-120 秒
                    - productionNotes 说明首屏冲突、竖屏视觉焦点、短句对白、反转或卡点设计
                    """;
        };
    }

    private static String televisionSection(int index, int totalScenes) {
        if (index == 0) {
            return "TEASER · 冷开场";
        }
        if (index == totalScenes - 1) {
            return "ACT FOUR · 集尾悬念";
        }
        double position = index / (double) Math.max(totalScenes - 1, 1);
        if (position < 0.35) {
            return "ACT ONE";
        }
        if (position < 0.6) {
            return "ACT TWO";
        }
        if (position < 0.82) {
            return "ACT THREE";
        }
        return "ACT FOUR";
    }

    private static String chineseNumber(int value) {
        return switch (value) {
            case 1 -> "一";
            case 2 -> "二";
            case 3 -> "三";
            case 4 -> "四";
            default -> Integer.toString(value);
        };
    }
}
