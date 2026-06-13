# MCP 模块重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 简化 MCP 模块为两类（内置 + 远程），新增 MCP 只支持 ModelScope 社区 JSON 输入。

**Architecture:** 精简内置配置只保留和风天气，新增远程 MCP 通过解析 ModelScope 社区 JSON（格式 A），自动拆分 URL 为 url + sse-endpoint。

**Tech Stack:** Spring WebFlux, Jackson, Vue 3, Ant Design Vue, TypeScript

---

## File Structure

### Modified Files

| File | Changes |
|------|---------|
| `src/main/resources/mcp-config.json` | 精简为只保留和风天气 |
| `src/main/resources/mcp-config-docker.json` | 同步精简 |
| `src/main/java/.../mcp/McpServerConfigService.java` | 新增 `createFromModelScopeJson` 方法 |
| `src/main/java/.../mcp/McpStatusController.java` | 新增 `/servers/from-json` 端点 |
| `ui-vue3/src/views/mcp/index.vue` | 重构页面，简化新增表单 |
| `ui-vue3/src/views/mcp/mcpTools.ts` | 新增 JSON 解析函数 |
| `ui-vue3/src/services/api/app.ts` | 新增 API 接口 |

---

### Task 1: 精简内置配置

**Files:**
- Modify: `src/main/resources/mcp-config.json`
- Modify: `src/main/resources/mcp-config-docker.json`

- [ ] **Step 1: 更新 mcp-config.json**

```json
{
    "mcp-servers": [
        {
            "id": "mcp-qweather",
            "url": "http://127.0.0.1:18090",
            "sse-endpoint": "/sse",
            "description": "本地和风天气 MCP - 查询城市实时天气",
            "enabled": true,
            "allowed-tools": ["weather_now"]
        }
    ]
}
```

- [ ] **Step 2: 更新 mcp-config-docker.json**

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

- [ ] **Step 3: 提交**

```bash
git add src/main/resources/mcp-config.json src/main/resources/mcp-config-docker.json
git commit -m "refactor: MCP 内置配置精简为只保留和风天气"
```

---

### Task 2: 后端 — ModelScope JSON 解析

**Files:**
- Modify: `src/main/java/top/lanshan/manmu/mcp/McpServerConfigService.java`

- [ ] **Step 1: 新增 `createFromModelScopeJson` 方法**

在 `McpServerConfigService.java` 的 `create` 方法之后添加：

```java
/**
 * 从 ModelScope 社区 JSON 格式创建 MCP Server。
 * 输入格式：
 * {
 *   "mcpServers": {
 *     "Bazi-MCP": {
 *       "type": "streamable_http",
 *       "url": "https://mcp.api-inference.modelscope.net/b89553de02054a/mcp"
 *     }
 *   }
 * }
 */
public synchronized ManagedMcpServerInfo createFromModelScopeJson(String json,
        String description, String apiKey) throws IOException {
    if (json == null || json.isBlank()) {
        throw new IllegalArgumentException("JSON 配置不能为空");
    }

    Map<String, Object> root;
    try {
        root = objectMapper.readValue(json.strip(),
                new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
        throw new IllegalArgumentException("JSON 格式不正确: " + e.getMessage());
    }

    Object mcpServersObj = root.get("mcpServers");
    if (!(mcpServersObj instanceof Map<?, ?> mcpServers) || mcpServers.isEmpty()) {
        throw new IllegalArgumentException("JSON 中缺少 mcpServers 对象");
    }

    // 取第一个 entry
    Map.Entry<?, ?> first = mcpServers.entrySet().iterator().next();
    String key = first.getKey().toString();
    Object value = first.getValue();

    if (!(value instanceof Map<?, ?> serverConfig)) {
        throw new IllegalArgumentException("mcpServers." + key + " 不是有效对象");
    }

    String rawUrl = serverConfig.get("url") instanceof String s ? s.strip() : "";
    if (rawUrl.isBlank()) {
        throw new IllegalArgumentException("mcpServers." + key + ".url 不能为空");
    }

    String type = serverConfig.get("type") instanceof String s ? s.strip() : "sse";

    // 拆分 URL 为 base url + path
    URI uri;
    try {
        uri = URI.create(rawUrl);
    } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("URL 格式不正确: " + rawUrl);
    }

    String scheme = uri.getScheme();
    String host = uri.getHost();
    if (scheme == null || host == null) {
        throw new IllegalArgumentException("URL 缺少 scheme 或 host: " + rawUrl);
    }

    int port = uri.getPort();
    String baseUrl = scheme + "://" + host + (port > 0 ? ":" + port : "");
    String path = uri.getPath();
    if (path == null || path.isBlank()) {
        path = "/sse";
    }

    // 构建 McpServerInfo
    McpProperties.McpServerInfo info = new McpProperties.McpServerInfo();
    info.setId(sanitizeId(key));
    info.setUrl(baseUrl);
    info.setSseEndpoint(path);
    info.setType(type);
    info.setEnabled(true);
    if (description != null && !description.isBlank()) {
        info.setDescription(description.strip());
    } else {
        info.setDescription("ModelScope MCP - " + key);
    }
    if (apiKey != null && !apiKey.isBlank()) {
        info.setApiKey(apiKey.strip());
    }

    return create(info);
}

private String sanitizeId(String raw) {
    if (raw == null || raw.isBlank()) {
        return "mcp-remote-" + System.currentTimeMillis();
    }
    String slug = raw.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
    if (slug.isBlank()) {
        return "mcp-remote-" + System.currentTimeMillis();
    }
    return "mcp-" + slug;
}
```

