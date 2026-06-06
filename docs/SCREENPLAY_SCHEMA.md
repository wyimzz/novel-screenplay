# 剧本 YAML Schema 设计说明

## 1. 目标

该 Schema 用于表达“由三章及以上小说改编得到、可继续编辑和校验的剧本初稿”。YAML 面向作者阅读与修改，配套的 `schema/screenplay.schema.json` 面向程序校验。

设计重点不是复刻某一种排版格式，而是保留剧本生产语义：故事资产、场景、动作与对白、前后衔接，以及每个场景和原文的证据关系。

## 2. 顶层结构

| 字段 | 必填 | 作用 | 设计原因 |
|---|---|---|---|
| `schemaVersion` | 是 | Schema 版本 | 支持后续字段迁移 |
| `project` | 是 | 作品名称、语言、形式、章节数 | 将项目元数据与正文分离 |
| `characters` | 是 | 全局人物表 | 使用稳定 ID，避免名称漂移 |
| `locations` | 是 | 全局地点表 | 支持场景复用和视觉一致性 |
| `props` | 是 | 全局关键道具表 | 追踪道具的剧情功能和连续性 |
| `scenes` | 是 | 有序场景列表 | 场景是改编与制作的基本单位 |
| `adaptationNotes` | 是 | 合并、删减、新增等说明 | 保留作者的最终判断权 |

## 3. 项目与章节约束

```yaml
schemaVersion: "1.0"
project:
  title: "雾城来信"
  sourceLanguage: "zh-CN"
  format: "web_series"
  sourceChapterCount: 3
  formatProfile:
    targetDurationMinutes: 12
    structure: "竖屏短剧单集：强钩子、快速升级、连续反转、卡点收尾"
    pacing: "短场景、高信息密度，前置冲突并减少铺垫"
    constraints:
      - "开场前 10 秒必须出现异常、目标或直接冲突"
```

`sourceChapterCount` 最小值为 3，对应题目要求。`format` 允许 `film`、`tv_series`、`web_series` 和 `stage_play`。`formatProfile` 把媒介选择转化为可检查的目标时长、结构、节奏和硬约束，而不是只保存一个标签。

- `film`：约 110 分钟，三幕式完整闭环，强调电影化动作与空间。
- `tv_series`：约 45 分钟单集，四幕推进并以集尾悬念承接后续。
- `web_series`：约 12 分钟，前 10 秒建立钩子，高频反转并卡点收尾。
- `stage_play`：约 100 分钟，减少换景，以对白、停顿和舞台调度推进。

## 4. Story Bible

生成流程先提取人物、地点和道具，再生成覆盖全部章节的改编蓝图，最后把 Story Bible 与蓝图共同作为受约束输入交给逐章剧本生成阶段。这一设计参考 LumenX Studio 的实体提取、稳定资产和分阶段确认思路，可降低长文本中人物改名、地点漂移、关键道具丢失以及跨章情节重复的问题。

改编蓝图包含每章摘要、进入状态、退出状态、场景目的、必须保留的动作与对白。它属于生成过程数据，不写入最终 YAML：作者交付物应保持聚焦，而不是暴露模型的内部规划；蓝图的约束结果已经体现在场景顺序、Beat 和连续性字段中。

### 4.1 人物

```yaml
characters:
  - id: "char_001"
    name: "林舟"
    description: "克制而敏感的记者"
    age: "28"
    gender: "男"
    clothing: "深色防雨外套"
    visualWeight: 5
    firstAppearance: "chapter_01"
```

- `description` 保存相对稳定的身份、性格和外貌，不混入某一瞬间的动作。
- `age`、`gender`、`clothing` 是可编辑的角色视觉信息。
- `visualWeight` 取 1 到 5，用于表示视觉重要度，后续可用于角色设定图或分镜资源分配。
- `firstAppearance` 让作者快速回到首次出现的原文章节。

### 4.2 地点

地点使用 `loc_001` 形式的稳定 ID。`description` 描述空间和陈设，`timeOfDay` 与 `lightingMood` 保存视觉基调，`visualWeight` 表示该地点对制作的重要程度。

### 4.3 道具

道具只记录影响情节、人物关系或可见行动的重要物品。`storyFunction` 解释它为什么值得进入全局资产表，避免把普通陈设全部当成关键道具。

## 5. 场景

```yaml
scenes:
  - id: "scene_001"
    sourceChapterIds: ["chapter_01"]
    heading:
      setting: "INT"
      location: "旧城咖啡馆"
      time: "NIGHT"
    purpose: "林舟取得神秘来信并见到苏禾"
    characters: ["char_001", "char_002"]
    props: ["prop_001"]
    beats: []
    continuity:
      previousSceneId: null
      nextSceneId: "scene_002"
    sourceFidelity:
      confidence: 0.91
      inventedContent: false
      evidence: "林舟在雨夜进入咖啡馆取信。"
```

- `sourceChapterIds` 支持一章拆成多场，也支持多章合并为一场。
- `heading` 使用影视剧本常见的内外景、地点、时间结构。
- `purpose` 明确场景承担的信息、冲突或人物变化，防止生成无效场景。
- `characters` 和 `props` 只引用全局稳定 ID。
- `continuity` 必须是结构化对象，不能使用“开篇场景”等自然语言字符串。
- `sourceFidelity.confidence` 是模型对改编依据充分程度的估计，不代表绝对事实。
- `inventedContent` 标记是否加入原文没有的桥接内容。
- `evidence` 保存不超过 80 字的原文依据，方便作者核对和修正。

## 6. Beat

Beat 是场景内部最小的可编辑叙事单元。

| `type` | 含义 |
|---|---|
| `action` | 可见、可听、可表演的动作或环境 |
| `dialogue` | 人物对白 |
| `voice_over` | 画外音 |
| `narration` | 旁白 |
| `transition` | 转场 |
| `shot` | 镜头建议 |
| `note` | 作者或改编说明 |

对白通过 `characterId` 引用人物，避免把“人物名：对白”保存成不可分析的自由文本。`parenthetical` 只保存必要的语气或动作提示。

采用 Beat 而不是整段场景文本，是因为作者经常需要单独修改一句对白、插入一个反应动作或调整转场。块编辑器可直接新增、删除和修改 Beat；保存时系统会重新生成 Beat ID 和场景连续性，再导出 YAML。

## 7. 校验策略

Schema 与应用共同提供两层校验：

1. `schema/screenplay.schema.json` 可供编辑器、CI 或外部工具检查必填字段、类型、枚举、ID 格式和数值范围。
2. 应用内的 Java 语义校验检查重复 ID、人物与道具引用、Beat 说话人引用、场景前后关系。

JSON Schema 无法独立验证所有跨对象引用，因此语义校验不可省略。结构化编辑器在应用修改时会重新运行 Java 校验并重新导出 YAML。

## 8. 设计取舍

- 不直接保存排版后的整篇剧本文本，因为自由文本不利于编辑、检索和后续生成。
- 不固定“一章等于一场”，让 AI 根据地点、时间和叙事目标拆分或合并。
- 不把内部改编蓝图写进交付 YAML，避免生成过程字段增加作者编辑负担。
- 不隐藏模型新增内容，使用 `inventedContent`、`evidence` 和 `adaptationNotes` 暴露改编依据。
- YAML 负责作者可读性，JSON Schema 负责机器可验证性，Java 校验负责跨对象语义。
- 当前范围只覆盖文本改编，不引入 LumenX 的图片、视频、TTS 和 FFmpeg 链路，以保证题目三的演示稳定性。
