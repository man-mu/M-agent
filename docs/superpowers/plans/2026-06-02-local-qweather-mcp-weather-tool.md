# 本地和风天气 MCP 工具实现计划

## Background

当前项目是 Java 17 / Maven / Spring Boot 3.4.x 后端，MCP 客户端已通过 `spring-ai-starter-mcp-client-webflux` 接入，核心配置位于：

- `src/main/resources/mcp-config.json`
- `src/main/java/top/lanshan/manmu/config/McpProperties.java`
- `src/main/java/top/lanshan/manmu/mcp/McpConfigMergeUtil.java`
- `src/main/java/top/lanshan/manmu/mcp/McpToolProvider.java`
- `src/main/java/top/lanshan/manmu/mcp/McpStatusController.java`

当前后端使用 `WebFluxSseClientTransport`，也就是连接远程 HTTP + SSE MCP Server。现有 `mcp-config.json` 指向高德 MCP，需要 `AMAP_MAPS_API_KEY`，申请和配置较麻烦。

本计划先新增一个本地 MCP Server，只暴露一个可生产形态验证的天气工具，调用和风天气 API，而不是 mock。用户会获取和风天气 Key 并放入项目本地配置或环境变量中。密钥不得提交到源码、测试断言或提交记录。

和风天气官方信息：

- API Key 可通过请求头 `X-QW-Api-Key` 鉴权。
- 城市搜索接口：`/geo/v2/city/lookup`，用于把城市名解析为 Location ID。
- 实时天气接口：`/v7/weather/now`，`location` 参数接受 Location ID 或经纬度。

MCP 侧优先用 TypeScript / Node.js 实现，因为官方 TypeScript SDK 成熟，适合独立工具服务；本项目后端当前只支持 SSE MCP 客户端，因此第一版必须提供 `/sse` 兼容入口。

执行前必须先检查：

```powershell
git status --short
```

不要修改、删除或整理 `.claude/`、`.local/`、`target/`、`.idea/`。如果需要临时日志、PID、curl 请求体，优先放在 `%TEMP%`；如果必须放 `target/`，需明确说明原因并在验证后清理。

## Overall Goals

1. 在项目内新增独立本地 MCP Server，提供一个天气查询工具。
2. MCP 工具调用和风天气真实 API：先城市搜索，再实时天气。
3. 后端通过现有 MCP SSE 客户端连接本地 MCP Server，并在 `/api/mcp/status` 看到工具可用。
4. 前端 `/mcp` 能显示本地天气 MCP 状态和工具数量。
5. 通过真实 HTTP/SSE 路径验证：本地 MCP Server、Spring Boot 后端、PostgreSQL、模型供应商和 Skill 调用链路。
6. 不泄露和风天气 Key，不把 Key 写入源码、测试或提交记录。

## Non-goals

- 不实现完整地图、POI、路线规划、空气质量、灾害预警等能力。
- 不迁移后端到 Streamable HTTP MCP 客户端，第一版兼容现有 SSE 客户端。
- 不把天气 API 调用直接写入 Spring Boot 后端业务代码。
- 不使用 mock 天气数据替代真实和风天气 API。
- 不新增数据库表或持久化天气查询结果。
- 不改动 `.local/model-providers.json`，不读取或打印其中的模型 Key。

## Phase 0: Baseline And Transport Audit

### Goal

确认当前 MCP 客户端行为、配置结构、前端状态页展示，以及本地 MCP Server 应兼容的协议形态。

### Main Changes

原则上本阶段不改代码，只记录事实。可新增或更新 `docs/superpowers/plans/` 下的执行记录。

检查文件：

- `src/main/resources/mcp-config.json`
- `src/main/java/top/lanshan/manmu/config/McpProperties.java`
- `src/main/java/top/lanshan/manmu/mcp/McpConfigMergeUtil.java`
- `src/main/java/top/lanshan/manmu/mcp/McpToolProvider.java`
- `src/main/java/top/lanshan/manmu/mcp/McpStatusController.java`
- `src/test/java/top/lanshan/manmu/mcp/*Test.java`
- `ui-vue3/src/views/mcp/index.vue`
- `ui-vue3/src/services/api/app.ts`

确认点：

- 后端当前只创建 `WebFluxSseClientTransport`。
- `mcp-config.json` 的 `url` 是 base URL，`sse-endpoint` 默认 `/sse`。
- `allowed-tools` 会限制实际暴露给模型的 MCP 工具名。
- `/api/mcp/status` 当前返回 server URL、SSE endpoint、description、connected、error、toolCount。

