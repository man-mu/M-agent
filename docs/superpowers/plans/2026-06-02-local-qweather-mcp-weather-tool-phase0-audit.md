# 本地和风天气 MCP Phase 0 基线审计

审计时间：2026-06-02 22:59 +08:00

## 工作区状态

- 执行前 `git status --short` 为空。
- 本阶段未修改业务代码，只新增本审计记录。
- 未读取、打印或提交 `.local/model-providers.json`。
- 未修改、删除或整理 `.claude/`、`.local/`、`target/`、`.idea/`。
- Maven 验证会正常生成或更新 `target/` 构建输出，本阶段未手动整理该目录。

## 静态审计结论

- `src/main/resources/mcp-config.json` 当前只有一个启用的高德 MCP 服务：
  - `url`: `https://mcp.amap.com`
  - `sse-endpoint`: `/sse?key=${AMAP_MAPS_API_KEY}`
  - `allowed-tools`: `maps_weather`、`maps_geo`、`maps_regeo`、`maps_text_search`、`maps_around_search`、`maps_ip_location`、`maps_direction_driving`、`maps_direction_walking`、`maps_direction_bicycling`、`maps_distance`
- `src/main/java/top/lanshan/manmu/config/McpProperties.java` 将 `sse-endpoint` 默认值设为 `/sse`，并支持 `description`、`enabled`、`allowed-tools`。
- `src/main/java/top/lanshan/manmu/mcp/McpConfigMergeUtil.java` 当前只创建 `WebFluxSseClientTransport`，创建时使用配置中的 `url` 作为 WebClient base URL，并把 `sse-endpoint` 传给 transport；因此后续本地 MCP Server 需要兼容 HTTP + SSE，至少提供 `/sse` 入口。
- `McpConfigMergeUtil.resolvePlaceholders(...)` 支持 `${ENV_NAME}` 和 `${ENV_NAME:fallback}` 占位符。缺失环境变量会解析为空字符串。
- `src/main/java/top/lanshan/manmu/mcp/McpToolProvider.java` 会汇总所有启用服务的 `allowed-tools`，并用该集合过滤最终暴露给模型的 MCP 工具名；如果集合为空，才暴露客户端返回的全部工具。
- `/api/mcp/status` 由 `McpStatusController` 返回 `McpToolProvider.McpStatus`，当前结构包含：
  - 顶层：`enabled`、`servers`、`toolCount`
  - server：`url`、`sseEndpoint`、`description`、`configuredEnabled`、`connected`、`error`
- `ui-vue3/src/services/api/app.ts` 的前端类型额外预留了 `allowedTools`、`keyEnvName`、`keyConfigured`、`requiredEnvVars` 字段，但后端当前不返回这些字段。
- `ui-vue3/src/views/mcp/index.vue` 当前是通用 MCP 状态页，按 `description` 展示服务名称和说明，能展示当前高德 MCP 的身份；但尚无本地和风天气 MCP 的专属工具列表、Key 指引或缺 Key 文案。

## 自动化测试

命令：

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=top.lanshan.manmu.mcp.*Test' test
```

结果：

- 构建成功。
- `McpConfigMergeUtilTest`、`McpStatusControllerTest`、`McpToolProviderTest` 共 12 个测试通过。
- 失败数 0，错误数 0，跳过数 0。

## 真实 HTTP 验证

环境：

- Docker Desktop 中 PostgreSQL 容器 `manmu-postgres` 已启动并处于 healthy 状态。
- `5432` 正常监听。
- `18080` 启动前未被占用。
- 后端使用 JDK 17 启动到 `18080`。
- 本次进程内显式将 `AMAP_MAPS_API_KEY` 置为空，符合“不设置任何高德 Key”的 Phase 0 验证场景。
- 后端日志和 PID 写入系统临时目录 `%TEMP%`，没有写入 `.local/` 或计划禁止改动的目录。

验证命令：

```powershell
curl.exe http://localhost:18080/api/app/capabilities
curl.exe http://localhost:18080/api/mcp/status
```

结果：

- `GET /api/app/capabilities` 返回：

```json
{"skillEnabled":true,"ragEnabled":true,"mcpEnabled":true}
```

- `GET /api/mcp/status` 返回 MCP 已启用、高德服务未连接、工具数量为 0：

```json
{
  "enabled": true,
  "servers": [
    {
      "url": "https://mcp.amap.com",
      "sseEndpoint": "/sse?key=${AMAP_MAPS_API_KEY}",
      "description": "高德地图 MCP - 天气、地址解析、POI 搜索、路线规划和距离测量",
      "configuredEnabled": true,
      "connected": false,
      "error": "block()/blockFirst()/blockLast() are blocking, which is not supported in thread reactor-http-nio-3"
    }
  ],
  "toolCount": 0
}
```

关闭验证：

- 已停止 Maven 启动进程和实际监听 `18080` 的 Spring Boot 进程。
- `Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue` 无返回，端口已释放。

## Phase 0 结论

- 当前后端确实需要本地 MCP Server 兼容 `WebFluxSseClientTransport`，本地天气 MCP 第一版应提供 `/sse`。
- 当前配置中的 `url` 是 base URL，`sse-endpoint` 是相对或带查询参数的 SSE 入口；`McpProperties` 默认 `sse-endpoint` 为 `/sse`。
- `allowed-tools` 会限制实际暴露给模型的工具名。后续本地和风天气 MCP 配置应只允许 `weather_now`。
- 当前 `/api/mcp/status` 已返回服务 URL、SSE endpoint、description、connected、error、toolCount 等 Phase 0 要求字段。
- 当前 `/mcp` 页面能通过后端返回的 `description` 区分“高德地图 MCP”这类服务身份，但不能主动区分或解释“本地和风天气 MCP”；后续 Phase 4 需要补充天气工具专属展示、Key 指引和工具列表。
- 真实 HTTP 验证暴露出一个基线问题：首次请求 `/api/mcp/status` 会在 Reactor HTTP 线程中触发 MCP 初始化，而 `McpToolProvider` 内部调用 `block(Duration.ofMinutes(2))`，因此当前错误文案是 WebFlux 阻塞限制，而不是清晰的缺少高德 Key 或连接失败提示。后续如果保持状态接口同步初始化，需要避免在事件循环线程上直接阻塞，或提前初始化/转移到 bounded elastic 线程。
