const elements = {
  title: document.querySelector("#title"),
  format: document.querySelector("#format"),
  content: document.querySelector("#content"),
  convert: document.querySelector("#convertBtn"),
  sample: document.querySelector("#sampleBtn"),
  edit: document.querySelector("#editBtn"),
  download: document.querySelector("#downloadBtn"),
  chapterHint: document.querySelector("#chapterHint"),
  empty: document.querySelector("#emptyState"),
  result: document.querySelector("#resultArea"),
  validation: document.querySelector("#validation"),
  projectTitle: document.querySelector("#projectTitle"),
  metrics: document.querySelector("#metrics"),
  formatProfile: document.querySelector("#formatProfile"),
  characters: document.querySelector("#characters"),
  locations: document.querySelector("#locations"),
  props: document.querySelector("#props"),
  scenes: document.querySelector("#scenes"),
  output: document.querySelector("#output"),
  characterCount: document.querySelector("#characterCount"),
  locationCount: document.querySelector("#locationCount"),
  propCount: document.querySelector("#propCount")
};

const editorElements = {
  dialog: document.querySelector("#editorDialog"),
  close: document.querySelector("#closeEditorBtn"),
  sceneList: document.querySelector("#editorSceneList"),
  purpose: document.querySelector("#editScenePurpose"),
  chapters: document.querySelector("#editSceneChapters"),
  setting: document.querySelector("#editSceneSetting"),
  location: document.querySelector("#editSceneLocation"),
  sceneCharacters: document.querySelector("#editSceneCharacters"),
  sceneProps: document.querySelector("#editSceneProps"),
  time: document.querySelector("#editSceneTime"),
  beats: document.querySelector("#editorBeats"),
  beatCount: document.querySelector("#editorBeatCount"),
  characters: document.querySelector("#editorCharacters"),
  locations: document.querySelector("#editorLocations"),
  props: document.querySelector("#editorProps"),
  addScene: document.querySelector("#addSceneBtn"),
  deleteScene: document.querySelector("#deleteSceneBtn"),
  addBeat: document.querySelector("#addBeatBtn"),
  result: document.querySelector("#editorResult"),
  apply: document.querySelector("#applyEditBtn")
};

const settingsElements = {
  open: document.querySelector("#aiSettingsBtn"),
  close: document.querySelector("#closeSettingsBtn"),
  dialog: document.querySelector("#settingsDialog"),
  fields: document.querySelector("#aiFields"),
  provider: document.querySelector("#aiProvider"),
  baseUrl: document.querySelector("#aiBaseUrl"),
  modelPreset: document.querySelector("#aiModelPreset"),
  model: document.querySelector("#aiModel"),
  modelHint: document.querySelector("#modelHint"),
  apiKey: document.querySelector("#aiApiKey"),
  apiKeyHint: document.querySelector("#apiKeyHint"),
  timeout: document.querySelector("#aiTimeout"),
  test: document.querySelector("#testAiBtn"),
  save: document.querySelector("#saveAiBtn"),
  result: document.querySelector("#connectionResult"),
  runtimeStatus: document.querySelector("#runtimeStatus")
};

const aiProviders = {
  deepseek: {
    label: "DeepSeek",
    baseUrl: "https://api.deepseek.com",
    models: ["deepseek-v4-flash", "deepseek-v4-pro", "deepseek-chat"],
    hint: "适合中文长文本改编；使用 DeepSeek API Key"
  },
  zhipu: {
    label: "智谱 GLM",
    baseUrl: "https://open.bigmodel.cn/api/paas/v4",
    models: ["glm-5", "glm-4.7", "glm-4.5-air", "glm-4-flash"],
    hint: "使用智谱开放平台 API Key；模型 ID 可按控制台实际可用项修改"
  },
  qwen: {
    label: "通义千问 Qwen",
    baseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
    models: ["qwen3.5-plus", "qwen-plus", "qwen-turbo", "qwen-long"],
    hint: "使用阿里云百炼 DashScope API Key"
  },
  moonshot: {
    label: "Moonshot / Kimi",
    baseUrl: "https://api.moonshot.cn/v1",
    models: ["kimi-k2.5", "moonshot-v1-128k", "moonshot-v1-32k"],
    hint: "使用 Moonshot 开放平台 API Key"
  },
  openai: {
    label: "OpenAI",
    baseUrl: "https://api.openai.com/v1",
    models: ["gpt-4.1", "gpt-4.1-mini", "gpt-4o"],
    hint: "使用 OpenAI API Key；建议选择支持 Chat Completions 的模型"
  },
  ollama: {
    label: "Ollama 本地模型",
    baseUrl: "http://localhost:11434/v1",
    models: ["qwen3:8b", "deepseek-r1:8b", "glm4:9b", "llama3.1:8b"],
    hint: "无需 API Key，但需要先在本机启动 Ollama 并拉取对应模型"
  },
  custom: {
    label: "自定义兼容接口",
    baseUrl: "",
    models: [],
    hint: "填写提供 OpenAI Chat Completions 兼容接口的 Base URL 与模型 ID"
  }
};