### Tests

```powershell
git status --short
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=top.lanshan.manmu.mcp.*Test' test
```

### Real Verification

启动后端到 `18080`，不设置任何高德 Key，验证当前状态：

```powershell
curl.exe http://localhost:18080/api/app/capabilities
curl.exe http://localhost:18080/api/mcp/status
```

测试完成后关闭后端并确认：

```powershell
Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue
```

### Acceptance Criteria

- 明确当前后端需要本地 MCP Server 提供 `/sse`。
- 明确当前状态页能否区分本地 MCP 和高德 MCP。
- 没有改动 `.claude/`、`.local/`、`target/`、`.idea/`。

### Suggested Commit

如果只新增审计记录：`记录本地天气 MCP 接入基线`

## Phase 1: Scaffold Local QWeather MCP Server

### Goal

新增一个独立 TypeScript MCP Server 工程，能本地启动，并通过 SSE 暴露工具列表。

### Main Changes

新增目录建议：

- `tools/local-qweather-mcp/package.json`
- `tools/local-qweather-mcp/tsconfig.json`
- `tools/local-qweather-mcp/vitest.config.ts`
- `tools/local-qweather-mcp/src/index.ts`
- `tools/local-qweather-mcp/src/config.ts`
- `tools/local-qweather-mcp/src/qweatherClient.ts`
- `tools/local-qweather-mcp/src/weatherTool.ts`
- `tools/local-qweather-mcp/src/*.spec.ts`
- `tools/local-qweather-mcp/README.md`

建议技术栈：

- TypeScript
- Node.js 20 或 22
- `@modelcontextprotocol/sdk`
- `express`
- `zod`
- `vitest`
- Node 原生 `fetch`

环境变量约定：

- `QWEATHER_API_KEY`：和风天气 API Key，必填。
- `QWEATHER_API_HOST`：和风天气 API Host，可选，默认使用公共 API Host 或用户控制台分配的 Host。
- `LOCAL_QWEATHER_MCP_PORT`：本地 MCP 端口，默认 `18090`。

第一版只注册一个工具：

- 工具名：`weather_now`
- 描述：查询指定城市或经纬度的实时天气。
- 输入参数：
  - `location`：必填，城市名、Location ID 或 `lon,lat`。
  - `adm`：可选，上级行政区，用于城市重名消歧。
  - `lang`：可选，默认 `zh`。
  - `unit`：可选，`m` 或 `i`，默认 `m`。

工具内部流程：

1. 如果 `location` 看起来是和风 Location ID 或经纬度，直接调用 `/v7/weather/now`。
2. 如果是城市名，先调用 `/geo/v2/city/lookup` 获取首个匹配城市的 Location ID。
3. 再调用 `/v7/weather/now` 获取实时天气。
4. 返回结构化 JSON 和简短中文摘要。

错误处理：

- 没有 `QWEATHER_API_KEY`：启动可以成功，但工具调用返回清晰错误。
- 城市搜索无结果：返回“未找到城市，请补充行政区或经纬度”。
- 和风 API 返回非 `200` code：保留 code 和用户可理解 message，不输出 Key。
- 网络超时：返回超时说明，避免泄露堆栈。

### Tests

```powershell
cd tools/local-qweather-mcp
npm install
npm run test
npm run build
```

单测重点：

- `config.ts` 不打印 Key，只判断是否存在。
- `qweatherClient.ts` 正确设置 `X-QW-Api-Key` Header。
- `weatherTool.ts` 城市名会先走 geo lookup。
- API 错误会转换为用户友好错误。

单测不使用真实 Key，不断言任何真实 Key 内容。

### Real Verification

用户把 Key 放入当前 PowerShell 环境：

```powershell
$env:QWEATHER_API_KEY='用户自己的 Key'
$env:QWEATHER_API_HOST='用户控制台分配的 API Host 或默认 Host'
cd tools/local-qweather-mcp
npm run dev
```

使用 MCP inspector 或项目后端连接前，先用服务自检端点验证：

```powershell
curl.exe http://localhost:18090/health
```

如果实现提供调试端点，可验证真实 API：

```powershell
curl.exe "http://localhost:18090/debug/weather-now?location=北京"
```

调试端点只用于本地开发，不应暴露 Key。

### Acceptance Criteria

- 本地 MCP Server 可启动。
- `/sse` 可供 MCP Client 连接。
- `weather_now` 工具定义清晰，参数 schema 完整。
- 未配置 Key 时错误清晰；配置 Key 后可返回真实天气。
- 没有新增 mock 天气数据。

