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
}
