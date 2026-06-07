package com.example.screenplay.service;

import com.example.screenplay.model.Chapter;
import com.example.screenplay.model.GenerationProgress;
import com.example.screenplay.model.Screenplay;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class AiScreenplayGenerator implements ScreenplayGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiScreenplayGenerator.class);

    private final AiSettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiScreenplayGenerator(AiSettingsService settingsService, ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public boolean available() {
        return settingsService.current().available();
    }

    @Override
    public Screenplay generate(String title, String format, List<Chapter> chapters) {
        return generate(title, format, chapters, progress -> {
        });
    }

    @Override
    public Screenplay generate(
            String title,
            String format,
            List<Chapter> chapters,
            Consumer<GenerationProgress> progress
    ) {
        if (!available()) {
            throw new IllegalStateException("AI 模型尚未配置");
        }
        AiSettingsService.RuntimeAiSettings settings = settingsService.current();

        try {
            String source = chapterSource(chapters);
            progress.accept(new GenerationProgress("assets", "正在提取人物、地点和关键道具", 15));
            long extractionStarted = System.nanoTime();
            LOGGER.info("AI Story Bible extraction started: model={}, chapters={}",
                    settings.model(), chapters.size());
            String storyBible = callModel(
                    settings,
                    entityExtractionPrompt(),
                    "作品名称：" + title + "\n\n小说原文：\n" + source,
                    0.1,
                    3000);
            LOGGER.info("AI Story Bible extraction completed in {} ms",
                    elapsedMillis(extractionStarted));
            JsonNode storyBibleNode = parseOrRepairJson(
                    settings,
                    storyBible,
                    "Story Bible 必须是包含 characters、locations、props 的 JSON 对象");
            normalizeStoryBible(storyBibleNode, chapters);
            Screenplay assetPreview = buildPreview(
                    title, format, chapters, storyBibleNode, List.of());
            progress.accept(new GenerationProgress(
                    "assets",
                    "故事圣经已完成，正在规划场景",
                    25,
                    assetPreview));

            progress.accept(new GenerationProgress("plan", "正在规划全书场景与戏剧节奏", 32));
            String adaptationPlan = callModel(
                    settings,
                    adaptationPlanPrompt(),
                    adaptationPlanUserPrompt(title, format, chapters, storyBibleNode),
                    0.15,
                    4000);
            JsonNode adaptationPlanNode = parseOrRepairJson(
                    settings,
                    adaptationPlan,
                    "改编蓝图必须是包含 chapters 数组的 JSON 对象");
            normalizeAdaptationPlan(adaptationPlanNode, chapters);

            Screenplay screenplay = generateChapterDrafts(
                    settings,
                    title,
                    format,
                    chapters,
                    storyBibleNode,
                    adaptationPlanNode,
                    progress);
            return mergeStoryBible(screenplay, storyBibleNode);
        } catch (IOException exception) {
            throw new IllegalStateException("AI 响应解析失败：" + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI 请求被中断", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException("AI 分章生成失败：" + cause.getMessage(), cause);
        }
    }

    private Screenplay generateChapterDrafts(
            AiSettingsService.RuntimeAiSettings settings,
            String title,
            String format,
            List<Chapter> chapters,
            JsonNode storyBible,
            JsonNode adaptationPlan,
            Consumer<GenerationProgress> progress
    ) throws IOException, InterruptedException, ExecutionException {
        long startedAt = System.nanoTime();
        LOGGER.info("AI detailed chapter adaptation started: model={}, chapters={}",
                settings.model(), chapters.size());

        int concurrency = chapterConcurrency(settings.baseUrl(), chapters.size());
        progress.accept(new GenerationProgress("scenes", "正在生成各章动作与对白", 42));
        List<ChapterDraft> completedDrafts = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(concurrency)) {
            CompletionService<ChapterDraft> completionService = new ExecutorCompletionService<>(executor);
            for (int index = 0; index < chapters.size(); index++) {
                int chapterIndex = index;
                Chapter chapter = chapters.get(index);
                completionService.submit(() -> new ChapterDraft(
                        chapterIndex,
                        chapter,
                        generateChapterDraft(settings, title, format, chapter, storyBible, adaptationPlan)));
            }
            for (int done = 1; done <= chapters.size(); done++) {
                completedDrafts.add(completionService.take().get());
                completedDrafts.sort(Comparator.comparingInt(ChapterDraft::index));
                int percent = 42 + (int) Math.round(done * 48.0 / chapters.size());
                Screenplay preview = buildPreview(
                        title, format, chapters, storyBible, completedDrafts);
                progress.accept(new GenerationProgress(
                        "scenes",
                        "已完成 " + done + " / " + chapters.size() + " 章详细剧本",
                        percent,
                        preview));
            }
        }

        LOGGER.info("AI detailed chapter adaptation completed in {} ms", elapsedMillis(startedAt));
        return buildPreview(title, format, chapters, storyBible, completedDrafts);
    }

    private JsonNode generateChapterDraft(
            AiSettingsService.RuntimeAiSettings settings,
            String title,
            String format,
            Chapter chapter,
            JsonNode storyBible,
            JsonNode adaptationPlan
    ) throws IOException, InterruptedException {
        long startedAt = System.nanoTime();
        String content = callModel(
                settings,
                chapterScreenplayPrompt(),
                chapterUserPrompt(title, format, chapter, storyBible, adaptationPlan),
                0.2,
                4500);
        LOGGER.info("AI chapter adaptation completed: chapter={}, elapsedMs={}",
                chapter.id(), elapsedMillis(startedAt));
        JsonNode result = parseOrRepairJson(
                settings,
                content,
                "章节剧本必须是包含 scenes、adaptationNotes 的 JSON 对象");
        return result;
    }

    private Screenplay buildPreview(
            String title,
            String format,
            List<Chapter> chapters,
            JsonNode storyBible,
            List<ChapterDraft> drafts
    ) throws IOException {
        ObjectNode combined = objectMapper.createObjectNode();
        combined.put("schemaVersion", "1.0");
        combined.set("characters", storyBible.path("characters").deepCopy());
        combined.set("locations", storyBible.path("locations").deepCopy());
        combined.set("props", storyBible.path("props").deepCopy());
        ArrayNode scenes = combined.putArray("scenes");
        ArrayNode notes = combined.putArray("adaptationNotes");

        for (ChapterDraft draft : drafts) {
            JsonNode chapterScenes = draft.content().path("scenes");
            if (chapterScenes.isArray()) {
                for (JsonNode sceneNode : chapterScenes) {
                    if (sceneNode instanceof ObjectNode scene) {
                        ObjectNode sceneCopy = scene.deepCopy();
                        normalizeStringArray(sceneCopy, "sourceChapterIds", draft.chapter().id());
                        scenes.add(sceneCopy);
                    }
                }
            }
            JsonNode chapterNotes = draft.content().path("adaptationNotes");
            if (chapterNotes.isArray()) {
                chapterNotes.forEach(note -> notes.add(note.deepCopy()));
            }
        }

        return parseScreenplayContent(
                objectMapper.writeValueAsString(combined),
                title,
                format,
                chapters.size());
    }

    private record ChapterDraft(int index, Chapter chapter, JsonNode content) {
    }

    private JsonNode parseOrRepairJson(
            AiSettingsService.RuntimeAiSettings settings,
            String content,
            String requirement
    ) throws IOException, InterruptedException {
        try {
            return objectMapper.readTree(extractJsonObject(content));
        } catch (IOException firstFailure) {
            LOGGER.warn("AI JSON parse failed, requesting one repair: {}", firstFailure.getMessage());
            String repaired = callModel(
                    settings,
                    """
                            你是 JSON 修复器。只输出修复后的完整 JSON 对象，不要 Markdown，不要解释。
                            不得删减原响应中的有效剧情内容；修正代码围栏、前后说明、缺失引号、
                            尾逗号、错误字段容器和未闭合结构。%s
                            """.formatted(requirement),
                    "待修复响应：\n" + content,
                    0,
                    5000);
            return objectMapper.readTree(extractJsonObject(repaired));
        }
    }

    private String callModel(
            AiSettingsService.RuntimeAiSettings settings,
            String systemPrompt,
            String userPrompt,
            double temperature,
            int maxTokens
    ) throws IOException, InterruptedException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", settings.model());
        request.put("temperature", temperature);
        request.put("max_tokens", maxTokens);
        settingsService.addProviderOptions(request, settings.baseUrl(), settings.model());
        if (settings.baseUrl().toLowerCase().contains("deepseek")) {
            request.put("response_format", Map.of("type", "json_object"));
        }
        request.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        HttpRequest.Builder httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(normalizeBaseUrl(settings.baseUrl()) + "/chat/completions"))
                .timeout(Duration.ofSeconds(Math.min(Math.max(settings.timeoutSeconds(), 30), 600)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)));
        if (settings.apiKey() != null && !settings.apiKey().isBlank()) {
            httpRequest.header("Authorization", "Bearer " + settings.apiKey());
        }
        HttpResponse<String> response = sendWithRetry(httpRequest);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String message = objectMapper.readTree(response.body()).path("error").path("message").asText();
            throw new IllegalStateException(
                    message.isBlank() ? "AI 请求失败，HTTP " + response.statusCode() : "AI 请求失败：" + message);
        }

        JsonNode responseRoot = objectMapper.readTree(response.body());
        JsonNode choice = responseRoot.path("choices").path(0);
        JsonNode messageNode = choice.path("message");
        String content = messageNode.path("content").asText();
        if (content.isBlank()) {
            String finishReason = choice.path("finish_reason").asText("unknown");
            int reasoningLength = messageNode.path("reasoning_content").asText().length();
            throw new IllegalStateException(
                    "AI 返回内容为空（finish_reason=%s，reasoning_length=%d）。"
                            .formatted(finishReason, reasoningLength)
                            + "请检查模型是否启用了深度思考，或改用支持关闭 Thinking 的模型。");
        }
        return content;
    }

    int chapterConcurrency(String baseUrl, int chapterCount) {
        String value = baseUrl == null ? "" : baseUrl.toLowerCase();
        return value.contains("bigmodel.cn") ? 1 : Math.min(3, chapterCount);
    }

    private HttpResponse<String> sendWithRetry(HttpRequest.Builder requestBuilder)
            throws IOException, InterruptedException {
        HttpResponse<String> response = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 429 && response.statusCode() != 503) {
                return response;
            }
            if (attempt < 3) {
                long delaySeconds = response.headers()
                        .firstValue("Retry-After")
                        .map(this::parseRetryDelay)
                        .orElse((long) attempt * 2);
                LOGGER.warn("AI request rate limited: status={}, retryIn={}s, attempt={}/3",
                        response.statusCode(), delaySeconds, attempt);
                Thread.sleep(Math.min(delaySeconds, 10) * 1000);
            }
        }
        return response;
    }

    private long parseRetryDelay(String value) {
        try {
            return Math.max(1, Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return 2;
        }
    }

    private String entityExtractionPrompt() {
        return """
                你是专业影视编剧助理。此阶段只建立 Story Bible，不生成场景或对白。
                分析小说并输出严格 JSON，顶层只能包含 characters、locations、props。
                所有描述必须使用简体中文，不要输出 Markdown。

                characters 每项必须包含：
                id(char_001起)、name、description（稳定外貌与性格，不写临时动作）、
                age、gender、clothing、visualWeight(1-5)、firstAppearance(chapter_01格式)。
                人物发生显著年龄或服装变化时，可创建变体并在名字中标注。

                locations 每项必须包含：
                id(loc_001起)、name、description（空间、陈设、氛围）、
                timeOfDay、lightingMood、visualWeight(1-5)。

                props 每项必须包含：
                id(prop_001起)、name、description、storyFunction、firstAppearance。
                只抽取影响情节、人物关系或可见行动的重要道具。

                不要虚构原文没有的实体。ID 必须唯一、连续、稳定。
                """;
    }

    private String chapterScreenplayPrompt() {
        return """
                你是专业影视编剧。请把当前单章小说改编成可直接继续打磨的详细剧本场景。
                输出严格 JSON，不要 Markdown，不要解释。顶层只能包含 scenes、adaptationNotes。
                必须使用给定 Story Bible 中的角色、地点和道具 ID，不得擅自改 ID。
                每个场景必须包含 sourceChapterIds、heading、purpose、characters、props、formatDesign、beats、continuity、sourceFidelity。
                formatDesign 必须是对象，包含 sectionLabel、storyLine、dramaticFunction、
                estimatedDurationSeconds、productionNotes。
                continuity 必须是对象：{"previousSceneId": null或场景ID, "nextSceneId": null或场景ID}，
                严禁把 continuity 输出为“开篇场景”“承接上一幕”等自然语言字符串。
                heading.setting 只能是 INT、EXT、INT/EXT；time 只能是 DAWN、DAY、DUSK、NIGHT、CONTINUOUS、UNKNOWN。
                beat.type 只能是 action、dialogue、voice_over、narration、transition、shot、note。
                所有引用 ID 必须真实存在。尽量忠于原文，新增内容必须通过 inventedContent 和 adaptationNotes 标明。

                改编质量规则：
                1. 场景数量与 Beat 数量严格遵守用户消息中的媒介规格；
                   地点、时间、主要行动目标变化时仍应合理拆场。
                2. 每场必须同时包含可执行 action 和关键 dialogue；
                   不得用统一模板抹平电影、电视剧、短剧和舞台剧的节奏差异。
                3. 原文存在引号对白时，优先保留有剧情功能、人物关系或情绪转折的原话，
                   禁止把整段对话概括成“众人议论”“两人交谈”。
                4. dialogue 必须填写 characterId 和 text；必要时用 parenthetical 表示语气或小动作。
                5. action 必须是演员可表演、摄影机可拍摄、声音可听见的内容。
                   内心活动改写为表情、动作、停顿、视线、画外音或对白。
                6. 群体嘲笑、围观反应等可拆成 action 与代表性 dialogue，保留戏剧节奏。
                7. 每场 purpose 要说明冲突、信息揭示或人物变化，不能只写地点概述。
                8. sourceFidelity.evidence 使用不超过 80 字的原文依据。
                9. 输出的是详细可编辑初稿，不是故事梗概；不得只生成一两条概述性 beat。
                10. 必须严格遵守用户消息中的场景级媒介结构，不能把四种剧本写成同一种节奏。

                Beat 必须严格使用以下格式，禁止使用 action_description、speaker、content 等别名：
                {"id":"beat_001_01","type":"action","characterId":null,"parenthetical":null,
                 "text":"萧炎盯着石碑上的三段二字，指甲缓缓陷入掌心。"}
                {"id":"beat_001_02","type":"dialogue","characterId":"char_001","parenthetical":"低声",
                 "text":"三十年河东，三十年河西。"}

                adaptationNotes 必须是对象数组，每项严格包含：
                {"sceneId":"scene_001","type":"compression|invention|reorder|ai_adaptation","description":"说明"}。
                即使只有一句说明也禁止直接输出字符串；没有改编说明时输出空数组。
                """;
    }

    private String adaptationPlanPrompt() {
        return """
                你是影视剧本统筹。基于完整小说和已确认 Story Bible，先制作全书改编蓝图，
                不写最终对白，不输出 Markdown，只输出严格 JSON。
                顶层结构必须是：
                {
                  "logline":"一句话主线",
                  "dramaticArc":"全书戏剧推进说明",
                  "chapters":[{
                    "chapterId":"chapter_01",
                    "summary":"本章关键事件与人物变化",
                    "entryState":"本章开始时人物与信息状态",
                    "exitState":"本章结束时人物与信息状态",
                    "scenePlan":[{
                      "order":1,
                      "location":"地点名称",
                      "time":"DAY|NIGHT|DAWN|DUSK|CONTINUOUS|UNKNOWN",
                      "purpose":"冲突、揭示或人物变化",
                      "sectionLabel":"该媒介的幕、段或钩子标记",
                      "storyLine":"MAIN、A_STORY、B_STORY、C_STORY或ENSEMBLE",
                      "dramaticFunction":"本场戏剧功能",
                      "estimatedDurationSeconds":60,
                      "productionNotes":["该媒介的制作或执行重点"],
                      "mustKeepActions":["必须保留的可见动作"],
                      "mustKeepDialogues":["必须保留或准确转述的关键原文对白"],
                      "characters":["char_001"],
                      "props":["prop_001"]
                    }]
                  }]
                }
                每个输入章节都必须恰好对应一个 chapters 项；
                每章场景数量必须遵守用户消息中的媒介规格。
                scenePlan 中的 sectionLabel、storyLine、dramaticFunction、
                estimatedDurationSeconds、productionNotes 必须遵守用户消息中的场景级媒介结构。
                场景按原文事件顺序排列，明确前后状态，避免跨章重复和人物瞬移。
                引用只能使用 Story Bible 中已有 ID。原文没有对白时不要虚构长对白。
                """;
    }

    private String chapterSource(List<Chapter> chapters) {
        StringBuilder source = new StringBuilder();
        for (Chapter chapter : chapters) {
            source.append("\n\n[").append(chapter.id()).append("] ")
                    .append(chapter.title()).append("\n")
                    .append(chapter.content());
        }
        return source.toString();
    }

    private String chapterUserPrompt(
            String title,
            String format,
            Chapter chapter,
            JsonNode storyBible,
            JsonNode adaptationPlan
    ) throws IOException {
        return """
                作品名称：%s
                目标形式：%s
                原文语言：zh-CN
                当前章节 ID：%s
                当前章节标题：%s

                必须遵守的媒介规格：
                %s

                场景级媒介结构：
                %s

                已确认 Story Bible：
                %s

                已确认全书改编蓝图：
                %s

                当前章节完整原文：
                %s
                """.formatted(
                title,
                ScreenplayFormatProfiles.normalize(format),
                chapter.id(),
                chapter.title(),
                ScreenplayFormatProfiles.prompt(format),
                ScreenplayFormatProfiles.sceneDesignPrompt(format),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(storyBible),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(adaptationPlan),
                chapter.content());
    }

    private String adaptationPlanUserPrompt(
            String title,
            String format,
            List<Chapter> chapters,
            JsonNode storyBible
    ) throws IOException {
        return """
                作品名称：%s
                目标形式：%s

                必须遵守的媒介规格：
                %s

                场景级媒介结构：
                %s

                Story Bible：
                %s

                带稳定章节 ID 的完整原文：
                %s
                """.formatted(
                title,
                ScreenplayFormatProfiles.normalize(format),
                ScreenplayFormatProfiles.prompt(format),
                ScreenplayFormatProfiles.sceneDesignPrompt(format),
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(storyBible),
                chapterSource(chapters));
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com/v1" : baseUrl.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed;
    }

    private String extractJsonObject(String content) {
        String trimmed = stripCodeFence(content);
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private void normalizeAdaptationPlan(JsonNode root, List<Chapter> chapters) {
        if (!(root instanceof ObjectNode plan)) {
            throw new IllegalArgumentException("AI 返回的改编蓝图顶层必须是 JSON 对象");
        }
        JsonNode chapterPlansNode = plan.get("chapters");
        ArrayNode chapterPlans = chapterPlansNode instanceof ArrayNode array
                ? array
                : objectMapper.createArrayNode();
        Map<String, JsonNode> plansByChapter = new LinkedHashMap<>();
        for (JsonNode chapterPlan : chapterPlans) {
            String chapterId = chapterPlan.path("chapterId").asText();
            if (!chapterId.isBlank()) {
                plansByChapter.put(chapterId, chapterPlan);
            }
        }
        ArrayNode normalized = objectMapper.createArrayNode();
        for (Chapter chapter : chapters) {
            JsonNode existing = plansByChapter.get(chapter.id());
            ObjectNode chapterPlan = existing instanceof ObjectNode object
                    ? object
                    : objectMapper.createObjectNode();
            chapterPlan.put("chapterId", chapter.id());
            putDefaultText(chapterPlan, "summary", chapter.title());
            putDefaultText(chapterPlan, "entryState", "承接前文状态");
            putDefaultText(chapterPlan, "exitState", "进入下一章状态");
            ensureArray(chapterPlan, "scenePlan");
            normalized.add(chapterPlan);
        }
        plan.set("chapters", normalized);
        putDefaultText(plan, "logline", "根据小说原文推进的影视改编");
        putDefaultText(plan, "dramaticArc", "按章节顺序推进冲突与人物变化");
    }

    Screenplay parseScreenplayContent(String content) throws IOException {
        return parseScreenplayContent(content, "未命名作品", "web_series", 3);
    }

    Screenplay parseScreenplayContent(
            String content,
            String title,
            String format,
            int chapterCount
    ) throws IOException {
        JsonNode root = objectMapper.readTree(stripCodeFence(content));
        normalizeScreenplay(root, title, format, chapterCount);
        return objectMapper.treeToValue(root, Screenplay.class);
    }

    private void normalizeScreenplay(JsonNode root, String title, String format, int chapterCount) {
        if (!(root instanceof ObjectNode objectRoot)) {
            throw new IllegalArgumentException("AI 返回的剧本顶层必须是 JSON 对象");
        }

        objectRoot.put("schemaVersion", "1.0");
        String normalizedFormat = ScreenplayFormatProfiles.normalize(format);
        normalizeProject(objectRoot, title, format, chapterCount);
        ensureArray(objectRoot, "characters");
        ensureArray(objectRoot, "locations");
        ensureArray(objectRoot, "props");
        ensureArray(objectRoot, "scenes");
        normalizeAdaptationNotes(objectRoot);
        Map<String, String> characterReferences = entityReferenceMap((ArrayNode) objectRoot.get("characters"));
        Map<String, String> propReferences = entityReferenceMap((ArrayNode) objectRoot.get("props"));
        Map<String, String> locationNames = entityNameMap((ArrayNode) objectRoot.get("locations"));

        ArrayNode scenes = (ArrayNode) objectRoot.get("scenes");
        for (int index = 0; index < scenes.size(); index++) {
            if (scenes.get(index) instanceof ObjectNode scene) {
                scene.put("id", "scene_%03d".formatted(index + 1));
            }
        }

        for (int index = 0; index < scenes.size(); index++) {
            JsonNode sceneNode = scenes.get(index);
            if (!(sceneNode instanceof ObjectNode scene)) {
                continue;
            }

            normalizeStringArray(scene, "sourceChapterIds", "chapter_%02d".formatted(
                    Math.min(index + 1, Math.max(chapterCount, 1))));
            normalizeHeading(scene, locationNames);
            putDefaultText(scene, "purpose", "推进故事情节");
            normalizeReferenceArray(scene, "characters", characterReferences);
            normalizeReferenceArray(scene, "props", propReferences);
            normalizeFormatDesign(scene, normalizedFormat, index, scenes.size());
            normalizeBeats(scene, index, characterReferences);

            String previousId = index == 0 ? null : sceneIdAt(scenes, index - 1);
            String nextId = index == scenes.size() - 1 ? null : sceneIdAt(scenes, index + 1);
            JsonNode continuityNode = scene.get("continuity");

            ObjectNode continuity;
            if (continuityNode instanceof ObjectNode existingContinuity) {
                continuity = existingContinuity;
            } else {
                continuity = objectMapper.createObjectNode();
                scene.set("continuity", continuity);
            }
            putNullable(continuity, "previousSceneId", previousId);
            putNullable(continuity, "nextSceneId", nextId);

            JsonNode fidelityNode = scene.get("sourceFidelity");
            ObjectNode fidelity;
            if (fidelityNode instanceof ObjectNode existingFidelity) {
                fidelity = existingFidelity;
            } else {
                fidelity = objectMapper.createObjectNode();
                fidelity.put("confidence", 0.5);
                fidelity.put("inventedContent", true);
                scene.set("sourceFidelity", fidelity);
            }
            if (!fidelity.has("evidence")) {
                fidelity.put("evidence", "");
            }
            double confidence = fidelity.path("confidence").asDouble(0.5);
            fidelity.put("confidence", Math.max(0, Math.min(confidence, 1)));
            if (!fidelity.has("inventedContent") || !fidelity.get("inventedContent").isBoolean()) {
                fidelity.put("inventedContent", false);
            }
        }
    }

    private void normalizeFormatDesign(
            ObjectNode scene,
            String format,
            int index,
            int totalScenes
    ) {
        Screenplay.FormatDesign fallback = ScreenplayFormatProfiles.sceneDesign(
                format, index, totalScenes);
        ObjectNode design;
        JsonNode designNode = scene.get("formatDesign");
        if (designNode instanceof ObjectNode objectDesign) {
            design = objectDesign;
        } else {
            design = objectMapper.createObjectNode();
            scene.set("formatDesign", design);
        }
        String sectionLabel = design.path("sectionLabel").asText();
        if (!matchesFormatSection(format, sectionLabel, index, totalScenes)) {
            design.put("sectionLabel", fallback.sectionLabel());
        }
        putDefaultText(design, "storyLine", fallback.storyLine());
        putDefaultText(design, "dramaticFunction", fallback.dramaticFunction());
        int duration = design.path("estimatedDurationSeconds").asInt(
                fallback.estimatedDurationSeconds());
        int maximum = "web_series".equals(format) ? 120 : 3600;
        design.put("estimatedDurationSeconds", Math.max(10, Math.min(duration, maximum)));
        JsonNode notesNode = design.get("productionNotes");
        if (!(notesNode instanceof ArrayNode notes) || notes.isEmpty()) {
            design.set("productionNotes", objectMapper.valueToTree(fallback.productionNotes()));
        }
    }

    private boolean matchesFormatSection(
            String format,
            String sectionLabel,
            int index,
            int totalScenes
    ) {
        if (sectionLabel == null || sectionLabel.isBlank()) {
            return false;
        }
        String normalized = sectionLabel.toUpperCase();
        return switch (format) {
            case "film" -> sectionLabel.contains("幕") || normalized.contains("ACT");
            case "tv_series" -> normalized.contains("TEASER")
                    || normalized.contains("ACT")
                    || normalized.contains("TAG");
            case "stage_play" -> sectionLabel.contains("幕") && sectionLabel.contains("场");
            default -> index == 0
                    ? normalized.contains("HOOK")
                    : index == totalScenes - 1
                    ? normalized.contains("CLIFFHANGER")
                    : normalized.contains("ESCALATION")
                    || normalized.contains("REVERSAL")
                    || normalized.contains("PAYOFF");
        };
    }

    private void normalizeProject(ObjectNode root, String title, String format, int chapterCount) {
        ObjectNode project;
        JsonNode projectNode = root.get("project");
        if (projectNode instanceof ObjectNode existingProject) {
            project = existingProject;
        } else {
            project = objectMapper.createObjectNode();
            if (projectNode != null && projectNode.isTextual() && !projectNode.asText().isBlank()) {
                project.put("title", projectNode.asText());
            }
            root.set("project", project);
        }

        project.put("title", title == null || title.isBlank()
                ? project.path("title").asText("未命名作品")
                : title);
        project.put("sourceLanguage", "zh-CN");
        String normalizedFormat = ScreenplayFormatProfiles.normalize(format);
        project.put("format", normalizedFormat);
        project.put("sourceChapterCount", Math.max(chapterCount, 3));
        project.set(
                "formatProfile",
                objectMapper.valueToTree(ScreenplayFormatProfiles.profile(normalizedFormat)));
    }

    private void normalizeHeading(ObjectNode scene, Map<String, String> locationNames) {
        JsonNode headingNode = scene.get("heading");
        ObjectNode heading;
        if (headingNode instanceof ObjectNode existingHeading) {
            heading = existingHeading;
        } else {
            heading = objectMapper.createObjectNode();
            if (headingNode != null && headingNode.isTextual()) {
                heading.put("location", headingNode.asText());
            }
            scene.set("heading", heading);
        }
        String setting = heading.path("setting").asText("INT").toUpperCase();
        if (!List.of("INT", "EXT", "INT/EXT").contains(setting)) {
            setting = "INT";
        }
        String time = heading.path("time").asText("UNKNOWN").toUpperCase();
        if (!List.of("DAWN", "DAY", "DUSK", "NIGHT", "CONTINUOUS", "UNKNOWN").contains(time)) {
            time = "UNKNOWN";
        }
        JsonNode locationNode = firstPresent(
                heading,
                "location",
                "locationId",
                "location_id",
                "place",
                "name");
        if (locationNode != null && locationNode.isObject()) {
            String locationName = locationNode.path("name").asText(
                    locationNode.path("id").asText("未指定地点"));
            heading.put("location", locationName);
        } else if (locationNode != null && locationNode.isTextual()) {
            String location = locationNode.asText();
            heading.put("location", locationNames.getOrDefault(location, location));
        }
        heading.put("setting", setting);
        putDefaultText(heading, "location", "未指定地点");
        heading.put("time", time);
        removeFields(heading, "locationId", "location_id", "place", "name");
    }

    private void normalizeBeats(
            ObjectNode scene,
            int sceneIndex,
            Map<String, String> characterReferences
    ) {
        JsonNode beatsNode = scene.get("beats");
        ArrayNode beats;
        if (beatsNode instanceof ArrayNode existingBeats) {
            beats = existingBeats;
        } else {
            beats = objectMapper.createArrayNode();
            if (beatsNode != null && beatsNode.isTextual() && !beatsNode.asText().isBlank()) {
                beats.add(beatsNode.asText());
            }
            scene.set("beats", beats);
        }

        ArrayNode normalized = objectMapper.createArrayNode();
        for (int index = 0; index < beats.size(); index++) {
            JsonNode beatNode = beats.get(index);
            ObjectNode beat;
            if (beatNode instanceof ObjectNode existingBeat) {
                beat = existingBeat;
            } else if (beatNode.isTextual() && !beatNode.asText().isBlank()) {
                beat = objectMapper.createObjectNode();
                beat.put("type", "action");
                beat.put("text", beatNode.asText());
            } else {
                continue;
            }
            beat.put("id", "beat_%03d_%02d".formatted(sceneIndex + 1, index + 1));
            normalizeBeatAliases(beat);
            String type = beat.path("type").asText("action");
            if (!List.of("action", "dialogue", "voice_over", "narration", "transition", "shot", "note")
                    .contains(type)) {
                type = "action";
            }
            beat.put("type", type);
            JsonNode characterNode = beat.get("characterId");
            if (characterNode != null && !characterNode.isNull()) {
                String reference = referenceValue(characterNode);
                String characterId = characterReferences.get(reference);
                if (characterId == null) {
                    beat.putNull("characterId");
                } else {
                    beat.put("characterId", characterId);
                }
            }
            putDefaultText(beat, "text", "待补充");
            normalized.add(beat);
        }
        if (normalized.isEmpty()) {
            ObjectNode beat = objectMapper.createObjectNode();
            beat.put("id", "beat_%03d_01".formatted(sceneIndex + 1));
            beat.put("type", "note");
            beat.put("text", "该场景需要作者继续补充。");
            normalized.add(beat);
        }
        scene.set("beats", normalized);
    }

    private void normalizeStringArray(ObjectNode object, String field, String fallback) {
        JsonNode value = object.get(field);
        ArrayNode array = objectMapper.createArrayNode();
        if (value instanceof ArrayNode values) {
            for (JsonNode item : values) {
                String reference = referenceValue(item);
                if (reference != null && !reference.isBlank()) {
                    array.add(reference);
                }
            }
        } else if (value != null && value.isTextual() && !value.asText().isBlank()) {
            array.add(value.asText());
        } else if (fallback != null) {
            array.add(fallback);
        }
        object.set(field, array);
    }

    private void normalizeReferenceArray(
            ObjectNode object,
            String field,
            Map<String, String> references
    ) {
        JsonNode value = object.get(field);
        ArrayNode normalized = objectMapper.createArrayNode();
        if (value instanceof ArrayNode values) {
            for (JsonNode item : values) {
                String resolved = references.get(referenceValue(item));
                if (resolved != null && !containsText(normalized, resolved)) {
                    normalized.add(resolved);
                }
            }
        } else {
            String resolved = references.get(referenceValue(value));
            if (resolved != null) {
                normalized.add(resolved);
            }
        }
        object.set(field, normalized);
    }

    private Map<String, String> entityReferenceMap(ArrayNode entities) {
        Map<String, String> references = new LinkedHashMap<>();
        for (JsonNode entity : entities) {
            if (!(entity instanceof ObjectNode object)) {
                continue;
            }
            String id = object.path("id").asText();
            String name = object.path("name").asText();
            if (!id.isBlank()) {
                references.put(id, id);
            }
            if (!name.isBlank() && !id.isBlank()) {
                references.put(name, id);
            }
        }
        return references;
    }

    private Map<String, String> entityNameMap(ArrayNode entities) {
        Map<String, String> names = new LinkedHashMap<>();
        for (JsonNode entity : entities) {
            if (!(entity instanceof ObjectNode object)) {
                continue;
            }
            String id = object.path("id").asText();
            String name = object.path("name").asText();
            if (!id.isBlank() && !name.isBlank()) {
                names.put(id, name);
                names.put(name, name);
            }
        }
        return names;
    }

    private void normalizeBeatAliases(ObjectNode beat) {
        JsonNode dialogueNode = beat.get("dialogue");
        if (dialogueNode != null && !dialogueNode.isNull()) {
            beat.put("type", "dialogue");
            if (dialogueNode.isObject()) {
                copyFirstText(dialogueNode, beat, "text", "text", "content", "line");
                if (!beat.has("characterId")) {
                    JsonNode speaker = firstPresent(
                            (ObjectNode) dialogueNode,
                            "characterId",
                            "speaker",
                            "character",
                            "name");
                    if (speaker != null) {
                        beat.set("characterId", speaker);
                    }
                }
            } else if (!beat.has("text")) {
                beat.put("text", dialogueNode.asText());
            }
        }

        if (!beat.has("type")) {
            copyFirstText(beat, beat, "type", "beat_type", "kind");
        }
        if (!beat.has("text") || beat.path("text").asText().isBlank()) {
            copyFirstText(
                    beat,
                    beat,
                    "text",
                    "action_description",
                    "actionDescription",
                    "content",
                    "description",
                    "line",
                    "action");
        }
        if (!beat.has("characterId")) {
            JsonNode speaker = firstPresent(
                    beat,
                    "speaker",
                    "character",
                    "character_id",
                    "speakerId",
                    "speaker_id");
            if (speaker != null) {
                beat.set("characterId", speaker);
            }
        }
        removeFields(
                beat,
                "dialogue",
                "beat_type",
                "kind",
                "action_description",
                "actionDescription",
                "content",
                "description",
                "line",
                "action",
                "speaker",
                "character",
                "character_id",
                "speakerId",
                "speaker_id");
    }

    private JsonNode firstPresent(ObjectNode object, String... fields) {
        for (String field : fields) {
            JsonNode value = object.get(field);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private void copyFirstText(
            JsonNode source,
            ObjectNode target,
            String targetField,
            String... sourceFields
    ) {
        for (String sourceField : sourceFields) {
            JsonNode value = source.get(sourceField);
            if (value != null && value.isValueNode() && !value.asText().isBlank()) {
                target.put(targetField, value.asText());
                return;
            }
        }
    }

    private void removeFields(ObjectNode object, String... fields) {
        for (String field : fields) {
            object.remove(field);
        }
    }

    private String referenceValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual() || value.isNumber()) {
            return value.asText();
        }
        if (value.isObject()) {
            for (String field : List.of("id", "characterId", "character_id", "propId", "prop_id", "name")) {
                String candidate = value.path(field).asText();
                if (!candidate.isBlank()) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private boolean containsText(ArrayNode values, String expected) {
        for (JsonNode value : values) {
            if (expected.equals(value.asText())) {
                return true;
            }
        }
        return false;
    }

    private void normalizeAdaptationNotes(ObjectNode root) {
        JsonNode notesNode = root.get("adaptationNotes");
        if (!(notesNode instanceof ArrayNode notes)) {
            root.set("adaptationNotes", objectMapper.createArrayNode());
            return;
        }

        String fallbackSceneId = root.path("scenes").path(0).path("id").asText("scene_001");
        ArrayNode normalized = objectMapper.createArrayNode();
        for (JsonNode note : notes) {
            if (note instanceof ObjectNode objectNote) {
                putDefaultText(objectNote, "sceneId", fallbackSceneId);
                putDefaultText(objectNote, "type", "ai_adaptation");
                putDefaultText(objectNote, "description", "AI 未提供具体说明");
                normalized.add(objectNote);
            } else if (note.isTextual() && !note.asText().isBlank()) {
                ObjectNode objectNote = objectMapper.createObjectNode();
                objectNote.put("sceneId", fallbackSceneId);
                objectNote.put("type", "ai_adaptation");
                objectNote.put("description", note.asText());
                normalized.add(objectNote);
            }
        }
        root.set("adaptationNotes", normalized);
    }

    private void normalizeStoryBible(JsonNode root, List<Chapter> chapters) {
        if (!(root instanceof ObjectNode object)) {
            return;
        }
        ensureArray(object, "characters");
        ensureArray(object, "locations");
        ensureArray(object, "props");

        normalizeEntityIds((ArrayNode) object.get("characters"), "char", chapters);
        normalizeEntityIds((ArrayNode) object.get("locations"), "loc", chapters);
        normalizeEntityIds((ArrayNode) object.get("props"), "prop", chapters);
    }

    private void ensureArray(ObjectNode object, String field) {
        if (!object.has(field) || !object.get(field).isArray()) {
            object.set(field, objectMapper.createArrayNode());
        }
    }

    private void normalizeEntityIds(ArrayNode entities, String prefix, List<Chapter> chapters) {
        for (int index = 0; index < entities.size(); index++) {
            JsonNode entityNode = entities.get(index);
            ObjectNode entity;
            if (entityNode instanceof ObjectNode objectEntity) {
                entity = objectEntity;
            } else {
                entity = objectMapper.createObjectNode();
                if (entityNode.isTextual()) {
                    entity.put("name", entityNode.asText());
                }
                entities.set(index, entity);
            }
            entity.put("id", "%s_%03d".formatted(prefix, index + 1));
            putDefaultText(entity, "name", "未命名");
            putDefaultText(entity, "description", "");
            if ("char".equals(prefix)) {
                putDefaultText(entity, "age", "未知");
                putDefaultText(entity, "gender", "未知");
                putDefaultText(entity, "clothing", "未说明");
                putVisualWeight(entity);
            } else if ("loc".equals(prefix)) {
                putDefaultText(entity, "timeOfDay", "UNKNOWN");
                putDefaultText(entity, "lightingMood", "未说明");
                putVisualWeight(entity);
            } else if ("prop".equals(prefix)) {
                putDefaultText(entity, "storyFunction", "推动情节或人物行动");
            }
            if (("char".equals(prefix) || "prop".equals(prefix)) && !entity.has("firstAppearance")) {
                entity.put("firstAppearance", chapters.getFirst().id());
            }
        }
    }

    private void putDefaultText(ObjectNode entity, String field, String fallback) {
        if (!entity.has(field) || entity.path(field).asText().isBlank()) {
            entity.put(field, fallback);
        }
    }

    private void putVisualWeight(ObjectNode entity) {
        int value = entity.path("visualWeight").asInt(3);
        entity.put("visualWeight", Math.max(1, Math.min(value, 5)));
    }

    private Screenplay mergeStoryBible(Screenplay screenplay, JsonNode storyBible) throws IOException {
        List<Screenplay.CharacterProfile> characters = objectMapper.readerForListOf(Screenplay.CharacterProfile.class)
                .readValue(storyBible.path("characters"));
        List<Screenplay.Location> locations = objectMapper.readerForListOf(Screenplay.Location.class)
                .readValue(storyBible.path("locations"));
        List<Screenplay.Prop> props = objectMapper.readerForListOf(Screenplay.Prop.class)
                .readValue(storyBible.path("props"));
        return new Screenplay(
                screenplay.schemaVersion(),
                screenplay.project(),
                characters,
                locations,
                props,
                screenplay.scenes(),
                screenplay.adaptationNotes());
    }

    private String sceneIdAt(ArrayNode scenes, int index) {
        String id = scenes.path(index).path("id").asText();
        return id.isBlank() ? "scene_%03d".formatted(index + 1) : id;
    }

    private void putNullable(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    @Override
    public String mode() {
        return "AI";
    }
}
