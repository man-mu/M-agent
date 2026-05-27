# Phase 7.0 演示体验基线审计

审计时间：2026-05-27

## 执行范围

- 仅执行 Phase 7.0: Demo Baseline Audit。
- 未开始 Phase 7.1，未修改前端或后端实现代码。
- 已读取 `AGENTS.md` 和计划文档 `docs/superpowers/plans/2026-05-27-frontend-deepresearch-demo-experience.md`。
- `git status --short` 初始状态：
  - `M AGENTS.md`
  - `M CLAUDE.md`
  - `?? .claude/`
- 上述为既有非本阶段改动，本阶段未回滚、整理、删除或提交。
- 未读取或输出 `.local/model-providers.json` 内容；仅通过真实 HTTP 接口确认当前模型 `apiKeyConfigured=true`。

## 视频基线

视频文件：`C:\Downloads\deep_research.mov`

本机无 `ffprobe` / `ffmpeg` 命令，应用内浏览器因安全策略不能直接打开本地 `file://` 视频。为完成审计，临时在 `target/phase7-pydeps` 安装 `imageio` / `imageio-ffmpeg`，并抽帧到 `target/phase7-video-frames`。

视频元数据：

- 时长：约 89.12 秒。
- 尺寸：2140 x 1080。
- 帧率：30 fps。
- 编码：H.264。
- 抽帧接触表：`target/phase7-video-frames/contact-sheet.png`。

可借鉴并可用当前真实前后端能力表达的效果：

- 双栏研究工作台：左侧对话、右侧研究/工作流/报告区域。
- 研究过程时间线：开始、意图识别、查询相关信息、背景调查、研究计划、并行研究节点、报告生成、完成。
- 节点状态表达：已完成、运行中、等待中、失败/停止等状态标签。
- 真实来源列表展示：当前项目已有 `site_information` / SSE 事件来源字段时可以展示，不存在时不能伪造。
- 报告阅读面板：当前项目已有 SSE 最终报告和持久化报告接口，可作为 Phase 7 报告展示基础。
- 模式开关：视频中的“深度模式”可映射为现有快速回答 / 深度研究、自动执行计划开关。
- 历史会话入口：视频侧栏的新会话/清空会话可映射为当前真实会话历史能力。

暂不纳入或需要后端/额外前端能力支撑的效果：

- 知识库顶层导航与知识库管理。
- 在线 HTML 报告、下载报告、HTML 源码查看、HTML 预览弹窗。
- 图表化报告预览，例如饼图、半圆图、可视化 dashboard。
- 伪造搜索结果、伪造引用、伪造节点结果。
- 多语言切换、用户头像/登录系统。
- deepresearch-main 的完整 RAG、MCP、Redis、多 Agent 编排前端。

## 环境状态

- Docker 容器 `manmu-postgres`：`Up 7 hours (healthy)`。
- PostgreSQL 端口：容器 5432 映射到 Windows `localhost:5432`。
- 后端 18080 初始状态：无监听进程。
- 前端 5173 初始状态：无监听进程。

后端验证时使用 JDK 17 临时启动：

- 启动命令：`mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18080`
- 启动 PID 写入：`target/phase7-backend.pid`
- 日志写入：`target/phase7-backend-run.out.log` / `target/phase7-backend-run.err.log`
- Flyway 验证：PostgreSQL 17，当前 schema version 7，迁移已是最新。

前端验证时启动 Vite：

- 启动命令：`npm run dev -- --host 127.0.0.1 --port 5173`
- 启动 PID 写入：`target/phase7-frontend.pid`
- 日志写入：`target/phase7-frontend-run.out.log` / `target/phase7-frontend-run.err.log`

## 前端构建

命令：

```powershell
Set-Location C:\MainData\code\Claude_project\M-agent\ui-vue3
npm run build
```

结果：通过。

重点 chunk 输出：

- `vue-DaSHdH0-.js`：370.93 kB，gzip 133.71 kB。
- `markdown-r2kcheL8.js`：180.81 kB，gzip 65.52 kB。
- `highlight-tqtQG8Q9.js`：66.24 kB，gzip 21.71 kB。
- 页面/业务 chunk：
  - `index-BIvRX8Iq.js`：244.55 kB，gzip 74.62 kB。
  - `index-CVfsWlkZ.js`：202.14 kB，gzip 61.17 kB。
  - `index-DGuINIPG.js`：154.30 kB，gzip 48.03 kB。
  - `index-BPQeqPPw.js`：64.02 kB，gzip 19.42 kB。
- 未出现 Vite 大 chunk warning。

## 真实 HTTP 接口

命令：

