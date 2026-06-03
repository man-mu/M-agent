# Skill 市场与 MCP 管理优化 Phase 0 基线审计

审计时间：2026-06-03 18:17 +08:00

## 工作区状态

- 执行前 `git status --short --branch` 显示当前分支为 `main...origin/main [ahead 13]`，工作区无未提交文件。
- 本阶段只新增本审计记录，不修改业务代码。
- 未读取、打印或提交 `.local/model-providers.json`、`.local/mcp-keys.json` 或任何 API Key。
- 未修改 `.local/`、`.claude/`、`target/`、`.idea/`。
- `.gitignore` 当前忽略 `docs/`，因此本审计记录需要在提交时显式 `git add -f`。

## Skill 后端基线

- `SkillAutoConfiguration` 在未配置 `mvp.skill.content-path` 时，默认把 Skill 内容目录设为 `src/main/java/top/lanshan/manmu/skill/content`。当前内置 Skill 仍位于源码目录。
- `SkillDefinition` 目前包含 `name`、`description`、`version`、`enabled`、`parameters`、`dependencies`、`created_at`，并通过 `@JsonIgnoreProperties(ignoreUnknown = true)` 兼容未知字段。
- `SkillFileRepository` 只有单一 `contentBasePath`，读写删除均通过 `contentBasePath.resolve(name)` 拼接目录；当前后端没有统一校验 Skill 名称，也没有 `normalize()` 后的目录边界检查。
- `SkillService#create/update/delete/toggle` 会直接写入或删除 Skill 文件。`update` 允许改名：当 path variable 与 request definition name 不一致时，先删除旧目录再写新目录。
- `SkillRegistry` 支持 `loadAll()`、`reload()`、`register()`、`unregister()`，但没有面向 HTTP 的单 Skill reload 接口。
- `SkillToolProvider#getToolCallbacks()` 每次调用都会从当前 registry 的 enabled skills 重新生成 `SkillToolCallback[]`，因此 create/update/delete/toggle 后注册表变更能影响后续工具回调生成。
- 当前没有包级生命周期、安装来源、内置只读/本地可写区分、导入/导出、卸载本地包、Jar Skill、类加载器隔离。

## Skill 前端基线

- `/skills` 当前是 Prompt Skill CRUD 管理页：
  - 支持能力开关状态、列表、搜索、启用状态筛选、新建、编辑、启停、删除。
  - 桌面端使用表格；移动端使用卡片列表。
  - 表单支持依赖编辑、参数 JSON Schema 编辑、Prompt 模板、启用开关和 Prompt 预览。
- `skillForm.ts` 在前端限制 Skill 名称为 `^[A-Za-z0-9_-]+$`，并校验参数 Schema 必须是 JSON object；该校验尚未在后端同步强制。
- `ui-vue3/src/services/api/skills.ts` 当前只覆盖 `list/get/create/update/delete/toggle`，没有 import/export/reload/uninstall 包级 API。
- 页面尚未区分内置 Skill 与用户安装 Skill，也没有本地市场、安装位置、来源、tags/category/author/homepage 等市场字段展示。

## MCP 后端基线

- `McpProperties` 当前支持 `enabled`、`config-location` 和静态 `mcp-servers` 配置，默认配置位置是 `classpath:mcp-config.json`。
- `src/main/resources/mcp-config.json` 当前包含：
  - 禁用的高德地图 MCP，SSE endpoint 使用 `${AMAP_MAPS_API_KEY}` 占位符。
  - 启用的本地和风天气 MCP，地址为 `http://127.0.0.1:18090`，允许工具 `weather_now`。
