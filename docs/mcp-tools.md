# MCP 工具配置

M-Agent 通过 Spring AI MCP Client 连接外部 MCP Server，并把 MCP 工具作为 Agent 可调用工具接入模型上下文。当前仓库包含两个配置入口：

- 内置配置：`src/main/resources/mcp-config.json`
- 本地覆盖配置：`.local/mcp-servers.json`

`.local/` 已被 `.gitignore` 忽略，适合保存本地私有配置和 Key。

## 当前内置 MCP Server

### 本地和风天气 MCP

默认启用：

- ID：`mcp-qweather`
- URL：`http://127.0.0.1:18090`
- SSE endpoint：`/sse`
- 工具：`weather_now`
- 代码目录：`tools/local-qweather-mcp`

用途：

- 查询城市、和风 Location ID 或 `lon,lat` 经纬度的实时天气。
- 支撑内置 `weather-now` Skill 的真实天气调用。

启动：

```powershell
cd tools/local-qweather-mcp
npm install
npm run build
npm start
```

Key 配置方式一：环境变量。

```powershell
$env:QWEATHER_API_KEY="你的和风天气 Key"
$env:QWEATHER_API_HOST="https://api.qweather.com"
```

Key 配置方式二：仓库根目录 `.local/mcp-keys.json`。

```json
{
  "QWEATHER_API_KEY": "你的和风天气 Key",
  "QWEATHER_API_HOST": "https://api.qweather.com",
  "LOCAL_QWEATHER_MCP_PORT": "18090"
}
```

不要提交 `.local/mcp-keys.json`，也不要在日志、文档或测试断言中输出 Key。

本地调试：

```powershell
curl.exe http://127.0.0.1:18090/health
curl.exe "http://127.0.0.1:18090/debug/weather-now?location=上海"
```

### 高德地图 MCP

默认关闭：

- ID：`mcp-amap`
- URL：`https://mcp.amap.com`
- SSE endpoint：`/sse?key=${AMAP_MAPS_API_KEY}`
- 工具：`maps_weather`、`maps_geo`、`maps_regeo`、`maps_text_search`、`maps_around_search`、`maps_ip_location`、`maps_direction_driving`、`maps_direction_walking`、`maps_direction_bicycling`、`maps_distance`

启用前需要高德 Web 服务 Key：

```powershell
$env:AMAP_MAPS_API_KEY="你的高德 Web 服务 Key"
```

如需本地启用，可在 `.local/mcp-servers.json` 中覆盖内置配置，或通过 `/api/mcp/servers` 管理接口创建本地配置。Key 或 token 字段必须使用 `${ENV_NAME}` 占位，不要直接写明文。

## 后端管理接口

查看 MCP 状态：

```powershell
curl.exe http://localhost:18080/api/mcp/status
```

查看 MCP Server 配置：

```powershell
curl.exe http://localhost:18080/api/mcp/servers
```

重载 MCP 工具：

```powershell
curl.exe -X POST http://localhost:18080/api/mcp/reload
```

测试某个 Server：

```powershell
curl.exe -X POST http://localhost:18080/api/mcp/servers/mcp-qweather/test
```

调用工具：

```powershell
New-Item -ItemType Directory -Force target/http-check | Out-Null
'{"location":"上海"}' | Set-Content -Encoding UTF8 target/http-check/weather-now-request.json
curl.exe -X POST -H "Content-Type: application/json" --data-binary "@target/http-check/weather-now-request.json" http://localhost:18080/api/mcp/tools/weather_now/invoke
```

完整验收顺序：

```powershell
New-Item -ItemType Directory -Force target/http-check | Out-Null
curl.exe http://localhost:18080/api/mcp/status > target/http-check/mcp-status.json
curl.exe http://localhost:18080/api/mcp/servers > target/http-check/mcp-servers.json
curl.exe -X POST http://localhost:18080/api/mcp/servers/mcp-qweather/test > target/http-check/mcp-qweather-test.json
curl.exe -X POST http://localhost:18080/api/mcp/reload > target/http-check/mcp-reload.json
'{"location":"上海","lang":"zh","unit":"m"}' | Set-Content -Encoding UTF8 target/http-check/weather-now-request.json
curl.exe -X POST -H "Content-Type: application/json" --data-binary "@target/http-check/weather-now-request.json" http://localhost:18080/api/mcp/tools/weather_now/invoke > target/http-check/weather-now-result.json
```

验收结果应满足：

- `mcp-status.json` 中 `mcp-qweather` 存在，`allowedTools` 包含 `weather_now`。
- `mcp-qweather-test.json` 在本地 MCP 已启动时返回 `connected=true`，并发现 `weather_now`。
- `weather-now-result.json` 返回真实天气结果；如果 Key 缺失、Key 无效、供应商限流或网络失败，应返回清晰错误，不应出现示例天气或 mock 数据。

创建本地 MCP Server：

```powershell
'{
  "id": "local-example",
  "url": "http://127.0.0.1:18090",
  "sseEndpoint": "/sse",
  "description": "本地和风天气 MCP",
  "enabled": true,
  "allowedTools": ["weather_now"]
}' | Set-Content -Encoding UTF8 target/http-check/mcp-server.json

curl.exe -X POST -H "Content-Type: application/json" --data-binary "@target/http-check/mcp-server.json" http://localhost:18080/api/mcp/servers
```

## 前端控制台

打开：

```text
http://localhost:5173/mcp
```

页面能力：

- 查看整体 MCP 状态。
- 查看内置和本地 MCP Server。
- 新增、编辑、删除本地 MCP Server。
- 启用或停用 MCP Server。
- 测试连接。
- reload 工具列表。
- 调试工具调用。
- 对本地和风天气显示 Key 配置状态、`weather_now` 工具和失败排查提示。

## 与 Skill 的关系

Skill 可以在 `dependencies` 中声明 MCP 依赖，例如：

```json
{
  "name": "weather-now",
  "dependencies": ["mcp-qweather"]
}
```

`SkillHealthService` 会根据 MCP 配置和连接状态报告依赖健康情况。天气类 Skill 应优先使用真实 MCP 工具返回，不要编造天气。

## 排查清单

- Docker Desktop 是否启动。
- 后端是否在 `18080` 端口运行。
- 本地和风天气 MCP 是否在 `18090` 端口运行。
- `.local/mcp-keys.json` 或环境变量是否配置了 `QWEATHER_API_KEY`。
- `/api/mcp/status` 是否显示目标 Server connected。
- `allowedTools` 是否包含需要调用的工具名。
- 如果供应商限流或 Key 无效，应该展示清晰错误，不应使用 mock 数据。