```powershell
curl.exe -sS http://localhost:18080/api/model/current
curl.exe -sS http://localhost:18080/api/app/capabilities
curl.exe -sS http://localhost:18080/api/conversations
```

结果：

- `/api/model/current` 返回 `DeepSeek / deepseek-chat`，`apiKeyConfigured=true`。
- `/api/app/capabilities` 返回 `skillEnabled=false`、`ragEnabled=true`、`mcpEnabled=false`。
- `/api/conversations` 返回真实 PostgreSQL 会话列表，最新会话为 Phase 5 E2E 验证样本。

补充接口检查：

- `/api/conversations/44f4868d-a8a3-42de-af58-cd79a9af2ee1` 可读取历史详情。
- `/api/reports/session/0bb3ccb3-6d52-449b-b32e-05c80f2ff598` 可读取持久化报告，状态 `COMPLETED`。

## 浏览器基线

验证目标：`http://127.0.0.1:5173`

### 1280 x 720

- `/chat` 空态：
  - 可见标题 `研究对话`、空态说明、深度研究开关、自动执行计划开关、输入框。
  - 无当前 `threadId` 时报告按钮禁用。
  - `documentElement.scrollWidth === clientWidth`，无主文档横向溢出。
- 历史会话详情：
  - 点击历史会话后进入 `/chat/{sessionId}`。
  - 可恢复用户消息、AI 消息和 `Thread`。
  - 完成会话中报告按钮可用。
- 报告面板：
  - 点击报告按钮后右侧 `aside.report-panel.open` 打开。
  - 可显示持久化报告内容。
  - 关闭按钮存在，点击后 `.report-panel.open` 消失。
  - 无主文档横向溢出。
- `/settings`：
  - 可见当前模型 `DeepSeek / deepseek-chat`，API Key 状态为已配置。
  - 供应商列表可见，包含 DashScope、DeepSeek、MiniMax、Moonshot、Zhipu、01.AI。
  - 无主文档横向溢出。
- `/skills`：
  - 因 `skillEnabled=false`，页面显示 `Skill 模块未启用`。
  - 未出现 `新建 Skill` 入口。
  - 无主文档横向溢出。

### 390 x 844

- `/chat` 空态：
  - 顶部导航、历史横向卡片、主内容和输入区可见。
  - 主文档无横向溢出。
  - 历史列表为内部横向滚动卡片，属于当前设计基线。
- 历史会话详情：
  - 点击第一张历史卡片后进入对应 `/chat/{sessionId}`。
  - 消息、Thread、报告按钮可见。
  - 主文档无横向溢出。
- 报告面板：
  - 点击报告按钮后报告面板在移动端底部打开。
  - 关闭按钮可用，关闭后 `.report-panel.open=false`。
  - 主文档无横向溢出。
- `/settings`：
  - 当前模型卡片和供应商卡片按单列展示。
  - 无主文档横向溢出。
- `/skills`：
  - 显示 `Skill 模块未启用` 和 `返回对话`。
  - 未出现 `新建 Skill` 入口。
  - 无主文档横向溢出。

浏览器控制台：未发现 error 级别日志。

## Phase 7.0 新发现问题

- 移动端历史列表按钮文本匹配在自动化中会产生大量重复命中，因为历史卡片文本同时出现在按钮、删除按钮和可访问树中。后续若补 E2E，应优先增加稳定 `data-testid` 或缩小历史列表容器选择器。
- 移动端历史列表采用内部横向滚动，主文档无横向溢出；这符合当前 Phase 6 基线，但 Phase 7.5 若要增强演示扫描性，需要注意卡片状态和删除按钮不要挤占可点击区域。
- `/chat` 当前首屏仍偏“普通聊天 + workflow 标题”，尚未明显呈现当前模型、会话状态、模式状态条；这是 Phase 7.1 的主要空间。
- 报告面板当前只显示报告正文和刷新/关闭，尚未展示来源、报告状态、复制等工具；这是 Phase 7.4 的主要空间。
- 视频中的 HTML 报告/图表/下载能力不应被 Phase 7.1-7.5 当作当前后端真实能力。

## Acceptance Criteria 对照

- 已明确记录视频效果中可用真实数据表达的部分，以及需要额外能力、暂不纳入的部分。
- 未读取或泄露 `.local/model-providers.json` 内容。
- 已启动真实后端和前端进行验证；测试结束前需要关闭服务并确认 18080 / 5173 释放。
- 已记录 `npm run build` chunk 输出。
- 已通过真实 HTTP 接口验证 `/api/model/current`、`/api/app/capabilities`、`/api/conversations`。
- 已在 1280x720 和 390x844 验证 `/chat`、历史详情、报告面板展开/关闭、`/settings`、`/skills` 禁用态。