### Suggested Commit

`新增本地和风天气 MCP 服务`

## Phase 2: Connect Spring Backend To Local Weather MCP

### Goal

让当前 Spring Boot 后端通过现有 MCP 配置连接本地天气 MCP 服务。

### Main Changes

更新或新增配置：

- `src/main/resources/mcp-config.json`

建议将高德 MCP 先保留但禁用，新增本地和风天气 MCP：

```json
{
  "url": "http://127.0.0.1:18090",
  "sse-endpoint": "/sse",
  "description": "本地和风天气 MCP - 查询城市实时天气",
  "enabled": true,
  "allowed-tools": [
    "weather_now"
  ]
}
```

如果前端 `/mcp` 需要展示更多信息，可在后续阶段增强：

- `src/main/java/top/lanshan/manmu/config/McpProperties.java`
- `src/main/java/top/lanshan/manmu/mcp/McpToolProvider.java`
- `ui-vue3/src/services/api/app.ts`
- `ui-vue3/src/views/mcp/index.vue`

但本阶段优先保持最小改动：只要 `/api/mcp/status` 能显示本地 server connected 和 `toolCount > 0`。

### Tests

后端聚焦测试：

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=top.lanshan.manmu.mcp.*Test' test
```

前端如只配置后端无需跑；如果改了 `/mcp` 页面：

```powershell
cd ui-vue3
npm run test:unit
npm run build
```

### Real Verification

1. 启动 PostgreSQL Docker，并确认健康：

```powershell
docker ps
Get-NetTCPConnection -LocalPort 5432 -ErrorAction SilentlyContinue
```

2. 启动本地和风天气 MCP：

```powershell
$env:QWEATHER_API_KEY='用户自己的 Key'
$env:QWEATHER_API_HOST='用户控制台分配的 API Host 或默认 Host'
cd tools/local-qweather-mcp
npm run dev
```

3. 启动 Spring Boot 后端到 `18080`。

4. 验证状态：

```powershell
curl.exe http://localhost:18080/api/app/capabilities
curl.exe http://localhost:18080/api/mcp/status
```

期望：

- `mcpEnabled: true`
- 本地 server `connected: true`
- `toolCount >= 1`

5. 测试完成后关闭本地 MCP 和后端，并确认端口释放：

```powershell
Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue
Get-NetTCPConnection -LocalPort 18090 -ErrorAction SilentlyContinue
```

### Acceptance Criteria

- 后端能连接 `http://127.0.0.1:18090/sse`。
- `/api/mcp/status` 能看到本地天气 MCP 已连接。
- `toolCount` 包含 `weather_now`。
- 失败时错误不泄露 Key。

### Suggested Commit

`接入本地和风天气 MCP`

## Phase 3: Add Weather Skill For Chat Path

### Goal

新增一个 Skill，让用户可以在聊天里通过 `@weather-now` 显式调用天气查询能力，并让模型有机会通过 MCP 工具获取真实天气。

### Main Changes

新增 Skill 内容：

- `src/main/java/top/lanshan/manmu/skill/content/weather-now/skill.json`
- `src/main/java/top/lanshan/manmu/skill/content/weather-now/SKILL.md`

建议 `skill.json`：

- `name`: `weather-now`
- `description`: 查询指定城市实时天气，优先使用本地和风天气 MCP 工具。
- `enabled`: `true`
- `dependencies`: `["mcp-qweather"]`
- `parameters`:
  - `location` required
  - `adm` optional
  - `unit` optional default `m`
  - `lang` optional default `zh`

`SKILL.md` 要明确要求模型：

- 使用 `weather_now` MCP 工具查询真实天气。
- 如果城市重名，优先根据用户上下文或 `adm` 消歧。
- 返回温度、体感温度、天气现象、湿度、风向风力、观测时间。
- 如果工具不可用，提示用户检查本地 MCP 服务和 `QWEATHER_API_KEY`。