- `McpConfigMergeUtil` 支持运行时配置与静态配置按 URL 合并，支持 `${ENV_NAME}` 与 `${ENV_NAME:fallback}` 占位符，并会从环境变量或 `.local/mcp-keys.json` 解析本地 Key。审计过程没有读取该本地 Key 文件内容。
- `McpToolProvider#getToolCallbacks()` 是懒加载并缓存结果：第一次调用会初始化 MCP Client、发现工具并设置 `initialized=true`；后续调用直接返回 `cachedCallbacks`。
- `/api/mcp/status` 当前只有状态查询接口，调用 `toolProvider.getStatus()` 会触发工具初始化；没有 reload、配置新增/编辑/删除、连接测试、工具直接调试接口。
- 当前 MCP 状态响应会返回 URL、SSE endpoint、描述、configuredEnabled、connected、error、allowedTools、keyEnvName、keyConfigured、requiredEnvVars，不返回真实 Key。

## MCP 前端与本地服务基线

- `/mcp` 当前是状态看板：
  - 展示模块状态、服务数量、已连接数、工具数量。
  - 展示每个 MCP 服务的地址、连接状态、允许工具、Key 指引和错误信息。
  - 对 `weather_now`、高德地图部分工具有已知工具名称和描述映射。
- `/mcp` 目前不支持新增、编辑、删除、启停 MCP Server，不支持连接测试，不支持 reload，也不支持直接调试工具调用。
- `tools/local-qweather-mcp` 是独立 TypeScript / Node.js MCP Server：
  - 提供 `/health`、`/sse`、`/messages` 和 `/debug/weather-now`。
  - `weather_now` 工具调用真实和风天气 API。
  - 配置优先读取环境变量，其次读取项目 `.local/mcp-keys.json`，但响应不返回 Key。

## 已确认差距与后续边界

- Phase 1 应优先把后端 Skill 名称校验和路径边界检查下沉到服务/仓储层，不能只依赖前端校验。
- Phase 1 应把内置源码 Skill 与本地用户安装 Skill 分成不同存储位置，并明确内置只读、本地可写。
- Phase 1/2 应补齐包级生命周期与来源字段，避免继续把用户上传内容长期写入源码目录。
- Phase 2 的 zip 导入必须防 Zip Slip、限制文件白名单和大小，并禁止 `.jar`、`.class`、脚本文件混入 Prompt Skill 包。
- Phase 4 应为 MCP 增加本地配置文件和管理 API，配置变更后需要能清空或重建 `McpToolProvider` 缓存。
- Phase 4/5 的 MCP 错误消息需要继续保持不泄露 Key；工具调试输出也需要长度限制和错误清洗。
- 当前测试覆盖了 repository、registry、controller、tool provider 和部分 MCP 工具提供者行为，但没有独立 `SkillServiceTest`，也缺少后端非法 Skill 名称/路径穿越测试。

## 自动化验证

命令：

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=top.lanshan.manmu.skill.service.*Test,top.lanshan.manmu.mcp.*Test' test
cd ui-vue3
npm run test:unit -- skillForm.spec.ts mcpTools.spec.ts
```

结果：

- 后端聚焦测试通过：39 个测试通过，失败 0，错误 0，跳过 0。
- 前端聚焦测试通过：`skillForm.spec.ts`、`mcpTools.spec.ts` 共 10 个测试通过。

## 真实 HTTP 与浏览器验证

环境：

- Docker Desktop 中 PostgreSQL 容器 `manmu-postgres` 已启动并处于 healthy 状态，`5432` 正常映射到 localhost。
- 启动前 `18080`、`18090`、`5173` 均未被占用。
- 本地和风天气 MCP 启动到 `18090`，后端使用 JDK 17 启动到 `18080`，前端 Vite 启动到 `5173`。
- 后端日志确认连接 PostgreSQL 17.9、Flyway 8 个 migration 已校验、Skill 默认内容路径为源码目录，并加载 `code-review`、`location-analyzer`、`weather-now` 共 3 个 Skill。

HTTP 验证：

```powershell
curl.exe http://localhost:18080/api/app/capabilities
curl.exe http://localhost:18080/api/skills
curl.exe http://localhost:18080/api/mcp/status
```

结果：

- `/api/app/capabilities` 返回 `skillEnabled=true`、`ragEnabled=true`、`mcpEnabled=true`。
- `/api/skills` 返回 3 个启用 Skill：`code-review`、`location-analyzer`、`weather-now`。
- `/api/mcp/status` 返回 MCP 已启用，本地和风天气 MCP `connected=true`、`toolCount=1`、`allowedTools=["weather_now"]`；禁用的高德地图 MCP 保留在状态中，且没有返回真实 Key。
- 本地 MCP `/debug/weather-now?location=上海&lang=zh&unit=m` 返回真实和风天气数据：上海当前小雨、温度 26°、湿度 86%，观测时间为 `2026-06-03T18:22+08:00`。

浏览器验证：

- 使用前端 `http://127.0.0.1:5173` 打开 `/skills`、`/mcp`、`/chat`。
- `/skills` DOM 中出现 `Skill 管理`、`code-review`、`weather-now`、`新建 Skill`。
- `/mcp` DOM 中出现 `MCP 工具`、`本地和风天气 MCP`、`weather_now`、`工具数量`。
- `/chat` DOM 中出现 `M-Agent`、`对话`、`发送`。
- 在 `1280x720` 和 `390x844` 视口下检查 `/skills`、`/mcp`、`/chat`，页面级 `scrollWidth` 未超过 `clientWidth`，未检测到明显横向溢出。

