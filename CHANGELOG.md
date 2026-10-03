# 更新日志 (Changelog)

本项目所有重要的功能变更、Bug 修复与部署记录都会汇总到这里。

版本号规则：`主版本.次版本.补丁`（语义化版本，配合前端 `package.json` 的 `version` 保持一致）。

---

## [Unreleased]

### 🐛 修复

#### AI 润色/大纲/摘要/问答按钮超时（改为异步 MQ）

- **问题**：点击"润色内容"等同步 AI 按钮时报错 `timeout of 10000ms exceeded`（前端 axios 10s 超时）或 `AI 调用异常：Read timed out`（后端 60s 超时）。真实模型推理单次耗时可能 >60s，两层阻塞超时均不足。
- **改动**（`frontend/src/views/NoteEditView.vue`）：
  - 将 `生成大纲 / 润色内容 / 生成摘要 / 内容问答` 四个同步 AI 按钮统一改为**提交异步任务（RabbitMQ）+ 轮询结果**。
  - 新增通用轮询函数 `pollTask()`，替换原同步调用 `aiApi.outline / polish / summarize / chat`。
  - 清理冗余的同步轮询 `pollAsyncTask` 与 `applyAi`。
- **效果**：AI 生成不再受 10s/60s 阻塞超时影响；异步任务由消费者处理、前端轮询直至出结果。
- **部署**：已更新本地与服务器 `frontend`，`docker compose build web` + 重启 `ai-note-web` 完成；服务器产物 `NoteEditView-DiQBJQB4.js` 已验证包含 `asyncSubmit` 且不再调用同步 `/ai/polish`。

---

## 历史
（暂无历史版本记录）