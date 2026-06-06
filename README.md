# 章幕：AI 小说转剧本

基于 Java 21 和 Spring Boot 3.3 的小说改编工具。输入至少 3 个章节，系统会提取 Story Bible、规划场景、生成结构化剧本，并导出可编辑的 YAML 初稿。

## Demo 视频

> 提交前将此处替换为可公开访问的 Bilibili 或网盘视频链接，并确认无需登录即可播放。

- Demo 视频：`待上传`
- 在线仓库：<https://github.com/wyimzz/novel-screenplay>

## 核心流程

1. 解析并校验章节数量。
2. AI 提取人物、地点、关键道具，建立稳定 ID 的 Story Bible。
3. AI 先生成覆盖全部章节的改编蓝图，明确每章进入状态、退出状态和场景计划。
4. 各章节在共享蓝图约束下并行精编，保留关键动作、对白、说话人、语气和原文证据。
5. Java 执行跨引用语义校验。
6. 作者在块编辑器中逐场修改动作与对白，重新校验并导出 YAML。

“章幕”取小说章节与剧本幕场之意。项目围绕三章以上长文本改编，形成“故事资产提取、全书规划、逐章精编、结构校验、可视化修改、YAML 交付”的完整工作流。

## 项目实现与参考

本项目实现了 Java 后端、模型接入、全书改编蓝图、分章并行生成、响应归一化、异步任务、YAML Schema、语义校验、渐进式预览和浏览器结构编辑器。针对小说转剧本场景，重点增强了跨章节连续性、不同剧本形式的场景结构、原文证据追溯、模型异常响应修复和作者可继续编辑的结构化交付。

参考项目：