真实聊天与持久化验证：

```powershell
curl.exe -N -H 'Content-Type: application/json' --data-binary "@target/http-check/phase0-weather-chat-request.json" http://localhost:18080/chat/stream
curl.exe http://localhost:18080/api/reports/{threadId}/exists
curl.exe http://localhost:18080/api/sessions/{sessionId}/threads/{threadId}
curl.exe http://localhost:18080/api/sessions/{sessionId}/threads/{threadId}/events
```

结果：

- `@weather-now 查询上海实时天气` 通过真实 `/chat/stream` 返回 SSE `done`。
- 报告接口返回存在，session history 中线程状态为 `COMPLETED`，event history 可读取。
- 该验证暴露出一个基线差距：虽然 `/api/mcp/status` 显示本地和风天气 MCP 已连接且本地 MCP debug 接口可返回真实天气，但 `@weather-now` 聊天输出仍提示 MCP 服务不可用。日志显示显式 `@weather-now` 确实进入了 Skill 渲染路径，后续阶段需要核查 Spring AI MCP ToolCallback 的实际工具名与 `SpringAiAgentClient#findToolCallback("weather_now")` 的匹配边界，避免状态页 connected 但显式 Skill 无法读取 MCP 真实返回。

关闭验证：

- 已停止本阶段启动的后端 Maven/Java 进程、本地和风天气 MCP Node 进程、前端 Vite Node 进程。
- `Get-NetTCPConnection -LocalPort 18080,18090,5173 -ErrorAction SilentlyContinue` 无返回，三个端口均已释放。
- PostgreSQL Docker 容器是验证前已经存在并运行的基础依赖，本阶段未停止该容器。

## Phase 0 结论

- 当前 Skill 默认内容目录仍是源码目录，后端写入/删除路径没有统一名称校验和目录边界保护；这是 Phase 1 的首要安全边界。
- 当前 Skill 管理是 Prompt Skill CRUD，而不是包级市场；缺少 catalog、本地安装目录、来源/安装位置、导入/导出、卸载、reload 和内置只读约束。
- 当前 MCP 后端能从静态配置连接本地和风天气 MCP，状态接口能展示 allowed tools 和 Key 配置状态，但缺少配置管理、连接测试、reload 和工具调试接口。
- 当前 `/skills`、`/mcp`、`/chat` 页面在桌面和移动基线视口可打开且无页面级横向溢出，`/mcp` 能展示本地和风天气 MCP。
- 本地 MCP 服务和和风真实天气链路可用；聊天 SSE 和 PostgreSQL 持久化链路可用。
- 真实聊天暴露 `@weather-now` 显式 Skill 与 MCP 工具回调匹配不一致的问题，后续 Phase 需要在不引入 mock 的前提下修复。
- 本阶段未读取或输出任何 Key，未修改 `.local/`、`.claude/`、`target/`、`.idea/`。
