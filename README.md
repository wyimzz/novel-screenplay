# 镜页：AI 小说转剧本

基于 Java 21 和 Spring Boot 3.3 的小说改编工具。输入至少 3 个章节，系统会提取 Story Bible、规划场景、生成结构化剧本，并导出可编辑的 YAML 初稿。

## 核心流程

1. 解析并校验章节数量。
2. AI 提取人物、地点、关键道具，建立稳定 ID 的 Story Bible。
3. 各章节并行精编为详细场景，保留关键动作、对白、说话人、语气和原文证据。
4. Java 执行跨引用语义校验。
5. 作者在块编辑器中逐场修改动作与对白，重新校验并导出 YAML。

这套分阶段方法参考了 LumenX Studio 的实体提取和资产稳定引用设计，但只保留题目三需要的文本改编能力。

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
- DeepSeek 分阶段生成：Story Bible -> 分章详细剧本 -> 全局合并
- 人物、地点、道具稳定 ID
- 每章多场拆解与跨章节连续性
- 动作、对白、说话人、语气、旁白、转场等 Beat
- 场景连续性与原文证据
- 可视化场景/Beat 块编辑、自动重新编号、YAML 下载
- AI 失败时明确显示错误，不静默伪装成 AI 结果
- 无 API Key 时可使用离线规则模式演示

## 文档

- Schema 设计说明：`docs/SCREENPLAY_SCHEMA.md`
- JSON Schema：`schema/screenplay.schema.json`
- YAML 示例：`examples/example-screenplay.yaml`
- LumenX 参考说明：`docs/LUMENX_REFERENCE.md`