- [Alibaba LumenX Studio](https://github.com/alibaba/lumenx)：参考内容分析、故事资产提取和分阶段创作流程。
- [StoryToolkitAI](https://github.com/octimot/StoryToolkitAI)：参考 AI 辅助故事编辑、转录内容管理和创作工具整合方式。
- [Beat](https://github.com/lmparppei/Beat)：参考专业剧本场景组织、结构导航和作者编辑体验。
- [Trelby](https://github.com/trelby/trelby)：参考开源剧本编辑器的格式化写作与剧本工作流。

主要第三方依赖：

| 依赖 | 用途 |
|---|---|
| Spring Boot 3.3 | Web 服务、配置与接口 |
| Jackson Databind / YAML | JSON 响应归一化与 YAML 导出 |
| Jakarta Validation | 请求参数校验 |
| JUnit 5 / AssertJ | 自动化测试 |
| DeepSeek OpenAI-compatible API | 小说分析与剧本生成 |

第三方依赖版本以 `pom.xml` 为准。API Key 不进入 Git，保存在被 `.gitignore` 排除的 `.env` 中。

## 启动项目

### 1. 环境要求

- Windows 10/11
- JDK 21，执行 `java -version` 应显示 21
- 首次构建需要联网下载 Maven 依赖
- 项目已经包含 Maven Wrapper，不需要单独安装 Maven

在 PowerShell 中进入项目目录：

```powershell
cd F:\java\novel-screenplay
```

### 2. 快速启动

执行：

```powershell
.\mvnw.cmd spring-boot:run
```

看到 `Started ScreenplayApplication` 后，浏览器访问：

<http://localhost:8088/>

默认未配置 API Key 时使用离线规则模式，可以直接载入示例、体验结构生成和编辑器。

### 3. 使用真实 AI

项目启动后，点击页面右上角“AI 设置”：

1. 选择 DeepSeek、智谱 GLM、通义千问、Moonshot/Kimi、OpenAI、Ollama 或自定义服务商。
2. 选择模型型号，或填写服务商支持的自定义模型 ID。
3. 检查 API 地址并填写对应平台的 API Key。
4. 点击连接测试，测试成功后保存设置。
5. 输入至少 3 个章节，再点击“开始改编”。

AI 设置保存在本机 `data/` 目录，不应把 API Key 提交到 Git。使用本地 Ollama 时，先启动 Ollama 并拉取所选模型，默认接口为 `http://localhost:11434/v1`。

### 4. 使用 `.env` 启动 DeepSeek

也可以在项目根目录创建不提交到 Git 的 `.env` 文件：

```dotenv
SCREENPLAY_AI_ENABLED=true
SCREENPLAY_AI_BASE_URL=https://api.deepseek.com
SCREENPLAY_AI_API_KEY=填写你自己的APIKey
SCREENPLAY_AI_MODEL=deepseek-chat
SCREENPLAY_AI_TIMEOUT_SECONDS=180
```

先构建，再用脚本启动：

```powershell
.\mvnw.cmd clean package
powershell -ExecutionPolicy Bypass -File .\run-deepseek.ps1 -Port 8090
```

此方式访问 <http://localhost:8090/>。`-Port` 可以改成其他未占用端口。

### 5. 常见启动问题

- `JAVA_HOME` 或 Java 版本错误：安装 JDK 21，重新打开 PowerShell 后检查 `java -version`。
- `8088` 端口被占用：执行 `.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--server.port=8090`，然后访问 `http://localhost:8090/`。
- 页面能打开但 AI 不工作：进入“AI 设置”检查服务商、模型、API 地址和 Key，并先执行连接测试。
- DeepSeek 请求较慢：长文本会分为故事圣经、全书蓝图和逐章剧本多个阶段；页面会在各阶段完成后渐进展示结果。
- 脚本提示缺少 `.env`：按上面的模板在项目根目录创建 `.env`，并确认文件名不是 `.env.txt`。

## 当前能力

- 识别“第一章”和 `Chapter 1` 等章节标题
- 强制至少 3 章输入
- DeepSeek 分阶段生成：Story Bible -> 全书改编蓝图 -> 分章详细剧本 -> 全局合并
- AI 服务商切换：DeepSeek、智谱 GLM、通义千问、Moonshot/Kimi、OpenAI、Ollama 与自定义兼容接口
- 服务商内模型切换：DeepSeek V4 Flash/Pro、GLM 多型号等使用明确下拉框，也支持自定义模型 ID
- 页眉实时显示当前 AI 实测响应延迟，并按快、一般、慢区分连接状态，支持手动重新测速
- 场景级媒介结构：电影三幕与视听设计、电视剧 A/B 线与幕尾、短剧秒级钩子反转、舞台剧幕场与演员调度
- 后台生成任务与真实阶段进度，刷新页面请求不会长期占用单个 HTTP 连接
- AI JSON 异常时自动截取有效对象并执行一次结构修复
- 渐进式生成预览：故事圣经完成即展示，章节场景完成一批追加一批，最终校验后开放 YAML
- 人物、地点、道具稳定 ID
- 每章多场拆解与跨章节连续性
- 场景大纲按原文章节分组，可快速定位长剧本中的目标场景
- 动作、对白、说话人、语气、旁白、转场等 Beat
- 场景连续性与原文证据
- 可视化场景/Beat 块编辑、自动重新编号、YAML 下载
- 人物、地点、道具可新增、修改和删除，删除时自动清理场景及对白引用
- 结构编辑器采用人物、地点、道具、场景左侧分类导航，右侧提供独立大尺寸编辑工作区
- AI 失败时明确显示错误，不静默伪装成 AI 结果
- 无 API Key 时可使用离线规则模式演示

切换云端服务商时需要填写对应平台的 API Key。为避免误把旧服务商的 Key 发送给新地址，修改 API 地址且未填写新 Key 时，系统会主动清空已保存的 Key。本地 Ollama 默认使用 `http://localhost:11434/v1`，无需 API Key。

## 文档

- Schema 设计说明：`docs/SCREENPLAY_SCHEMA.md`
- JSON Schema：`schema/screenplay.schema.json`
- YAML 示例：`examples/example-screenplay.yaml`
- 参赛提交清单：`docs/SUBMISSION_CHECKLIST.md`
