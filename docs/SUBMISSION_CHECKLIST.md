# 参赛提交清单

## 仓库

- [x] GitHub 仓库可访问：<https://github.com/wyimzz/novel-screenplay>
- [x] README 说明作品目标、运行方式、核心流程和原创改进
- [x] README 列明第三方依赖与相关开源参考项目
- [x] API Key、构建目录和本地数据已加入 `.gitignore`
- [ ] 截止后按赛事要求确认仓库公开可访问

## 持续交付

- [x] 功能开发使用独立分支
- [x] 每个 commit 只包含一个明确主题
- [x] PR 模板要求填写功能描述、实现思路和测试方式
- [ ] 后续功能继续通过小粒度 PR 合并，不直接在主分支堆积修改
- [ ] 提交前检查所有 commit 时间戳位于批次开始与截止时间内

## 必交作品

- [x] 可运行源码
- [x] README 文档
- [x] YAML Schema 设计文档：`docs/SCREENPLAY_SCHEMA.md`
- [x] JSON Schema：`schema/screenplay.schema.json`
- [x] YAML 示例：`examples/example-screenplay.yaml`
- [ ] 有声音讲解的 Demo 视频
- [ ] 将可公开播放的视频链接放到 README 的“Demo 视频”章节

## Demo 视频建议脚本

1. 说明题目目标和三章以上输入约束。
2. 展示 AI 设置与 DeepSeek 连接测试。
3. 载入三章示例并启动改编。
4. 展示 Story Bible、改编蓝图阶段和真实生成进度。
5. 展示每个场景中的动作、对白、说话人和原文证据。
6. 在块编辑器中修改一句对白或增加一个动作。
7. 应用修改并下载 YAML。
8. 打开 Schema 文档，说明稳定 ID、Beat、连续性和忠实度字段的设计原因。

## 提交前验证

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
git status
git log --oneline --decorate
```

确认主分支保持可运行，PR 描述与实际变更一致，且仓库中不存在 API Key。