- [ ] **Step 2: 验证编译**

Run: `$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'; mvn compile -q`

Expected: 编译成功

- [ ] **Step 3: 提交**

```bash
git add src/main/java/top/lanshan/manmu/mcp/McpServerConfigService.java
git commit -m "feat: McpServerConfigService 新增 createFromModelScopeJson 方法"
```

---

### Task 3: 后端 — 新增 API 端点

**Files:**
- Modify: `src/main/java/top/lanshan/manmu/mcp/McpStatusController.java`

- [ ] **Step 1: 新增 `/servers/from-json` 端点**

在 `McpStatusController.java` 的 `createServer` 方法之后添加：

```java
@PostMapping("/servers/from-json")
public Mono<ResponseEntity<Object>> createServerFromJson(@RequestBody Map<String, Object> body) {
    return Mono.fromCallable(() -> {
        try {
            String json = body.get("json") instanceof String s ? s : "";
            String description = body.get("description") instanceof String s ? s : null;
            String apiKey = body.get("apiKey") instanceof String s ? s : null;

            McpServerConfigService.ManagedMcpServerInfo server =
                    configService.createFromModelScopeJson(json, description, apiKey);
            toolProvider.clearCache();
            return ResponseEntity.status(201).body((Object) server);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body((Object) Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body((Object) Map.of("error", "Failed to write MCP config"));
        }
    }).subscribeOn(Schedulers.boundedElastic());
}
```

- [ ] **Step 2: 验证编译**

Run: `$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'; mvn compile -q`

Expected: 编译成功

- [ ] **Step 3: 提交**

```bash
git add src/main/java/top/lanshan/manmu/mcp/McpStatusController.java
git commit -m "feat: McpStatusController 新增 /servers/from-json 端点"
```

---

### Task 4: 前端 — API 接口

**Files:**
- Modify: `ui-vue3/src/services/api/app.ts`

- [ ] **Step 1: 新增 `createMcpServerFromJson` 方法**

在 `AppService` 类中添加：

```typescript
createMcpServerFromJson(json: string, description?: string, apiKey?: string): Promise<McpServerConfig> {
  return post<McpServerConfig>('/api/mcp/servers/from-json', { json, description, apiKey })
}
```

- [ ] **Step 2: 提交**

```bash
git add ui-vue3/src/services/api/app.ts
git commit -m "feat: 前端新增 createMcpServerFromJson API"
```

---

### Task 5: 前端 — MCP 工具函数

**Files:**
- Modify: `ui-vue3/src/views/mcp/mcpTools.ts`

- [ ] **Step 1: 新增 `parseModelScopeJson` 函数**

在 `mcpTools.ts` 末尾添加：

