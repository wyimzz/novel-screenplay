# 镜页：AI 小说转剧本

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

这套分阶段方法参考了 LumenX Studio 的实体提取和资产稳定引用设计，但只保留题目三需要的文本改编能力。

## 原创实现与第三方引用

本项目的 Java 后端、DeepSeek 接入、响应归一化、异步任务、YAML Schema、语义校验和浏览器编辑器均为本项目实现。项目没有复制 LumenX 源码，仅参考其公开产品流程中的分阶段生成、稳定实体引用和草稿确认思路，详细映射见 `docs/LUMENX_REFERENCE.md`。

参考项目：

- Alibaba LumenX Studio，MIT License：<https://github.com/alibaba/lumenx>

主要第三方依赖：

| 依赖 | 用途 |
|---|---|
| Spring Boot 3.3 | Web 服务、配置与接口 |
| Jackson Databind / YAML | JSON 响应归一化与 YAML 导出 |
| Jakarta Validation | 请求参数校验 |
| JUnit 5 / AssertJ | 自动化测试 |
| DeepSeek OpenAI-compatible API | 小说分析与剧本生成 |

第三方依赖版本以 `pom.xml` 为准。API Key 不进入 Git，保存在被 `.gitignore` 排除的 `.env` 中。

## 运行

离线规则模式：

```powershell
.\mvnw.cmd spring-boot:run
```

DeepSeek 模式：

```powershell
.\mvnw.cmd package
powershell -ExecutionPolicy Bypass -File .\run-deepseek.ps1 -Port 8090
```

浏览器访问 `http://localhost:8088`，使用脚本启动时访问指定端口。也可以在页面右上角的“AI 设置”中选择离线模式或配置 OpenAI 兼容接口。

## 当前能力

- 识别“第一章”和 `Chapter 1` 等章节标题
- 强制至少 3 章输入
- DeepSeek 分阶段生成：Story Bible -> 全书改编蓝图 -> 分章详细剧本 -> 全局合并
- AI 服务商切换：DeepSeek、智谱 GLM、通义千问、Moonshot/Kimi、OpenAI、Ollama 与自定义兼容接口
- 服务商内模型切换：DeepSeek V4 Flash/Pro、GLM 多型号等使用明确下拉框，也支持自定义模型 ID
- 场景级媒介结构：电影三幕与视听设计、电视剧 A/B 线与幕尾、短剧秒级钩子反转、舞台剧幕场与演员调度
- 后台生成任务与真实阶段进度，刷新页面请求不会长期占用单个 HTTP 连接
- AI JSON 异常时自动截取有效对象并执行一次结构修复
- 渐进式生成预览：故事圣经完成即展示，章节场景完成一批追加一批，最终校验后开放 YAML
- 人物、地点、道具稳定 ID
- 每章多场拆解与跨章节连续性
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
- LumenX 参考说明：`docs/LUMENX_REFERENCE.md`
- 参赛提交清单：`docs/SUBMISSION_CHECKLIST.md`
