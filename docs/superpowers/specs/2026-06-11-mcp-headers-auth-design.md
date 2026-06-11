# MCP 模块通用性增强：Headers 认证支持

## 目标

让 MCP 模块能够接入外部 MCP 服务市场（如 ModelScope MCP 市场、企业自建 MCP 服务），而不仅限于本地 MCP 服务。核心改动：新增 HTTP Headers 自定义能力和 `apiKey` 快捷认证字段。

## 背景

### 当前状态

- MCP 模块基于 Spring AI MCP Client WebFlux，仅支持 SSE 传输协议
- 服务配置模型 `McpServerInfo` 包含字段：`id`、`url`、`sseEndpoint`、`description`、`enabled`、`allowedTools`
- 凭据注入仅支持 URL Query Param 占位符：`?key=${ENV_VAR}`
- 传输层创建时 `WebClient.Builder` 未注入任何自定义 Header

### 行业标准

主流 MCP 客户端（Claude Desktop、Cursor、Cherry Studio、Continue.dev）已收敛到通用 JSON Schema：

```json
{
  "mcpServers": {
    "my-server": {
      "type": "sse",
      "url": "https://api.example.com/sse",
      "headers": {
        "Authorization": "Bearer <token>",
        "X-API-Key": "<key>"
      },
      "apiKey": "<shortcut-for-bearer-token>"
    }
  }
}
```

### 差距

- 缺少 `headers` 字段，无法注入 HTTP Header 级认证
- 缺少 `apiKey` 快捷字段，无法简化 Bearer Token 场景

## 设计

### 数据模型变更

**`McpProperties.McpServerInfo`** 新增两个字段：

```java
// 自定义 HTTP Headers，值中的 ${ENV_VAR} 占位符自动解析
// 从系统环境变量或 .local/mcp-keys.json 取值
@JsonProperty("headers")
private Map<String, String> headers = new LinkedHashMap<>();

// API Key 快捷字段，自动转为 Authorization: Bearer <value>
// 与 headers.Authorization 同时存在时，headers 优先
@JsonProperty("api-key")
@JsonAlias("apiKey")
private String apiKey;
```

### 传输层变更

**`McpConfigMergeUtil.createNamedTransports()`**：

1. 遍历 `serverInfo.headers`，解析占位符后通过 `WebClient.Builder.defaultHeader()` 注入
2. 如果 `apiKey` 不为空，注入 `Authorization: Bearer <resolvedApiKey>`
3. 如果 `headers` 中已有 `Authorization`，跳过 `apiKey` 自动注入（显式优先）
4. 日志中打印 Header 名称列表（不打印值），避免泄露凭据

伪代码：

```java
// resolve and inject custom headers
Map<String, String> resolved = new LinkedHashMap<>();
serverInfo.getHeaders().forEach((name, value) -> {
    resolved.put(name, resolvePlaceholders(value));
});

boolean hasAuthHeader = resolved.keySet().stream()
    .anyMatch(k -> k.equalsIgnoreCase("Authorization"));

if (!hasAuthHeader && serverInfo.getApiKey() != null
        && !serverInfo.getApiKey().isBlank()) {
    resolved.put("Authorization",
        "Bearer " + resolvePlaceholders(serverInfo.getApiKey()));
}

resolved.forEach(clone::defaultHeader);
```

### 安全校验变更

**`McpServerConfigService.sanitizeForWrite()`**：

- `requireNoInlineSecret()` 扩展校验范围：url、sseEndpoint、headers 所有值、apiKey
- Header 值中包含 `key`/`token`/`api_key` 等敏感参数名时，值必须是 `${}` 占位符形式，拒绝明文

### 状态展示变更

**`McpToolProvider.ServerStatus`**：

- 新增 `hasHeaders` 布尔字段，表示该服务是否配置了自定义 Header
- 新增 `hasApiKey` 布尔字段，表示是否使用了 apiKey 快捷认证
- Header 名称列表写入状态（值不暴露），供前端 `/mcp` 页面展示连接方式

### 向下兼容

- `headers` 默认为空 Map，`apiKey` 默认为 null
- 现有 `mcp-config.json` 中的服务无需修改，行为不变
- API 接口 `POST/PUT /api/mcp/servers` 的请求体向后兼容，新字段可选

## 配置示例

```json
{
  "mcp-servers": [
    {
      "id": "modelscope-fetch",
      "url": "https://mcp.modelscope.cn",
      "sse-endpoint": "/sse",
      "description": "ModelScope Fetch MCP — Header 认证",
      "enabled": true,
      "headers": {
        "Authorization": "Bearer ${MODELSCOPE_API_TOKEN}"
      }
    },
    {
      "id": "amap-header-auth",
      "url": "https://mcp.amap.com",
      "sse-endpoint": "/sse",
      "description": "高德地图 — apiKey 快捷认证",
      "enabled": true,
      "api-key": "${AMAP_MAPS_API_KEY}"
    },
    {
      "id": "enterprise-mcp",
      "url": "https://mcp.company.com",
      "sse-endpoint": "/sse",
      "description": "企业 MCP — 多 Header 认证",
      "enabled": true,
      "headers": {
        "X-API-Key": "${ENTERPRISE_API_KEY}",
        "X-Tenant-ID": "tenant-123"
      }
    }
  ]
}
```

## 涉及文件

| 文件 | 改动 |
|------|------|
| `McpProperties.java` | `McpServerInfo` 新增 `headers`、`apiKey` 字段 |
| `McpConfigMergeUtil.java` | `createNamedTransports()` 解析并注入 Header；新增 `resolveHeaders()` 方法 |
| `McpServerConfigService.java` | `sanitizeForWrite()` 扩展安全校验到 headers 和 apiKey |
| `McpToolProvider.java` | `ServerStatus` 新增 `hasHeaders`、`hasApiKey` 字段 |
| `McpStatusController.java` | 无需改动（status 自动包含新字段） |
| `mcp-config.json` | 可选：更新高德 MCP 示例为 apiKey 模式 |

## 测试要点

1. **Headers 占位符解析**：`${ENV_VAR}` 从环境变量和 `.local/mcp-keys.json` 正确取值
2. **apiKey 自动注入**：`apiKey: "${TOKEN}"` 自动生成 `Authorization: Bearer <token>`
3. **显式优先**：同时有 `apiKey` 和 `headers.Authorization` 时，headers 生效
4. **安全拦截**：headers 值中使用明文 key/token 时，`sanitizeForWrite()` 抛异常
5. **向下兼容**：不传 headers/apiKey 的服务行为不变
6. **API 兼容**：`POST/PUT /api/mcp/servers` 接收含新字段的请求体
7. **日志不泄露**：Header 值不出现在日志中
