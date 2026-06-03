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
npm run dev
```

服务端点：

- `GET /health`：健康检查，不返回 Key。
- `GET /sse`：MCP SSE 入口。
- `POST /messages?sessionId=...`：SSE transport 消息入口。
- `GET /debug/weather-now?location=北京`：本地调试真实天气调用，不返回 Key。

## 测试

```powershell
npm run test
npm run build
```