```typescript
export interface ModelScopeMcpParseResult {
  ok: boolean
  id?: string
  type?: string
  url?: string
  error?: string
}

export function parseModelScopeJson(text: string): ModelScopeMcpParseResult {
  try {
    const root = JSON.parse(text || '{}')
    if (!root || typeof root !== 'object' || Array.isArray(root)) {
      return { ok: false, error: '请输入 JSON 对象。' }
    }

    const mcpServers = root.mcpServers
    if (!mcpServers || typeof mcpServers !== 'object' || Array.isArray(mcpServers)) {
      return { ok: false, error: 'JSON 中缺少 mcpServers 对象。' }
    }

    const entries = Object.entries(mcpServers)
    if (entries.length === 0) {
      return { ok: false, error: 'mcpServers 为空。' }
    }

    const [key, value] = entries[0]
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return { ok: false, error: `mcpServers.${key} 不是有效对象。` }
    }

    const serverConfig = value as Record<string, unknown>
    const url = typeof serverConfig.url === 'string' ? serverConfig.url.trim() : ''
    if (!url) {
      return { ok: false, error: `mcpServers.${key}.url 不能为空。` }
    }

    if (!/^https?:\/\/.+/i.test(url)) {
      return { ok: false, error: `mcpServers.${key}.url 格式不正确。` }
    }

    const type = typeof serverConfig.type === 'string' ? serverConfig.type.trim() : 'sse'

    return { ok: true, id: key, type, url }
  } catch (err: any) {
    return { ok: false, error: err?.message || 'JSON 格式不正确。' }
  }
}
```

- [ ] **Step 2: 提交**

```bash
git add ui-vue3/src/views/mcp/mcpTools.ts
git commit -m "feat: 新增 parseModelScopeJson 工具函数"
```

---

### Task 6: 前端 — 重构 MCP 页面

**Files:**
- Modify: `ui-vue3/src/views/mcp/index.vue`

- [ ] **Step 1: 更新 script 部分**

在 import 中添加 `parseModelScopeJson`：

```typescript
import {
  invocationResultSummary,
  mcpServerAddress,
  mcpServerDisplay,
  mcpSourceColor,
  mcpSourceLabel,
  mcpToolExampleInput,
  normalizeToolNames,
  parseMcpJsonObject,
  parseModelScopeJson,  // 新增
  prettyMcpJson,
  testResultSummary,
  toolsText,
  validateMcpServerConfig,
} from './mcpTools'
```

- [ ] **Step 2: 替换 serverForm 和相关状态**

删除现有的 `serverForm`、`allowedToolsText`、`editingServerId`、`formError`，替换为：

```typescript
const jsonInput = ref('')
const descriptionInput = ref('')
const apiKeyInput = ref('')
const jsonParseError = ref('')
const parsedPreview = ref<{ id: string; type: string; url: string } | null>(null)
```

- [ ] **Step 3: 替换新增/编辑函数**

删除 `resetServerForm`、`openCreateServer`、`openEditServer`、`saveServer`，替换为：

```typescript
function openCreateServer() {
  jsonInput.value = ''
  descriptionInput.value = ''
  apiKeyInput.value = ''
  jsonParseError.value = ''
  parsedPreview.value = null
  serverModalVisible.value = true
}

function onJsonInputChange() {
  const result = parseModelScopeJson(jsonInput.value)
  if (result.ok) {
    parsedPreview.value = { id: result.id!, type: result.type!, url: result.url! }
    jsonParseError.value = ''
  } else {
    parsedPreview.value = null
    jsonParseError.value = result.error || ''
  }
}

async function saveServer() {
  const result = parseModelScopeJson(jsonInput.value)
  if (!result.ok) {
    jsonParseError.value = result.error || 'JSON 格式不正确'
    return
  }

  savingServer.value = true
  jsonParseError.value = ''
  try {
    await appService.createMcpServerFromJson(
      jsonInput.value,
      descriptionInput.value || undefined,
      apiKeyInput.value || undefined,
    )
    message.success('MCP Server 已新增')
    serverModalVisible.value = false
    await loadData()
  } catch (err: any) {
    jsonParseError.value = userMessageFromError(err, '新增 MCP Server 失败')
  } finally {
    savingServer.value = false
  }
}
```

- [ ] **Step 4: 更新模板 — 新增弹窗**

找到 `<a-modal>` 部分，替换为：

```vue
<a-modal
  v-model:open="serverModalVisible"
  title="新增远程 MCP Server"
  ok-text="连接"
  :confirm-loading="savingServer"
  @ok="saveServer"
>
  <a-alert v-if="jsonParseError" class="form-alert" show-icon type="warning" :message="jsonParseError" />
  <a-form layout="vertical">
    <a-form-item label="MCP 配置 JSON（从 ModelScope 社区复制）" required>
      <a-textarea
        v-model:value="jsonInput"
        :auto-size="{ minRows: 6, maxRows: 12 }"
        placeholder='{
  "mcpServers": {
    "Bazi-MCP": {
      "type": "streamable_http",
      "url": "https://mcp.api-inference.modelscope.net/b89553de02054a/mcp"
    }
  }
}'
        @change="onJsonInputChange"
      />
    </a-form-item>
    <a-form-item v-if="parsedPreview" label="解析预览">
      <div class="parsed-preview">
        <a-tag color="blue">ID: {{ parsedPreview.id }}</a-tag>
        <a-tag color="green">类型: {{ parsedPreview.type }}</a-tag>
        <span class="parsed-url">{{ parsedPreview.url }}</span>
      </div>
    </a-form-item>
    <a-form-item label="说明（可选）">
      <a-input v-model:value="descriptionInput" placeholder="默认：ModelScope MCP - {id}" />
    </a-form-item>
    <a-form-item label="API Key（可选）">
      <a-input-password v-model:value="apiKeyInput" placeholder="部分 MCP 服务需要" />
    </a-form-item>
  </a-form>
</a-modal>
```

