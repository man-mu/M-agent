# 前端 Skill 与 MCP 页面 Phase 0 基线审计

审计时间：2026-06-02 19:43 +08:00

## 工作区状态

- 执行前 `git status --short` 为空。
- 本阶段未修改业务代码。
- 未读取、打印或提交 `.local/model-providers.json`。
- 未修改、删除或整理 `.claude/`、`.local/`、`target/`、`.idea/`。

## 自动检查

- Node 以 UTF-8 读取以下文件，未发现 `U+FFFD` 替换字符，也未命中计划中列出的典型 mojibake 串：
  - `ui-vue3/src/views/skills/index.vue`
  - `ui-vue3/src/views/settings/index.vue`
  - `ui-vue3/src/components/layout/index.vue`
- `cd ui-vue3 && npm run build` 通过，包含 `vue-tsc --build --force` 和 `vite build`。

## 真实后端 HTTP 验证

- Docker Desktop 已启动。
- PostgreSQL 容器 `manmu-postgres` 已启动并处于 healthy 状态，`5432` 正常监听。
- 后端使用 JDK 17、`target/classes` 和临时 runtime classpath 启动到 `18080`，日志和 PID 写在系统临时目录。
- 验证结果：
  - `GET /api/app/capabilities` 返回 `skillEnabled=true`、`ragEnabled=true`、`mcpEnabled=true`。
  - `GET /api/skills` 返回 2 个启用 Skill：`code-review`、`location-analyzer`。
  - `GET /api/mcp/status` 返回 `enabled=true`、`toolCount=0`、高德 MCP `connected=false`。
- 无 `AMAP_MAPS_API_KEY` 场景下，当前 MCP 状态错误不是清晰的缺 Key 文案，而是 `block()/blockFirst()/blockLast() are blocking, which is not supported in thread reactor-http-nio-4`。前端 Settings 只展示“未连接”和 `0 个工具`，没有指导用户配置高德 Web 服务 Key。

## 浏览器基线

浏览器使用 Vite 开发服务 `http://127.0.0.1:5173`，后端代理到 `18080`。分别检查 `1280x720` 与 `390x844`：

- `/settings`
  - 桌面端无页面级水平溢出，中文显示正常。
  - 移动端无真实乱码，但“扩展能力”卡片较拥挤，MCP 服务描述和 `https://mcp.amap.com/sse?key=${AMAP_MAPS_API_KEY}` 在卡片内折行很生硬。
  - Settings 当前承担了模型设置和 Skill/MCP 状态摘要，MCP 缺 Key/失败排障信息不足。
- `/skills`
  - 桌面端中文显示正常，基础列表、刷新、新建入口可见。
  - 移动端无真实乱码，但表格布局不可用：描述列被压成逐字竖排，操作列向右超出可视区域，页面出现横向滚动条。
  - 当前页面仍是基础 CRUD：缺少搜索、筛选、依赖展示、参数 Schema 编辑、Prompt 预览和前端 name/JSON 校验。
- `/chat`
  - 桌面端和移动端中文显示正常。
  - 移动端输入 `@code` 可显示 `@code-review` 候选，候选卡不遮挡发送按钮。
  - 当前导航仍显示英文 `Skills`，不是纯中文用户入口。
  - 依赖 MCP 的 Skill 在 MCP 未连接时没有明显提示或跳转到 MCP 排障入口。

截图保存在系统临时目录：

- `C:\Users\20232\AppData\Local\Temp\m-agent-phase0-screenshots\desktop-settings.png`
- `C:\Users\20232\AppData\Local\Temp\m-agent-phase0-screenshots\desktop-skills.png`
- `C:\Users\20232\AppData\Local\Temp\m-agent-phase0-screenshots\desktop-chat.png`
- `C:\Users\20232\AppData\Local\Temp\m-agent-phase0-screenshots\mobile-settings.png`
- `C:\Users\20232\AppData\Local\Temp\m-agent-phase0-screenshots\mobile-skills.png`
- `C:\Users\20232\AppData\Local\Temp\m-agent-phase0-screenshots\mobile-chat-skill-picker.png`

## Phase 0 结论

- 源码层面和浏览器实际页面均未发现 Skill/MCP 相关中文乱码；PowerShell 输出乱码属于终端编码显示问题。
- `/settings` 当前能展示 Skill/MCP 启用状态，但 MCP 失败态不够可理解，移动端摘要区域拥挤。
- `/skills` 当前桌面端可做基础操作，但移动端表格布局明显不可用，管理能力也未达到计划后续阶段要求。
- `/chat @skill` 当前基础候选可用，移动端不遮挡发送按钮，但缺少 MCP 未连接时对依赖 MCP Skill 的提示。
- `/api/mcp/status` 在无 Key 场景下没有返回用户友好的缺 Key 状态，且暴露了底层 Reactor blocking 异常文案。