### Tests

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=top.lanshan.manmu.skill.*Test,top.lanshan.manmu.mcp.*Test' test
```

前端 Skill 页面已支持 CRUD 和依赖展示，可跑：

```powershell
cd ui-vue3
npm run test:unit -- skillForm.spec.ts skillPicker.spec.ts
npm run build
```

### Real Verification

1. 启动 PostgreSQL、本地和风天气 MCP、后端、前端。
2. 打开 `/skills`，确认 `weather-now` 存在、启用、依赖为 `mcp-qweather`。
3. 打开 `/mcp`，确认本地天气 MCP connected。
4. 打开 `/chat`，输入：

```text
@weather-now 查询北京今天实时天气
```

5. 使用真实模型供应商路径验证 SSE：

- SSE 能正常流式输出。
- 回答中包含真实和风天气字段。
- 不能出现 mock 字样。

6. 查询结束后检查报告/会话持久化接口，确认线程状态按项目规则正确保存。
7. 关闭服务，确认 `18080`、`18090`、`5173` 释放。

### Acceptance Criteria

- `@weather-now` 可从聊天页触发。
- 模型能看到并使用 `weather_now` 工具。
- 回答基于真实和风天气 API 返回。
- 错误状态能引导用户检查 Key 或本地 MCP 服务。

### Suggested Commit

`新增天气查询 Skill`

## Phase 4: Frontend MCP Page Polish For Local Weather

### Goal

让 `/mcp` 页面能清楚展示“本地和风天气 MCP”，而不是只面向高德工具描述。

### Main Changes

可能涉及：

- `ui-vue3/src/views/mcp/index.vue`
- `ui-vue3/src/views/mcp/mcpTools.ts`
- `ui-vue3/src/views/mcp/mcpTools.spec.ts`
- `ui-vue3/src/services/api/app.ts`
- `src/main/java/top/lanshan/manmu/mcp/McpToolProvider.java`

页面显示建议：

- 服务名：本地和风天气 MCP
- 状态：已连接 / 未连接 / 缺少 Key / 服务未启动
- 工具列表：
  - 实时天气查询：`weather_now`
- Key 指引：
  - 本地 PowerShell 设置 `QWEATHER_API_KEY`
  - 可选设置 `QWEATHER_API_HOST`
- 不显示真实 Key。

后端如果要让前端展示 `allowedTools` 或 `keyEnvName`，需扩展 `ServerStatus`，但不得返回 Key 值。

### Tests

```powershell
cd ui-vue3
npm run test:unit -- mcpTools.spec.ts
npm run build
```

如果扩展后端状态模型：

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=top.lanshan.manmu.mcp.*Test,top.lanshan.manmu.api.AppInfoControllerTest' test
```

### Real Verification

1. 未启动本地 MCP：`/mcp` 显示服务未连接。
2. 启动本地 MCP 但不配置 Key：`/mcp` 显示连接状态和 Key 指引；工具调用时错误清晰。
3. 配置 Key 并启动本地 MCP：`/mcp` 显示 connected 和工具数量。
4. 在 `390x844` 和 `1280x720` 视口检查无乱码、无重叠、无按钮溢出。

### Acceptance Criteria

- `/mcp` 面向本地和风天气工具可读。
- 用户知道下一步如何设置 Key 和启动服务。
- 不泄露任何 Key。

### Suggested Commit

`优化本地天气 MCP 展示`

## Phase 5: Full Validation And Handoff

### Goal

完成全链路验证，确保本地 MCP 服务、后端、前端、Skill、真实模型路径都能稳定协作。

### Tests

本地 MCP：

```powershell
cd tools/local-qweather-mcp
npm run test
npm run build
```