let latestYaml = "";
let latestScreenplay = null;
let aiEnabled = true;
let editorDraft = null;
let selectedSceneIndex = 0;
let previewVisible = false;
let previewSceneCount = 0;

function countChapters() {
  const pattern = /^\s*(第[零〇一二两三四五六七八九十百千0-9]+章|Chapter\s+\d+)/gmi;
  return (elements.content.value.match(pattern) || []).length;
}

function updateChapterHint() {
  const count = countChapters();
  elements.chapterHint.textContent = count >= 3
    ? `已识别 ${count} 个章节，可以开始改编`
    : `已识别 ${count} 个章节，还需要 ${3 - count} 个`;
}

function setPipeline(stage) {
  const order = ["source", "assets", "plan", "scenes", "yaml"];
  const current = order.indexOf(stage);
  document.querySelectorAll(".pipeline-step").forEach((step) => {
    const index = order.indexOf(step.dataset.stage);
    step.classList.toggle("active", index <= current);
  });
  document.querySelectorAll(".pipeline-line").forEach((line, index) => {
    line.classList.toggle("active", index < current);
  });
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function renderMetric(label, value) {
  return `<div class="metric"><strong>${escapeHtml(value)}</strong><span>${escapeHtml(label)}</span></div>`;
}

function renderAsset(container, items, type) {
  const emptyText = {
    character: "未识别到明确对白人物",
    location: "未识别到明确地点",
    prop: "未识别到关键道具"
  }[type];

  if (!items.length) {
    container.innerHTML = `<div class="asset-empty">${emptyText}</div>`;
    return;
  }

  container.innerHTML = items.map((item) => `
    <article class="asset-item">
      <div class="asset-id">${escapeHtml(item.id)}</div>
      <h4>${escapeHtml(item.name)}</h4>
      <p>${escapeHtml(item.description)}</p>
      ${item.firstAppearance ? `<small>首次出现：${escapeHtml(item.firstAppearance)}</small>` : ""}
    </article>
  `).join("");
}

function beatLabel(type, format) {
  if (type === "action" && format === "stage_play") return "舞台调度";
  if (type === "shot" && format === "stage_play") return "灯光 / 音效";
  return {
    action: "动作",
    dialogue: "对白",
    voice_over: "画外音",
    narration: "旁白",
    transition: "转场",
    shot: "镜头",
    note: "说明"
  }[type] || type;
}

function formatDesignFallback(format, index, total) {
  const last = index === Math.max(total - 1, 0);
  if (format === "film") {
    return {
      sectionLabel: index === 0 ? "第一幕 · 建置" : last ? "第三幕 · 高潮与收束" : "第二幕 · 对抗",
      storyLine: "MAIN",
      dramaticFunction: index === 0 ? "setup" : last ? "resolution" : "escalation",
      estimatedDurationSeconds: 180,
      productionNotes: ["以可视化动作和空间关系推进"]
    };
  }
  if (format === "tv_series") {
    return {
      sectionLabel: index === 0 ? "TEASER · 冷开场" : last ? "ACT FOUR · 集尾悬念" : "ACT TWO",
      storyLine: index % 3 === 1 ? "B_STORY" : "A_STORY",
      dramaticFunction: index === 0 ? "hook" : last ? "cliffhanger" : "act_turn",
      estimatedDurationSeconds: 120,
      productionNotes: ["推进故事线并形成幕转折"]
    };
  }
  if (format === "stage_play") {
    return {
      sectionLabel: `第一幕 · 第${index + 1}场`,
      storyLine: "ENSEMBLE",
      dramaticFunction: index === 0 ? "setup" : last ? "resolution" : "confrontation",
      estimatedDurationSeconds: 300,
      productionNotes: ["标明出入场、站位、灯光、音效或换景需求"]
    };
  }
  return {
    sectionLabel: index === 0 ? "HOOK · 前10秒" : last ? "CLIFFHANGER · 卡点" : "REVERSAL · 反转",
    storyLine: "MAIN",
    dramaticFunction: index === 0 ? "hook" : last ? "cliffhanger" : "reversal",
    estimatedDurationSeconds: 60,
    productionNotes: ["首屏进入冲突，场尾保留反转或卡点"]
  };
}

function durationLabel(seconds) {
  const value = Number(seconds) || 0;
  if (value < 60) return `${value} 秒`;
  const minutes = Math.floor(value / 60);
  const remainder = value % 60;
  return remainder ? `${minutes} 分 ${remainder} 秒` : `${minutes} 分钟`;
}

function renderScenes(screenplay) {
  const characters = screenplay.characters || [];
  const scenes = screenplay.scenes || [];
  const format = screenplay.project?.format || "web_series";
  const characterNames = Object.fromEntries(characters.map((item) => [item.id, item.name]));
  if (!scenes.length) {
    elements.scenes.innerHTML = '<div class="asset-empty">章节剧本生成后，动作与对白会逐章出现在这里</div>';
    return;
  }
  elements.scenes.innerHTML = scenes.map((scene, index) => {
    const design = scene.formatDesign || formatDesignFallback(format, index, scenes.length);
    return `
    <article class="scene-item scene-format-${escapeHtml(format)}">
      <div class="scene-format-bar">
        <strong>${escapeHtml(design.sectionLabel)}</strong>
        <span>${escapeHtml(design.storyLine)} · ${escapeHtml(design.dramaticFunction)}</span>
        <b>${escapeHtml(durationLabel(design.estimatedDurationSeconds))}</b>
      </div>
      <div class="scene-header">
        <div class="scene-number">${String(index + 1).padStart(2, "0")}</div>
        <div>
          <div class="scene-heading">
            ${escapeHtml(scene.heading.setting)} · ${escapeHtml(scene.heading.location)} · ${escapeHtml(scene.heading.time)}
          </div>
          <h3>${escapeHtml(scene.purpose)}</h3>
        </div>
        <div class="confidence">${Math.round(scene.sourceFidelity.confidence * 100)}%</div>
      </div>
      <div class="scene-meta">
        <span>${scene.beats.length} 个叙事单元</span>
        <span>${scene.characters.length} 位人物</span>
        <span>${scene.props.length} 件道具</span>
        <span>${escapeHtml(scene.sourceChapterIds.join(" / "))}</span>
      </div>
      <div class="beats">
        ${scene.beats.map((beat) => `
          <div class="beat beat-${escapeHtml(beat.type)}">
            <span>${escapeHtml(beatLabel(beat.type, format))}</span>
            <div>
              ${beat.characterId
                ? `<strong>${escapeHtml(characterNames[beat.characterId] || beat.characterId)}
                    ${beat.parenthetical ? `<i>（${escapeHtml(beat.parenthetical)}）</i>` : ""}
                   </strong>`
                : ""}
              <p>${escapeHtml(beat.text)}</p>
            </div>
          </div>
        `).join("")}
      </div>
      <div class="production-notes">
        <b>${format === "stage_play" ? "舞台执行" : "制作重点"}</b>
        ${(design.productionNotes || []).map((note) => `<span>${escapeHtml(note)}</span>`).join("")}
      </div>
      ${scene.sourceFidelity?.evidence
        ? `<div class="scene-evidence"><b>原文依据</b>${escapeHtml(scene.sourceFidelity.evidence)}</div>`
        : ""}
    </article>
  `;
  }).join("");
}

function renderPreview(screenplay, stage, message) {
  if (!screenplay) return;
  const characters = screenplay.characters || [];
  const locations = screenplay.locations || [];
  const props = screenplay.props || [];
  const scenes = screenplay.scenes || [];
  const firstPreview = !previewVisible;
  const receivedNewScenes = scenes.length > previewSceneCount;

  latestYaml = "";
  latestScreenplay = null;
  elements.projectTitle.textContent = screenplay.project?.title || "剧本初稿";
  elements.validation.textContent = `生成中 · ${message || "正在处理"}`;
  elements.validation.classList.remove("error");
  elements.output.textContent = "YAML 将在全部章节生成并通过结构校验后显示。";
  elements.metrics.innerHTML = [
    renderMetric("源章节", screenplay.project?.sourceChapterCount || 0),
    renderMetric("人物", characters.length),
    renderMetric("地点", locations.length),
    renderMetric("道具", props.length),
    renderMetric("已生成场景", scenes.length)
  ].join("");
  renderFormatProfile(screenplay.project || {});

  elements.characterCount.textContent = characters.length;
  elements.locationCount.textContent = locations.length;
  elements.propCount.textContent = props.length;
  renderAsset(elements.characters, characters, "character");
  renderAsset(elements.locations, locations, "location");
  renderAsset(elements.props, props, "prop");
  renderScenes(screenplay);

  elements.empty.classList.add("hidden");
  elements.result.classList.remove("hidden");
  elements.download.disabled = true;
  elements.edit.disabled = true;
  previewVisible = true;
  previewSceneCount = scenes.length;

  if (firstPreview) {
    switchTab("assets");
  } else if (receivedNewScenes && stage === "scenes") {
    switchTab("scenes");
  }
}

function renderResult(data) {
  latestYaml = data.yaml;
  latestScreenplay = data.screenplay;
  const screenplay = data.screenplay;

  elements.projectTitle.textContent = screenplay.project.title;
  elements.validation.textContent = `${data.mode} · ${data.validationMessages.join("；")}`;
  elements.validation.classList.remove("error");
  elements.output.textContent = data.yaml;

  elements.metrics.innerHTML = [
    renderMetric("源章节", screenplay.project.sourceChapterCount),
    renderMetric("人物", screenplay.characters.length),
    renderMetric("地点", screenplay.locations.length),
    renderMetric("道具", screenplay.props.length),
    renderMetric("场景", screenplay.scenes.length)
  ].join("");
  renderFormatProfile(screenplay.project);

  elements.characterCount.textContent = screenplay.characters.length;
  elements.locationCount.textContent = screenplay.locations.length;
  elements.propCount.textContent = screenplay.props.length;
  renderAsset(elements.characters, screenplay.characters, "character");
  renderAsset(elements.locations, screenplay.locations, "location");
  renderAsset(elements.props, screenplay.props, "prop");
  renderScenes(screenplay);

  elements.empty.classList.add("hidden");
  elements.result.classList.remove("hidden");
  elements.download.disabled = false;
  elements.edit.disabled = false;
  previewVisible = false;
  previewSceneCount = screenplay.scenes.length;
  setPipeline("yaml");
  switchTab("assets");
}

function renderFormatProfile(project) {
  const profile = project.formatProfile;
  if (!profile) {
    elements.formatProfile.innerHTML = "";
    elements.formatProfile.classList.add("hidden");
    return;
  }
  const labels = {
    film: "电影",
    tv_series: "电视剧",
    web_series: "短剧",
    stage_play: "舞台剧"
  };
  elements.formatProfile.classList.remove("hidden");
  elements.formatProfile.innerHTML = `
    <div class="format-profile-heading">
      <div>
        <span>媒介策略</span>
        <strong>${escapeHtml(labels[project.format] || project.format)}</strong>
      </div>
      <b>约 ${escapeHtml(profile.targetDurationMinutes)} 分钟</b>
    </div>
    <div class="format-profile-body">
      <p><strong>结构</strong>${escapeHtml(profile.structure)}</p>
      <p><strong>节奏</strong>${escapeHtml(profile.pacing)}</p>
      <ul>${(profile.constraints || [])
        .map((item) => `<li>${escapeHtml(item)}</li>`)
        .join("")}</ul>
    </div>
  `;
}

function openStructuredEditor() {
  if (!latestScreenplay) return;
  editorDraft = JSON.parse(JSON.stringify(latestScreenplay));
  selectedSceneIndex = 0;
  editorElements.result.classList.add("hidden");
  renderAssetEditor();
  renderBlockEditor();
  editorElements.dialog.classList.remove("hidden");
}

const assetDefinitions = {
  characters: {
    prefix: "char",
    empty: "暂无人物",
    defaultName: "新人物",
    extraLabel: "首次出现",
    extraField: "firstAppearance",
    extraDefault: "chapter_01"
  },
  locations: {
    prefix: "loc",
    empty: "暂无地点",
    defaultName: "新地点",
    extraLabel: "时间氛围",
    extraField: "timeOfDay",
    extraDefault: "UNKNOWN"
  },
  props: {
    prefix: "prop",
    empty: "暂无道具",
    defaultName: "新道具",
    extraLabel: "剧情功能",
    extraField: "storyFunction",
    extraDefault: "推动情节或承载线索"
  }
};

function renderAssetEditor() {
  if (!editorDraft) return;
  renderEditableAssetList("characters", editorElements.characters);
  renderEditableAssetList("locations", editorElements.locations);
  renderEditableAssetList("props", editorElements.props);
}

function renderEditableAssetList(type, container) {
  const definition = assetDefinitions[type];
  const items = editorDraft[type] || [];
  if (!items.length) {
    container.innerHTML = `<div class="editor-asset-empty">${definition.empty}</div>`;
    return;
  }
  container.innerHTML = items.map((item, index) => `
    <div class="editor-asset-row" data-asset-type="${type}" data-asset-index="${index}">
      <div class="editor-asset-row-heading">
        <code>${escapeHtml(item.id)}</code>
        <button class="icon-button asset-delete" type="button"
                title="删除${type === "characters" ? "人物" : type === "locations" ? "地点" : "道具"}"
                data-delete-asset="${type}" data-asset-index="${index}">×</button>
      </div>
      <input data-asset-field="name" value="${escapeHtml(item.name || "")}" placeholder="名称">
      <textarea data-asset-field="description" placeholder="描述">${escapeHtml(item.description || "")}</textarea>
      <label>
        <span>${definition.extraLabel}</span>
        <input data-asset-field="${definition.extraField}"
               value="${escapeHtml(item[definition.extraField] || definition.extraDefault)}">
      </label>
    </div>
  `).join("");
}

function nextAssetId(type) {
  const prefix = assetDefinitions[type].prefix;
  const maximum = (editorDraft[type] || []).reduce((value, item) => {
    const match = String(item.id || "").match(new RegExp(`^${prefix}_(\\d+)$`));
    return Math.max(value, match ? Number(match[1]) : 0);
  }, 0);
  return `${prefix}_${String(maximum + 1).padStart(3, "0")}`;
}

function addAsset(type) {
  const definition = assetDefinitions[type];
  const id = nextAssetId(type);
  const base = {
    id,
    name: definition.defaultName,
    description: "",
    [definition.extraField]: definition.extraDefault
  };
  if (type === "characters") {
    Object.assign(base, {age: "未知", gender: "未知", clothing: "未说明", visualWeight: 3});
  } else if (type === "locations") {
    Object.assign(base, {lightingMood: "未说明", visualWeight: 3});
  } else {
    Object.assign(base, {firstAppearance: "chapter_01"});
  }
  editorDraft[type] ||= [];
  editorDraft[type].push(base);
  renderAssetEditor();
  if (type === "characters") renderEditorBeats();
}

function deleteAsset(type, index) {
  const item = editorDraft[type]?.[index];
  if (!item) return;
  editorDraft[type].splice(index, 1);
  if (type === "characters") {
    (editorDraft.scenes || []).forEach((scene) => {
      scene.characters = (scene.characters || []).filter((id) => id !== item.id);
      (scene.beats || []).forEach((beat) => {
        if (beat.characterId === item.id) beat.characterId = null;
      });
    });
    renderEditorBeats();
  }
  if (type === "props") {
    (editorDraft.scenes || []).forEach((scene) => {
      scene.props = (scene.props || []).filter((id) => id !== item.id);
    });
  }
  renderAssetEditor();
  if (type === "characters" || type === "props") {
    renderBlockEditor();
  }
}

function currentEditorScene() {
  return editorDraft?.scenes?.[selectedSceneIndex] || null;
}

function syncCurrentSceneFields() {
  const scene = currentEditorScene();
  if (!scene) return;
  scene.purpose = editorElements.purpose.value.trim();
  scene.sourceChapterIds = editorElements.chapters.value
    .split(/[,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean);
  scene.heading = {
    setting: editorElements.setting.value,
    location: editorElements.location.value.trim(),
    time: editorElements.time.value
  };
  scene.characters = parseIdList(editorElements.sceneCharacters.value);
  scene.props = parseIdList(editorElements.sceneProps.value);
}

function parseIdList(value) {
  return value
    .split(/[,，\s]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function renderBlockEditor() {
  if (!editorDraft?.scenes?.length) {
    editorElements.sceneList.innerHTML = '<div class="asset-empty">暂无场景，请新增场景</div>';
    editorElements.beats.innerHTML = "";
    editorElements.beatCount.textContent = "0 个叙事单元";
    editorElements.deleteScene.disabled = true;
    return;
  }
  selectedSceneIndex = Math.min(selectedSceneIndex, editorDraft.scenes.length - 1);
  editorElements.sceneList.innerHTML = editorDraft.scenes.map((scene, index) => `
    <button class="editor-scene-option ${index === selectedSceneIndex ? "active" : ""}"
            type="button" data-scene-index="${index}">
      <b>${String(index + 1).padStart(2, "0")}</b>
      <span>
        <strong>${escapeHtml(scene.heading?.location || "未指定地点")}</strong>
        <small>${escapeHtml(scene.purpose || "待补充场景目的")}</small>
      </span>
    </button>
  `).join("");

  const scene = currentEditorScene();
  scene.heading ||= {setting: "INT", location: "", time: "UNKNOWN"};
  scene.sourceChapterIds ||= [];
  scene.beats ||= [];
  editorElements.purpose.value = scene.purpose || "";
  editorElements.chapters.value = scene.sourceChapterIds.join(", ");
  editorElements.setting.value = scene.heading.setting || "INT";
  editorElements.location.value = scene.heading.location || "";
  editorElements.sceneCharacters.value = (scene.characters || []).join(", ");
  editorElements.sceneProps.value = (scene.props || []).join(", ");
  editorElements.time.value = scene.heading.time || "UNKNOWN";
  editorElements.deleteScene.disabled = false;
  renderEditorBeats();
}

function renderEditorBeats() {
  const scene = currentEditorScene();
  const beats = scene?.beats || [];
  const characterOptions = (editorDraft.characters || [])
    .map((character) => `<option value="${escapeHtml(character.id)}">${escapeHtml(character.name)}</option>`)
    .join("");
  editorElements.beatCount.textContent = `${beats.length} 个叙事单元`;
  editorElements.beats.innerHTML = beats.map((beat, index) => `
    <article class="editor-beat" data-beat-index="${index}">
      <div class="editor-beat-toolbar">
        <span>${String(index + 1).padStart(2, "0")}</span>
        <select data-beat-field="type" aria-label="Beat 类型">
          ${["action", "dialogue", "voice_over", "narration", "transition", "shot", "note"]
            .map((type) => `<option value="${type}" ${beat.type === type ? "selected" : ""}>${escapeHtml(beatLabel(type, editorDraft.project?.format))}</option>`)
            .join("")}
        </select>
        <button class="icon-button beat-delete" type="button" title="删除 Beat" data-delete-beat="${index}">×</button>
      </div>
      <div class="editor-beat-fields">
        <label class="${beat.type === "dialogue" ? "" : "beat-speaker-hidden"}">
          <span>说话人</span>
          <select data-beat-field="characterId">
            <option value="">未指定</option>
            ${characterOptions.replace(
              `value="${escapeHtml(beat.characterId || "")}"`,
              `value="${escapeHtml(beat.characterId || "")}" selected`
            )}
          </select>
        </label>
        <label class="${beat.type === "dialogue" ? "" : "beat-speaker-hidden"}">
          <span>语气 / 动作提示</span>
          <input data-beat-field="parenthetical" value="${escapeHtml(beat.parenthetical || "")}" placeholder="低声、停顿、看向众人">
        </label>
      </div>
      <textarea data-beat-field="text" spellcheck="false"
                placeholder="${beat.type === "dialogue" ? "输入角色台词" : "输入可拍摄动作或叙事内容"}">${escapeHtml(beat.text || "")}</textarea>
    </article>
  `).join("");
}

function addEditorScene() {
  syncCurrentSceneFields();
  const index = editorDraft.scenes.length;
  editorDraft.scenes.push({
    id: `scene_${String(index + 1).padStart(3, "0")}`,
    sourceChapterIds: ["chapter_01"],
    heading: {setting: "INT", location: "未指定地点", time: "UNKNOWN"},
    purpose: "待补充场景目的",
    characters: [],
    props: [],
    formatDesign: formatDesignFallback(
      editorDraft.project?.format || "web_series",
      index,
      index + 1
    ),
    beats: [{
      id: `beat_${String(index + 1).padStart(3, "0")}_01`,
      type: "action",
      characterId: null,
      parenthetical: null,
      text: "待补充动作。"
    }],
    continuity: {previousSceneId: null, nextSceneId: null},
    sourceFidelity: {confidence: 0.5, inventedContent: true, evidence: ""}
  });
  selectedSceneIndex = index;
  renderBlockEditor();
}

function deleteEditorScene() {
  if (!currentEditorScene()) return;
  editorDraft.scenes.splice(selectedSceneIndex, 1);
  selectedSceneIndex = Math.max(0, selectedSceneIndex - 1);
  renderBlockEditor();
}

function addEditorBeat() {
  syncCurrentSceneFields();
  const scene = currentEditorScene();
  if (!scene) return;
  const index = scene.beats.length;
  scene.beats.push({
    id: `beat_${String(selectedSceneIndex + 1).padStart(3, "0")}_${String(index + 1).padStart(2, "0")}`,
    type: "action",
    characterId: null,
    parenthetical: null,
    text: ""
  });
  renderEditorBeats();
}

function showEditorResult(success, message) {
  editorElements.result.classList.remove("hidden", "success", "failure");
  editorElements.result.classList.add(success ? "success" : "failure");
  editorElements.result.textContent = message;
}

async function applyStructuredEdit() {
  syncCurrentSceneFields();
  const screenplay = editorDraft;

  editorElements.apply.disabled = true;
  editorElements.apply.textContent = "校验中…";
  try {
    const response = await fetch("/api/screenplays/serialize", {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify(screenplay)
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.message || "应用修改失败");
    renderResult(data);
    showEditorResult(true, data.validationMessages.join("；"));
    window.setTimeout(() => editorElements.dialog.classList.add("hidden"), 600);
  } catch (error) {
    showEditorResult(false, error.message);
  } finally {
    editorElements.apply.disabled = false;
    editorElements.apply.textContent = "应用修改";
  }
}

function showError(message) {
  elements.empty.classList.add("hidden");
  elements.result.classList.remove("hidden");
  elements.validation.textContent = message;
  elements.validation.classList.add("error");
  elements.output.textContent = "";
}

function aiSettingsPayload() {
  return {
    enabled: aiEnabled,
    provider: settingsElements.provider.value,
    baseUrl: settingsElements.baseUrl.value.trim(),
    apiKey: settingsElements.apiKey.value.trim(),
    model: settingsElements.modelPreset.value === "__custom__"
      ? settingsElements.model.value.trim()
      : settingsElements.modelPreset.value,
    timeoutSeconds: Number(settingsElements.timeout.value)
  };
}

function renderModelOptions(providerId, selectedModel, updateEndpoint = true) {
  const provider = aiProviders[providerId] || aiProviders.custom;
  const knownModel = provider.models.includes(selectedModel);
  settingsElements.modelPreset.innerHTML = [
    ...provider.models.map((model) =>
      `<option value="${escapeHtml(model)}">${escapeHtml(model)}</option>`),
    '<option value="__custom__">自定义模型 ID…</option>'
  ].join("");
  settingsElements.modelHint.textContent = provider.hint;
  if (updateEndpoint) {
    settingsElements.baseUrl.value = provider.baseUrl;
  }
  if (selectedModel && knownModel) {
    settingsElements.modelPreset.value = selectedModel;
    settingsElements.model.value = selectedModel;
  } else if (selectedModel) {
    settingsElements.modelPreset.value = "__custom__";
    settingsElements.model.value = selectedModel;
  } else if (provider.models.length) {
    settingsElements.modelPreset.value = provider.models[0];
    settingsElements.model.value = provider.models[0];
  } else {
    settingsElements.modelPreset.value = "__custom__";
    settingsElements.model.value = "";
  }
  updateCustomModelVisibility();
  settingsElements.apiKey.placeholder = providerId === "ollama"
    ? "本地 Ollama 无需填写"
    : `输入 ${provider.label} API Key`;
}

function updateCustomModelVisibility() {
  const custom = settingsElements.modelPreset.value === "__custom__";
  settingsElements.model.classList.toggle("hidden", !custom);
  if (!custom) settingsElements.model.value = settingsElements.modelPreset.value;
}

function setAiMode(enabled) {
  aiEnabled = enabled;
  document.querySelectorAll(".mode-option").forEach((option) => {
    option.classList.toggle(
      "active",
      option.dataset.mode === (enabled ? "deepseek" : "offline")
    );
  });
  settingsElements.fields.classList.toggle("disabled-fields", !enabled);
  settingsElements.test.disabled = !enabled;
}

function updateRuntimeStatus(settings) {
  const active = settings.activeMode === "AI";
  settingsElements.runtimeStatus.classList.toggle("ai-active", active);
  settingsElements.runtimeStatus.querySelector("b").textContent = active
    ? `${settings.provider} · ${settings.model}`
    : "离线规则模式";
}

async function loadAiSettings() {
  try {
    const response = await fetch("/api/settings/ai");
    if (!response.ok) throw new Error("读取 AI 设置失败");
    const settings = await response.json();
    setAiMode(settings.enabled);
    settingsElements.provider.value = settings.providerId || "custom";
    settingsElements.baseUrl.value = settings.baseUrl;
    renderModelOptions(settingsElements.provider.value, settings.model, false);
    settingsElements.timeout.value = settings.timeoutSeconds;
    settingsElements.apiKey.value = "";
    settingsElements.apiKeyHint.textContent = settings.apiKeyConfigured
      ? "已配置 Key，留空保存将继续使用原 Key"
      : "尚未配置 Key";
    updateRuntimeStatus(settings);
  } catch (error) {
    settingsElements.runtimeStatus.querySelector("b").textContent = "AI 设置读取失败";
  }
}

function showConnectionResult(success, message) {
  settingsElements.result.classList.remove("hidden", "success", "failure");
  settingsElements.result.classList.add(success ? "success" : "failure");
  settingsElements.result.textContent = message;
}

async function testAiConnection() {
  settingsElements.test.disabled = true;
  settingsElements.test.textContent = "测试中…";
  settingsElements.result.classList.add("hidden");
  try {
    const response = await fetch("/api/settings/ai/test", {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify(aiSettingsPayload())
    });
    const result = await response.json();
    showConnectionResult(
      result.success,
      result.success ? `${result.message}，延迟 ${result.latencyMs} ms` : result.message
    );
  } catch (error) {
    showConnectionResult(false, error.message);
  } finally {
    settingsElements.test.disabled = !aiEnabled;
    settingsElements.test.textContent = "测试连接";
  }
}

async function saveAiSettings() {
  settingsElements.save.disabled = true;
  settingsElements.save.textContent = "保存中…";
  try {
    const response = await fetch("/api/settings/ai", {
      method: "PUT",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify(aiSettingsPayload())
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.message || "保存失败");
    updateRuntimeStatus(result);
    settingsElements.apiKey.value = "";
    settingsElements.apiKeyHint.textContent = result.apiKeyConfigured
      ? "已配置 Key，留空保存将继续使用原 Key"
      : "尚未配置 Key";
    showConnectionResult(true, result.activeMode === "AI" ? "已启用 AI 模式" : "已切换到离线规则模式");
  } catch (error) {
    showConnectionResult(false, error.message);
  } finally {
    settingsElements.save.disabled = false;
    settingsElements.save.textContent = "保存设置";
  }
}

async function convertNovel() {
  elements.convert.disabled = true;
  elements.convert.textContent = "正在创建任务…";
  previewVisible = false;
  previewSceneCount = 0;
  latestYaml = "";
  latestScreenplay = null;
  elements.edit.disabled = true;
  elements.download.disabled = true;
  setPipeline("source");

  try {
    const response = await fetch("/api/screenplays/jobs", {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({
        title: elements.title.value.trim(),
        content: elements.content.value,
        format: elements.format.value
      })
    });
    const job = await response.json();
    if (!response.ok) {
      throw new Error(job.message || "创建生成任务失败");
    }
    await waitForConversionJob(job.id);
  } catch (error) {
    showError(error.message);
  } finally {
    elements.convert.disabled = false;
    elements.convert.textContent = "开始改编";
  }
}

async function waitForConversionJob(jobId) {
  const startedAt = Date.now();
  while (true) {
    const response = await fetch(`/api/screenplays/jobs/${encodeURIComponent(jobId)}`, {
      cache: "no-store"
    });
    const job = await response.json();
    if (!response.ok) {
      throw new Error(job.message || "读取生成进度失败");
    }

    setPipeline(job.stage || "source");
    const elapsed = Math.max(1, Math.round((Date.now() - startedAt) / 1000));
    elements.convert.textContent =
      `${job.message || "正在处理"} · ${job.percent || 0}% · ${elapsed}s`;

    if (job.preview && job.status !== "COMPLETED") {
      renderPreview(job.preview, job.stage, job.message);
    }
    if (job.status === "COMPLETED") {
      renderResult(job.result);
      return;
    }
    if (job.status === "FAILED") {
      throw new Error(job.error || "AI 生成失败");
    }
    await new Promise((resolve) => window.setTimeout(resolve, 1000));
  }
}

function switchTab(name) {
  document.querySelectorAll(".tab").forEach((tab) => {
    tab.classList.toggle("active", tab.dataset.tab === name);
  });
  document.querySelectorAll(".tab-view").forEach((view) => view.classList.add("hidden"));
  document.querySelector(`#${name}View`).classList.remove("hidden");
}

elements.content.addEventListener("input", updateChapterHint);
elements.sample.addEventListener("click", async () => {
  const originalLabel = elements.sample.textContent;
  elements.sample.disabled = true;
  elements.sample.textContent = "载入中…";
  try {
    const response = await fetch("/sample-novel.txt", {cache: "no-store"});
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    elements.content.value = await response.text();
    elements.title.value = "斗破苍穹前三章改编";
    updateChapterHint();
    elements.content.focus();
  } catch (error) {
    showError(`示例载入失败：${error.message}`);
  } finally {
    elements.sample.disabled = false;
    elements.sample.textContent = originalLabel;
  }
});
elements.convert.addEventListener("click", convertNovel);
elements.edit.addEventListener("click", openStructuredEditor);
elements.download.addEventListener("click", () => {
  if (!latestYaml || !latestScreenplay) return;
  const blob = new Blob([latestYaml], {type: "application/yaml;charset=utf-8"});
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = `${latestScreenplay.project.title}-screenplay.yaml`;
  link.click();
  URL.revokeObjectURL(link.href);
});
document.querySelectorAll(".tab").forEach((tab) => {
  tab.addEventListener("click", () => switchTab(tab.dataset.tab));
});
document.querySelectorAll(".mode-option").forEach((option) => {
  option.addEventListener("click", () => setAiMode(option.dataset.mode === "deepseek"));
});
settingsElements.provider.addEventListener("change", () => {
  renderModelOptions(settingsElements.provider.value, "", true);
  settingsElements.apiKey.value = "";
  settingsElements.apiKeyHint.textContent = settingsElements.provider.value === "ollama"
    ? "本地接口无需 Key"
    : "切换服务商后请输入对应 API Key";
});
settingsElements.modelPreset.addEventListener("change", updateCustomModelVisibility);
settingsElements.open.addEventListener("click", () => {
  settingsElements.dialog.classList.remove("hidden");
  settingsElements.result.classList.add("hidden");
});
settingsElements.close.addEventListener("click", () => settingsElements.dialog.classList.add("hidden"));
settingsElements.dialog.addEventListener("click", (event) => {
  if (event.target === settingsElements.dialog) settingsElements.dialog.classList.add("hidden");
});
settingsElements.test.addEventListener("click", testAiConnection);
settingsElements.save.addEventListener("click", saveAiSettings);
editorElements.close.addEventListener("click", () => editorElements.dialog.classList.add("hidden"));
editorElements.dialog.addEventListener("click", (event) => {
  if (event.target === editorElements.dialog) editorElements.dialog.classList.add("hidden");
});
editorElements.apply.addEventListener("click", applyStructuredEdit);
editorElements.addScene.addEventListener("click", addEditorScene);
editorElements.deleteScene.addEventListener("click", deleteEditorScene);
editorElements.addBeat.addEventListener("click", addEditorBeat);
document.querySelectorAll("[data-add-asset]").forEach((button) => {
  button.addEventListener("click", () => addAsset(button.dataset.addAsset));
});
editorElements.dialog.addEventListener("input", (event) => {
  const row = event.target.closest("[data-asset-type]");
  const field = event.target.dataset.assetField;
  if (!row || !field) return;
  const type = row.dataset.assetType;
  const item = editorDraft[type]?.[Number(row.dataset.assetIndex)];
  if (!item) return;
  const previousValue = item[field];
  item[field] = event.target.value;
  if (type === "locations" && field === "name") {
    (editorDraft.scenes || []).forEach((scene) => {
      if (scene.heading?.location === previousValue) {
        scene.heading.location = item.name;
      }
    });
    if (editorElements.location.value === previousValue) {
      editorElements.location.value = item.name;
    }
  }
  if (type === "characters" && field === "name") renderEditorBeats();
});
editorElements.dialog.addEventListener("click", (event) => {
  const button = event.target.closest("[data-delete-asset]");
  if (!button) return;
  deleteAsset(button.dataset.deleteAsset, Number(button.dataset.assetIndex));
});
editorElements.sceneList.addEventListener("click", (event) => {
  const option = event.target.closest("[data-scene-index]");
  if (!option) return;
  syncCurrentSceneFields();
  selectedSceneIndex = Number(option.dataset.sceneIndex);
  renderBlockEditor();
});
editorElements.beats.addEventListener("input", (event) => {
  const field = event.target.dataset.beatField;
  const beatElement = event.target.closest("[data-beat-index]");
  if (!field || !beatElement) return;
  const beat = currentEditorScene().beats[Number(beatElement.dataset.beatIndex)];
  beat[field] = event.target.value || null;
});
editorElements.beats.addEventListener("change", (event) => {
  const field = event.target.dataset.beatField;
  const beatElement = event.target.closest("[data-beat-index]");
  if (!field || !beatElement) return;
  const beat = currentEditorScene().beats[Number(beatElement.dataset.beatIndex)];
  beat[field] = event.target.value || null;
  if (field === "type") renderEditorBeats();
});
editorElements.beats.addEventListener("click", (event) => {
  const button = event.target.closest("[data-delete-beat]");
  if (!button) return;
  currentEditorScene().beats.splice(Number(button.dataset.deleteBeat), 1);
  renderEditorBeats();
});

updateChapterHint();
loadAiSettings();
