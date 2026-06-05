# LumenX Studio 参考与 Java 裁剪方案

参考项目：<https://github.com/alibaba/lumenx>

LumenX Studio 使用 MIT License，完整流程覆盖小说到动态视频。当前项目只实现题目要求的“小说转结构化 YAML 剧本”，因此采用其前半段方法论，并控制工程范围。

## 采用的设计

| LumenX 能力 | 当前 Java 项目实现 |
|---|---|
| 剧本分析 | `ChapterParser` 与 `AiScreenplayGenerator` |
| 角色、场景、道具提取 | 第一阶段独立生成 Story Bible |
| 资产稳定引用 | `char_*`、`loc_*`、`prop_*` ID |
| Storyboard 结构 | 按章节并行生成 `scenes` 与详细 `beats` |
| 可视化编辑 | 场景导航与动作/对白块编辑器 |
| AI 模型抽象 | `ScreenplayGenerator` 接口和页面 AI 设置 |
| 生成过程反馈 | Story Bible、场景规划、剧本生成阶段状态 |
| 失败可见性 | API、解析和语义错误直接反馈，不静默降级 |

## 暂不采用的设计

- 文生图与图生视频
- Three.js 分镜画布
- TTS、音效和视频合成
- OSS 媒体资产管理
- FFmpeg 渲染链路

这些能力超出题目三验收范围，而且会显著增加模型费用、运行环境和演示稳定性风险。

## 当前实现与后续边界

当前版本已经完成 Story Bible 提取、分章详细剧本生成、块编辑和 YAML 重导出。和 LumenX 的 `frames` 思路一致，Beat 显式保存动作、对白、说话人和语气；Java 层逐字段归一化模型输出，而不是直接信任一次反序列化。

分镜图片与视频生成仍不进入题目三首个验收版本。后续可增加实体提取确认弹窗与拖拽调整场景顺序，但不需要引入 LumenX 的媒体渲染链路。