后端：

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn test
```

前端：

```powershell
cd ui-vue3
npm run test:unit
npm run build
```

### Real Verification

1. Docker/PostgreSQL 健康。
2. 本地 MCP 启动到 `18090`，已配置 `QWEATHER_API_KEY`。
3. 后端启动到 `18080`。
4. 前端启动到 `5173`。
5. HTTP 验证：

```powershell
curl.exe http://localhost:18080/api/app/capabilities
curl.exe http://localhost:18080/api/mcp/status
curl.exe http://localhost:18080/api/skills
```

6. 浏览器验证：

- `/mcp`
- `/skills`
- `/chat`

7. 聊天真实验证：

```text
@weather-now 查询上海实时天气
```

8. 持久化验证：

- 查询会话历史接口。
- 确认线程状态和报告/消息内容可读取。

9. 关闭所有本地服务并确认：

```powershell
Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue
Get-NetTCPConnection -LocalPort 18090 -ErrorAction SilentlyContinue
Get-NetTCPConnection -LocalPort 5173 -ErrorAction SilentlyContinue
```

### Acceptance Criteria

- 所有自动化测试通过，或明确记录外部依赖原因。
- 本地 MCP 工具能通过和风天气真实 API 查询天气。
- 后端 `/api/mcp/status` 显示 connected。
- 聊天 `@weather-now` 能走真实模型 + MCP 工具路径。
- 临时进程全部关闭。
- 没有提交任何 Key。

### Suggested Commit

`验证本地天气 MCP 全链路`

## 执行结果（2026-06-03 Phase 5）

Phase 5 已完成全量验证和收尾。

自动化测试：

- 本地 MCP：`npm run test` 通过，3 个测试文件、10 个用例通过；`npm run build` 通过。
- 后端：使用 `JAVA_HOME=C:\WorkResources\JDKs\JDK17` 执行 `mvn test` 通过，230 个用例运行，0 失败，0 错误，3 个跳过。
- 前端：`npm run test:unit` 通过，10 个测试文件、51 个用例通过；`npm run build` 通过。

真实链路验证：

- Docker/PostgreSQL：`manmu-postgres` 验证时为 healthy，`vector` 扩展可用且已安装；该容器是验证前已存在的本地数据库服务，未擅自关闭。
- 本地 MCP：启动到 `127.0.0.1:18090`，`/health` 返回 `keyConfigured: true`；`/debug/weather-now?location=上海` 通过和风天气真实 API 返回上海实时天气，包含 Location ID `101020100`、观测时间、温度、体感、湿度、风向风力等字段。
- 后端：启动到 `18080`，Flyway 8 个迁移校验通过，pgvector 初始化正常，`weather-now` Skill 加载成功。
- HTTP API：
  - `/api/app/capabilities` 返回 `skillEnabled: true`、`ragEnabled: true`、`mcpEnabled: true`。
  - `/api/mcp/status` 返回本地和风天气 MCP `connected: true`，`toolCount: 1`，`allowedTools: ["weather_now"]`，只暴露 `keyConfigured: true`，不返回 Key 值。
  - `/api/skills` 返回 `weather-now` 已启用，依赖为 `mcp-qweather`。
- 聊天 SSE：`/chat/stream` 请求 `@weather-now 查询上海实时天气`，`enable_deepresearch=false`，走真实模型 + MCP 工具路径，返回上海实时天气并收到 `done` 事件。
- 持久化：`/api/sessions/phase5-weather-20260603/history` 确认线程 `phase5-weather-shanghai` 状态为 `COMPLETED`；线程事件接口可读取 `node.delta` 和 `graph.completed`；会话详情可读取 USER / ASSISTANT 两条消息。
- 浏览器页面：在 `1280x720` 视口验证 `/mcp`、`/skills`、`/chat/phase5-weather-20260603` 均能展示目标内容，无水平溢出或明显重叠；截图 API 曾超时，但 DOM 和可见文本检查已完成。
- 收尾：后端、本地 MCP、前端服务均已关闭，`18080`、`18090`、`5173` 均已释放。
- 密钥：未读取或输出 `.local/model-providers.json` 的模型 Key，未把和风天气 Key 写入源码、测试断言或提交记录。

## Acceptance Checklist

- [x] 本地 MCP Server 使用 TypeScript / Node.js 独立实现。
- [x] 本地 MCP Server 暴露 `/sse`，可被当前 Spring 后端连接。
- [x] 工具 `weather_now` 参数 schema 清晰。
- [x] `weather_now` 调用和风天气 `/geo/v2/city/lookup` 和 `/v7/weather/now`。
- [x] Key 只通过 `QWEATHER_API_KEY` 或本地安全配置提供，不进入源码和提交。
- [x] `/api/mcp/status` 能看到本地天气 MCP connected。
- [x] `/skills` 能看到 `weather-now` Skill。
- [x] `/chat @weather-now` 能返回真实天气。
- [x] 前端 `/mcp`、`/skills`、`/chat` 桌面视口无乱码、无重叠；移动端已由 Phase 4 覆盖。
- [x] PostgreSQL 复用既有健康 Docker 容器；后端、本地 MCP、前端服务验证后全部关闭。
- [x] `git status --short` 在提交前仅包含本计划执行记录，提交后干净。

## Recommended Execution Order

1. Phase 0：审计当前 MCP 客户端和状态页。
2. Phase 1：新增本地和风天气 MCP Server。
3. Phase 2：后端 MCP 配置切到本地天气服务。
4. Phase 3：新增 `weather-now` Skill，打通聊天路径。
5. Phase 4：优化 `/mcp` 页面展示本地天气服务。
6. Phase 5：全量验证和收尾。

每个阶段完成后按项目约定提交一次，commit 说明使用中文。
