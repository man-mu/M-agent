# Skill 市场与 MCP 管理优化 Phase 8 验证记录

验证时间：2026-06-06 12:45 +08:00

## 范围

- 验证计划 `2026-06-03-skill-market-mcp-optimization.md` 的 Phase 8：Skill 市场、MCP 管理、MCP 工具调试、真实聊天链路、PostgreSQL 持久化、前端桌面/移动端页面和本地服务关闭。
- 本记录只保存命令、结果和验收证据，不包含 `.local/model-providers.json`、`.local/mcp-keys.json` 或任何 API Key。
- 本轮真实验证使用临时 Prompt Skill `phase8-verify-skill`，验证结束前已通过包卸载接口清理。

## 自动化验证

后端全量测试：

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn test
```

结果：

- Maven 全量测试通过。
- 271 个测试运行，失败 0，错误 0，跳过 3。
- 覆盖 Skill 包导入/导出、Jar Skill、MCP 配置管理、MCP 工具调试、Skill 健康检查、调用记录、聊天控制器和持久化相关测试。

前端单测：

```powershell
cd ui-vue3
npm run test:unit
```

结果：

- 11 个测试文件通过。
- 61 个测试通过，失败 0。
- 覆盖 `skillForm.spec.ts`、`skillMarket.spec.ts`、`mcpTools.spec.ts`、聊天 Skill 选择器和状态工具等。

前端构建：

```powershell
cd ui-vue3
npm run build
```

结果：

- `vue-tsc --build --force` 通过。
- `vite build` 通过。

本地和风天气 MCP：

```powershell
cd tools/local-qweather-mcp
npm run test
npm run build
```

结果：

- 3 个测试文件通过。
- 10 个测试通过，失败 0。
- TypeScript 构建通过。

## 真实 HTTP / E2E 验证

环境：

- Docker Desktop 中 PostgreSQL 容器 `manmu-postgres` 为 healthy，`5432` 映射到 Windows localhost。
- 本地和风天气 MCP 启动到 `18090`。
- 后端使用 JDK 17 启动到 `18080`，日志确认 `Starting DeepResearchMvpApplication using Java 17.0.17`。
- 后端连接真实 PostgreSQL 17.9，Flyway 校验 8 个迁移，schema 已是最新。
- 前端 Vite 启动到 `5173`。

基础能力：

```powershell
curl.exe http://localhost:18080/api/app/capabilities
curl.exe http://localhost:18080/api/skills
curl.exe http://localhost:18080/api/mcp/status
curl.exe http://localhost:18080/api/mcp/servers
```

结果：

- `/api/app/capabilities` 返回 `skillEnabled=true`、`ragEnabled=true`、`mcpEnabled=true`。
- `/api/skills` 返回内置 `code-review`、`location-analyzer`、`weather-now` 和已有本地 `sample-skill`。
- `/api/mcp/status` 返回本地和风天气 MCP connected，`toolCount=1`，工具为 `weather_now`。
- 高德 MCP 内置配置保持 disabled，不影响本地天气 MCP。

## Skill 市场验证

测试包：

- `target/http-check/phase8-verify-skill.zip`
- zip 内容：
  - `skill.json`
  - `SKILL.md`
- Skill 名称：`phase8-verify-skill`
- 类型：Prompt Skill

验证命令：

```powershell
curl.exe -F "file=@target/http-check/phase8-verify-skill.zip" `
  http://localhost:18080/api/skills/packages/import

curl.exe http://localhost:18080/api/skills/phase8-verify-skill
curl.exe http://localhost:18080/api/skills/phase8-verify-skill/health
curl.exe -X PATCH http://localhost:18080/api/skills/phase8-verify-skill/toggle
curl.exe -X POST http://localhost:18080/api/skills/phase8-verify-skill/reload
curl.exe -o target/http-check/phase8-verify-skill-export.zip `
  http://localhost:18080/api/skills/phase8-verify-skill/export
```

结果：

- 导入返回 `packageType=PROMPT`、`storageLocation=LOCAL`、`enabled=true`。
- `/api/skills/phase8-verify-skill` 返回 definition 和 promptTemplate。
- 健康检查返回 `HEALTHY`。
- 启用/禁用、重载均可用。
- 导出接口返回 HTTP 200，导出 zip 大小为 925 bytes。
- 导入后无需重启后端即可在 `/skills` 页面和 API 中可见。

卸载与清理：

```powershell
curl.exe -X DELETE http://localhost:18080/api/skills/packages/phase8-verify-skill
curl.exe http://localhost:18080/api/skills/phase8-verify-skill
```

结果：

- 卸载接口返回 204。
- 卸载后 `GET /api/skills/phase8-verify-skill` 返回 404。
- `.local/skills/installed` 中只剩验证前已有的 `sample-skill`。

## MCP 管理与工具调试验证

连接测试：

```powershell
curl.exe -X POST http://localhost:18080/api/mcp/servers/{local-qweather-id}/test
```

结果：

- 返回 `connected=true`。
- `toolCount=1`。
- `toolNames=["weather_now"]`。
- `requiredEnvVars=["QWEATHER_API_KEY"]`，`keyConfigured=true`。

工具调试：

```powershell
curl.exe -H "Content-Type: application/json" `
  --data-binary "@target/http-check/weather-now-request.json" `
  http://localhost:18080/api/mcp/tools/weather_now/invoke