- [ ] **Step 5: 更新模板 — 列表操作按钮**

在表格的操作列中，删除编辑按钮，保留启用/禁用、连接测试、删除按钮。内置 MCP 的删除按钮禁用：

找到操作列模板，替换为：

```vue
<template v-if="column.key === 'actions'">
  <a-space wrap>
    <a-tooltip :title="record.enabled ? '停用' : '启用'">
      <a-button
        size="small"
        :danger="record.enabled"
        :disabled="!record.id"
        :loading="actionLoading"
        @click="toggleServer(record)"
      >
        <PoweroffOutlined />
      </a-button>
    </a-tooltip>
    <a-tooltip title="连接测试">
      <a-button
        size="small"
        :loading="testingServerId === record.id"
        :disabled="!record.id"
        @click="testServer(record)"
      >
        <ToolOutlined />
      </a-button>
    </a-tooltip>
    <a-tooltip :title="record.source === 'BUILTIN' ? '内置服务器不可删除' : '删除'">
      <a-button
        size="small"
        danger
        :disabled="record.source === 'BUILTIN'"
        :loading="actionLoading"
        @click="confirmDeleteServer(record)"
      >
        <DeleteOutlined />
      </a-button>
    </a-tooltip>
  </a-space>
</template>
```

- [ ] **Step 6: 更新样式**

在 `<style>` 部分添加：

```less
.parsed-preview {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.parsed-url {
  color: #7a8798;
  font-size: 12px;
  word-break: break-all;
}
```

- [ ] **Step 7: 验证前端编译**

Run: `cd ui-vue3 && npx vue-tsc --noEmit -p tsconfig.app.json 2>&1 | Select-Object -Last 10`

Expected: 无新增类型错误

- [ ] **Step 8: 提交**

```bash
git add ui-vue3/src/views/mcp/index.vue
git commit -m "refactor: MCP 页面重构，新增支持 ModelScope JSON 输入"
```

---

### Task 7: 端到端验证

- [ ] **Step 1: 启动后端**

```bash
cd C:/MainData/code/Claude_project/M-agent
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
mvn spring-boot:run '-Dspring-boot.run.arguments=--server.port=18080' > target/backend.log 2>&1 &
echo $! > target/backend.pid
```

等待启动完成（检查日志出现 "Started DeepResearchMvpApplication"）。

- [ ] **Step 2: 测试内置配置**

```bash
curl.exe http://localhost:18080/api/mcp/status
```

Expected: 只显示 mcp-qweather（内置）

- [ ] **Step 3: 测试新增远程 MCP**

```bash
$body = @{
    json = '{"mcpServers":{"Bazi-MCP":{"type":"streamable_http","url":"https://mcp.api-inference.modelscope.net/b89553de02054a/mcp"}}}'
    description = "生辰八字 MCP"
} | ConvertTo-Json
$body | Out-File -Encoding UTF8 target/http-check/add-mcp.json
curl.exe -X POST -H "Content-Type: application/json" --data-binary "@target/http-check/add-mcp.json" http://localhost:18080/api/mcp/servers/from-json
```

Expected: 返回 201，包含服务器信息

- [ ] **Step 4: 测试连接**

```bash
curl.exe -X POST http://localhost:18080/api/mcp/servers/bazi-mcp/test
```

Expected: 返回连接结果

- [ ] **Step 5: 测试删除**

```bash
curl.exe -X DELETE http://localhost:18080/api/mcp/servers/bazi-mcp
```

Expected: 返回 204

- [ ] **Step 6: 停止服务**

```bash
$pid = Get-Content target/backend.pid
Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
```

- [ ] **Step 7: 最终提交**

```bash
git add -A
git commit -m "refactor: MCP 模块重构完成，精简为内置+远程两类"
```
