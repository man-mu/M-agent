# 本地和风天气 MCP 服务

这是一个独立的 TypeScript / Node.js MCP Server，提供 `weather_now` 工具，用于查询城市、Location ID 或经纬度的实时天气。

## 配置

优先读取环境变量，其次读取项目根目录 `.local/mcp-keys.json`：

```json
{
  "QWEATHER_API_KEY": "你的和风天气 Key",
  "QWEATHER_API_HOST": "可选，例如 https://api.qweather.com",
  "LOCAL_QWEATHER_MCP_PORT": "可选，默认 18090"
}
```

`QWEATHER_API_KEY` 不应提交到源码或测试中。未配置 Key 时服务仍可启动，但工具调用会返回清晰错误。

## 本地运行

```powershell
cd tools/local-qweather-mcp
npm install
npm run build
npm start
```

服务端点：

- `GET /health`：健康检查，不返回 Key。
- `GET /sse`：MCP SSE 入口。
- `POST /messages?sessionId=...`：SSE transport 消息入口。
- `GET /debug/weather-now?location=北京`：本地调试真实天气调用，不返回 Key。

## 后端联调验收

仓库内置 MCP Server ID 为 `mcp-qweather`，后端默认连接 `http://127.0.0.1:18090/sse` 并只允许 `weather_now` 工具。启动本服务和后端后，可在仓库根目录执行：

```powershell
New-Item -ItemType Directory -Force target/http-check | Out-Null
curl.exe http://127.0.0.1:18090/health > target/http-check/local-qweather-health.json
curl.exe http://localhost:18080/api/mcp/status > target/http-check/mcp-status.json
curl.exe -X POST http://localhost:18080/api/mcp/servers/mcp-qweather/test > target/http-check/mcp-qweather-test.json
curl.exe -X POST http://localhost:18080/api/mcp/reload > target/http-check/mcp-reload.json
'{"location":"上海","lang":"zh","unit":"m"}' | Set-Content -Encoding UTF8 target/http-check/weather-now-request.json
curl.exe -X POST -H "Content-Type: application/json" --data-binary "@target/http-check/weather-now-request.json" http://localhost:18080/api/mcp/tools/weather_now/invoke > target/http-check/weather-now-result.json
```

期望结果：

- `/health` 返回 `ok=true`，只显示 `keyConfigured`，不返回 Key 明文。
- `/api/mcp/servers/mcp-qweather/test` 连接成功时返回 `weather_now`。
- `weather-now-result.json` 返回真实天气；如果 Key 缺失、Key 无效、供应商限流或网络失败，应返回清晰错误，不使用示例天气或 mock 数据。

## 测试

```powershell
npm run test
npm run build
```