```

请求内容：

```json
{"location":"上海","lang":"zh","unit":"m"}
```

结果：

- 返回 `toolName=weather_now`。
- `error` 为空。
- 输出包含真实和风天气数据：上海当前阴，温度 27°，体感 26°，湿度 40%，东北风 3 级，观测时间 `2026-06-06T12:36+08:00`。

MCP reload：

```powershell
curl.exe -X POST http://localhost:18080/api/mcp/reload
```

结果：

- reload 后本地和风天气 MCP 仍为 connected。
- `toolCount=1`。

## 真实聊天链路与持久化

请求：

```powershell
curl.exe -N --max-time 180 -H "Content-Type: application/json" `
  --data-binary "@target/http-check/phase8-chat-request.json" `
  http://localhost:18080/chat/stream
```

请求要点：

- `query`: `@weather-now 查询上海实时天气`
- `enable_deepresearch`: `false`
- `auto_accepted_plan`: `true`
- `session_id`: `phase8-e2e-20260606124408`
- `thread_id`: `phase8-e2e-20260606124408-thread`

结果：

- SSE 返回 `event:message` 和 `event:done`。
- `done` 内容包含上海实时天气：阴、27°C、体感 26°C、湿度 40%、东北风 3 级、观测时间 `2026-06-06 12:38`。
- 后端调用记录显示 `weather-now` 有 `EXPLICIT` 和 `TOOL` 成功记录，输出包含 `weather_now MCP 工具真实返回`。

持久化验证：

```powershell
curl.exe http://localhost:18080/api/sessions/phase8-e2e-20260606124408/threads/phase8-e2e-20260606124408-thread
curl.exe http://localhost:18080/api/sessions/phase8-e2e-20260606124408/threads/phase8-e2e-20260606124408-thread/events
```

结果：

- session history 返回 `status=COMPLETED`。
- event history 可读取，包含 `node.delta` 和 `graph.completed` 事件。

## 浏览器验证

说明：

- Codex Browser 插件文件存在，但本轮启动时运行时资源写入失败，错误为系统找不到指定路径。
- 为避免跳过前端验证，本轮使用 Playwright 对同一本地前端地址执行等价浏览器检查。

桌面视口 `1280x720`：

- `/skills` 可渲染 Skill 市场，显示 5 个 Skill，包含 `phase8-verify-skill`；无横向溢出。
- `/mcp` 可渲染 MCP 工具页，显示本地和风天气 MCP connected、`weather_now` 工具和调试入口；无横向溢出；控制台 warning/error 为 0。
- `/chat` 可渲染研究工作台，显示会话列表、模型状态和输入框；无横向溢出；控制台 warning/error 为 0。

移动视口 `390x844`：

- `/skills` 显示 Skill 市场和 `phase8-verify-skill`，无横向溢出。
- `/mcp` 显示 MCP 工具、`weather_now` 和服务器配置，无横向溢出。
- `/chat` 显示研究工作台和输入区域，无横向溢出。

## 服务关闭验证

关闭对象：

- 本轮启动的 Maven/Java 后端进程。
- 本轮启动或残留的本地和风天气 MCP `node dist/index.js`。
- 本轮启动的 Vite 前端进程。
- PostgreSQL Docker 容器是本地基础依赖，本轮未停止该容器。

验证命令：

```powershell
Get-NetTCPConnection -LocalPort 18080,18090,5173 -ErrorAction SilentlyContinue |
  Where-Object { $_.State -eq 'Listen' }
```

结果：

- 无 `Listen` 输出。
- `18080`、`18090`、`5173` 已释放。

## Phase 8 结论

- 自动化测试、前端构建、本地 MCP 构建均通过。
- Skill 市场满足发现、安装、启用/禁用、导出、重载和卸载。
- MCP 管理满足服务器列表、连接测试、reload 和工具调试。
- `weather-now` 真实模型 + MCP 工具链路继续可用，返回真实天气数据。
- PostgreSQL 持久化可读取，聊天线程状态为 `COMPLETED`。
- `/skills`、`/mcp`、`/chat` 桌面和移动视口无明显横向溢出。
- 本轮验证结束后本地后端、MCP、前端服务均已关闭。
- 验证过程未读取、输出或提交任何 API Key。
