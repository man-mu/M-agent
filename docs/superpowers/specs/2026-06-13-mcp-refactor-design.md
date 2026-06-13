# MCP 模块重构设计文档

> 日期：2026-06-13

## 一、需求概述

重构 MCP 模块，简化为两类：
- **内置配置**：只保留和风天气 MCP（本地服务）
- **远程连接**：用户通过 ModelScope 社区 JSON 添加

新增 MCP 时，用户直接粘贴从社区获取的 JSON 字符串，后端自动解析并连接。

## 二、MCP 分类

| 类型 | 来源 | 可删除 | 可禁用 | 可编辑 |
|------|------|--------|--------|--------|
| **内置** | `mcp-config.json` | ❌ | ✅ | ❌ |
| **远程** | 用户 JSON 添加 | ✅ | ✅ | ❌ |

## 三、内置配置（精简）

`mcp-config.json` 只保留和风天气 MCP：

```json
{
  "mcp-servers": [
    {
      "id": "mcp-qweather",
      "url": "http://qweather-mcp:18090",
      "sse-endpoint": "/sse",
      "description": "本地和风天气 MCP - 查询城市实时天气",
      "enabled": true,
      "allowed-tools": ["weather_now"]
    }
  ]
}
```

同步更新 `mcp-config-docker.json`，删除高德地图和菜谱配置。

## 四、新增远程 MCP

### 4.1 输入格式

用户粘贴 ModelScope 社区 JSON（格式 A）：

```json
{
  "mcpServers": {
    "Bazi-MCP": {
      "type": "streamable_http",
      "url": "https://mcp.api-inference.modelscope.net/b89553de02054a/mcp"
    }
  }
}
```

### 4.2 后端解析逻辑

1. 解析 JSON 字符串
2. 读取 `mcpServers` 对象
3. 取第一个 key 作为 `id`（如 `Bazi-MCP`）
4. 拆分 URL：
   - `url`：协议 + 域名（如 `https://mcp.api-inference.modelscope.net`）
   - `sse-endpoint`：路径部分（如 `/b89553de02054a/mcp`）
5. 保留 `type` 字段（`sse` 或 `streamable_http`）
6. 可选字段：`description`（默认 "ModelScope MCP 服务"）、`api-key`

### 4.3 URL 拆分规则

```
输入: https://mcp.api-inference.modelscope.net/b89553de02054a/mcp
拆分:
  url: https://mcp.api-inference.modelscope.net
  sse-endpoint: /b89553de02054a/mcp
```

使用 `java.net.URI` 解析，取 scheme + host 作为 url，path 作为 sse-endpoint。

## 五、前端页面

### 5.1 页面结构

- **列表展示**：服务名、状态、工具数、操作按钮
- **操作**：启用/禁用、连接测试、删除
- **新增弹窗**：JSON 输入框 + 可选字段（说明、API Key）

### 5.2 新增表单

| 字段 | 必填 | 说明 |
|------|------|------|
| JSON 配置 | ✅ | 粘贴社区 JSON |
| 说明 | ❌ | 默认 "ModelScope MCP 服务" |
| API Key | ❌ | 部分 MCP 需要 |

### 5.3 列表展示

内置 MCP：
- 显示"内置"标签
- 只有启用/禁用按钮
- 无删除按钮

远程 MCP：
- 显示"远程"标签
- 有启用/禁用、连接测试、删除按钮

## 六、后端 API

复用现有 API，无需新增端点：

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/mcp/status` | GET | 获取 MCP 状态 |
| `/api/mcp/servers` | GET | 获取服务器列表 |
| `/api/mcp/servers` | POST | 新增服务器 |
| `/api/mcp/servers/{id}` | DELETE | 删除服务器 |
| `/api/mcp/servers/{id}/toggle` | PATCH | 启用/禁用 |
| `/api/mcp/servers/{id}/test` | POST | 连接测试 |
| `/api/mcp/reload` | POST | 重载配置 |

新增服务器时，请求体接收 JSON 字符串，后端解析后存储。

## 七、文件变更

| 操作 | 文件 | 说明 |
|------|------|------|
| 修改 | `src/main/resources/mcp-config.json` | 精简为只保留和风天气 |
| 修改 | `src/main/resources/mcp-config-docker.json` | 同步精简 |
| 修改 | `src/main/java/.../mcp/McpServerConfigService.java` | 新增 JSON 解析逻辑 |
| 修改 | `src/main/java/.../mcp/McpStatusController.java` | 修改新增接口，接收 JSON 字符串 |
| 修改 | `ui-vue3/src/views/mcp/index.vue` | 重构页面，简化新增表单 |
| 修改 | `ui-vue3/src/views/mcp/mcpTools.ts` | 新增 JSON 解析工具函数 |
| 修改 | `ui-vue3/src/services/api/app.ts` | 更新 API 接口类型 |
