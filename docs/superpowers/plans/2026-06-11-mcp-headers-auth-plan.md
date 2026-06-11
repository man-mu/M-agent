# MCP Headers 认证支持 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 MCP 模块新增 `headers` 和 `apiKey` 字段，使其能够接入外部 MCP 服务市场（如 ModelScope），支持 HTTP Header 级别认证。

**Architecture:** 在现有 `McpServerInfo` 模型上扩展两个可选字段，在 `McpConfigMergeUtil` 传输层创建时解析并注入到 `WebClient.Builder.defaultHeader()`，在 `McpServerConfigService` 安全校验层扩展明文密钥拦截，在 `ServerStatus` 中暴露认证方式元数据。

**Tech Stack:** Java 17, Spring WebFlux, Spring AI MCP Client, Jackson

---

## 文件结构

| 文件 | 职责 | 改动类型 |
|------|------|---------|
| `McpProperties.java` | 数据模型：`McpServerInfo` 新增 `headers`、`apiKey` | 修改 |
| `McpConfigMergeUtil.java` | 传输层：解析 headers、apiKey，注入 WebClient | 修改 |
| `McpServerConfigService.java` | 安全校验：扩展 `requireNoInlineSecret` 到 headers/apiKey | 修改 |
| `McpToolProvider.java` | 状态展示：`ServerStatus` 新增 `hasHeaders`、`hasApiKey` | 修改 |
| `McpConfigMergeUtilTest.java` | 测试：headers 解析、apiKey 注入、占位符解析 | 修改 |
| `McpServerConfigServiceTest.java` | 测试：安全拦截 headers/apiKey 明文密钥 | 修改 |
| `McpToolProviderTest.java` | 测试：ServerStatus 正确暴露认证元数据 | 修改 |

---

### Task 1: 扩展 McpServerInfo 数据模型

**Files:**
- Modify: `src/main/java/top/lanshan/manmu/config/McpProperties.java`

- [ ] **Step 1: 在 `McpServerInfo` 类末尾新增 `headers` 和 `apiKey` 字段**

在 `allowedTools` 字段之后、类的闭合大括号之前加入以下代码：

```java
@JsonProperty("headers")
private Map<String, String> headers = new LinkedHashMap<>();

@JsonProperty("api-key")
@JsonAlias("apiKey")
private String apiKey;

public Map<String, String> getHeaders() { return headers; }
public void setHeaders(Map<String, String> headers) {
    this.headers = headers == null ? new LinkedHashMap<>() : headers;
}
public String getApiKey() { return apiKey; }
public void setApiKey(String apiKey) { this.apiKey = apiKey; }
```

需要在文件头部新增 import：

```java
import java.util.LinkedHashMap;
```

- [ ] **Step 2: 编译验证**

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn compile -pl . -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/top/lanshan/manmu/config/McpProperties.java
git commit -m "feat: McpServerInfo 新增 headers 和 apiKey 字段"
```

---

### Task 2: 传输层注入 Headers

**Files:**
- Modify: `src/main/java/top/lanshan/manmu/mcp/McpConfigMergeUtil.java`

- [ ] **Step 1: 新增 `resolveHeaders()` 静态方法**

在 `McpConfigMergeUtil` 类末尾、闭合大括号之前加入：

```java
static Map<String, String> resolveHeaders(McpProperties.McpServerInfo serverInfo) {
    Map<String, String> resolved = new LinkedHashMap<>();
    if (serverInfo.getHeaders() == null || serverInfo.getHeaders().isEmpty()) {
        return resolved;
    }
    serverInfo.getHeaders().forEach((name, value) -> {
        if (name != null && !name.isBlank() && value != null) {
            resolved.put(name.strip(), resolvePlaceholders(value));
        }
    });
    return resolved;
}

static boolean hasAuthorizationHeader(Map<String, String> headers) {
    if (headers == null || headers.isEmpty()) {
        return false;
    }
    return headers.keySet().stream()
            .anyMatch(k -> "Authorization".equalsIgnoreCase(k));
}
```

需要新增 import：
```java
import java.util.LinkedHashMap;
```

- [ ] **Step 2: 修改 `createNamedTransports()` 注入 headers**

在 `createNamedTransports()` 方法的 for 循环内，`baseUrl` 设置之后、transport 创建之前，加入 headers 注入逻辑。找到这段代码：

```java
WebClient.Builder clone = webClientBuilder.clone().baseUrl(resolvePlaceholders(si.getUrl()));
String sseEndpoint = si.getSseEndpoint() != null ? si.getSseEndpoint() : "/sse";
sseEndpoint = resolvePlaceholders(sseEndpoint);
WebFluxSseClientTransport transport = WebFluxSseClientTransport.builder(clone)
    .sseEndpoint(sseEndpoint)
    .objectMapper(objectMapper)
    .build();
```

替换为：

```java
WebClient.Builder clone = webClientBuilder.clone().baseUrl(resolvePlaceholders(si.getUrl()));

Map<String, String> headers = resolveHeaders(si);
boolean hasAuth = hasAuthorizationHeader(headers);
headers.forEach(clone::defaultHeader);

if (!hasAuth && si.getApiKey() != null && !si.getApiKey().isBlank()) {
    clone.defaultHeader("Authorization", "Bearer " + resolvePlaceholders(si.getApiKey()));
}

String sseEndpoint = si.getSseEndpoint() != null ? si.getSseEndpoint() : "/sse";
sseEndpoint = resolvePlaceholders(sseEndpoint);
WebFluxSseClientTransport transport = WebFluxSseClientTransport.builder(clone)
    .sseEndpoint(sseEndpoint)
    .objectMapper(objectMapper)
    .build();
```

- [ ] **Step 3: 编译验证**

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn compile -pl . -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: 运行现有 MCP 测试确保无回归**

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=McpConfigMergeUtilTest,McpToolProviderTest,McpServerConfigServiceTest' test
```

Expected: Tests run: XX, Failures: 0, Errors: 0

- [ ] **Step 5: Commit**

```bash
git add src/main/java/top/lanshan/manmu/mcp/McpConfigMergeUtil.java
git commit -m "feat: McpConfigMergeUtil 支持 headers 注入和 apiKey 快捷认证"
```

---

### Task 3: 扩展安全校验

**Files:**
- Modify: `src/main/java/top/lanshan/manmu/mcp/McpServerConfigService.java`

- [ ] **Step 1: 在 `sanitizeForWrite()` 中新增 headers 和 apiKey 的密钥拦截**

在 `sanitizeForWrite()` 方法中，找到现有的两行校验：

```java
requireNoInlineSecret(url);
// ... 
requireNoInlineSecret(server.getSseEndpoint());
```

在其后新增 headers 和 apiKey 的校验：

```java
if (server.getHeaders() != null) {
    server.getHeaders().values().forEach(McpServerConfigService::requireNoInlineSecret);
}
if (server.getApiKey() != null) {
    requireNoInlineSecret(server.getApiKey());
}
```

注意 `requireNoInlineSecret` 是实例方法，headers 遍历时需要改为静态方法引用。检查当前 `requireNoInlineSecret` 签名：

```java
private void requireNoInlineSecret(String value) {
```

将其改为 `private static`：

```java
private static void requireNoInlineSecret(String value) {
```

需要更新调用点——同理将引用的 `SENSITIVE_QUERY_PARAM` 也无需改动（已是 static final）。

如果 `requireNoInlineSecret` 中有对实例字段的引用，检查后发现没有，可安全改为 static。

- [ ] **Step 2: 编译验证**

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn compile -pl . -q
```

- [ ] **Step 3: 运行安全相关测试**

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=McpServerConfigServiceTest' test
```

Expected: 现有 `rejectsInvalidValuesAndMissingDeleteTargets` 测试仍然 PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/top/lanshan/manmu/mcp/McpServerConfigService.java
git commit -m "feat: McpServerConfigService 安全校验扩展到 headers 和 apiKey"
```

---

### Task 4: ServerStatus 新增认证元数据

**Files:**
- Modify: `src/main/java/top/lanshan/manmu/mcp/McpToolProvider.java`

- [ ] **Step 1: 在 `ServerStatus` record 中新增字段**

找到 `ServerStatus` record 定义（约第 286 行），在现有字段末尾、闭合括号之前新增：

```java
boolean hasHeaders,
boolean hasApiKey,
List<String> headerNames
```

- [ ] **Step 2: 更新三个静态工厂方法**

`ServerStatus.connected()` 方法签名和调用更新：

```java
static ServerStatus connected(McpProperties.McpServerInfo server) {
    return new ServerStatus(server.getId(), sanitizeConfigValue(server.getUrl()),
            sanitizeConfigValue(server.getSseEndpoint()),
            server.getDescription(), server.isEnabled(), true, "",
            allowedTools(server), keyEnvName(server), keyConfigured(server),
            requiredEnvVars(server), serverSource(server), true, serverLocalOverride(server),
            effectiveHeaders(server) != null && !effectiveHeaders(server).isEmpty(),
            server.getApiKey() != null && !server.getApiKey().isBlank(),
            headerNames(server));
}
```

`ServerStatus.failed()` 和 `ServerStatus.disabled()` 同样追加最后三个参数，值直接用 server 的真实数据（与 connected 相同逻辑）。

- [ ] **Step 3: 新增三个辅助方法**

在 `ServerStatus` record 内部，现有静态辅助方法之后新增：

```java
private static Map<String, String> effectiveHeaders(McpProperties.McpServerInfo server) {
    return server.getHeaders() == null ? Map.of() : server.getHeaders();
}

private static List<String> headerNames(McpProperties.McpServerInfo server) {
    Map<String, String> headers = effectiveHeaders(server);
    if (headers.isEmpty()) {
        return List.of();
    }
    return headers.keySet().stream()
            .filter(name -> name != null && !name.isBlank())
            .map(String::strip)
            .distinct()
            .toList();
}
```

- [ ] **Step 4: 编译验证**

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn compile -pl . -q
```

- [ ] **Step 5: 运行 MCP 测试**

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=McpToolProviderTest' test
```

Expected: 现有 `exposesNonSensitiveServerMetadataInStatus` 测试 FAIL（新字段导致构造参数数量变化），需要修复。

- [ ] **Step 6: Commit**

```bash
git add src/main/java/top/lanshan/manmu/mcp/McpToolProvider.java
git commit -m "feat: McpToolProvider.ServerStatus 新增 hasHeaders/hasApiKey/headerNames 字段"
```

---

### Task 5: 补充和修复测试

**Files:**
- Modify: `src/test/java/top/lanshan/manmu/mcp/McpConfigMergeUtilTest.java`
- Modify: `src/test/java/top/lanshan/manmu/mcp/McpServerConfigServiceTest.java`
- Modify: `src/test/java/top/lanshan/manmu/mcp/McpToolProviderTest.java`

- [ ] **Step 1: 修复 McpToolProviderTest**

`exposesNonSensitiveServerMetadataInStatus` 测试中 `ServerStatus` 构造参数已变，需要在 `assertThat(status.allowedTools()...)` 之前将测试代码调整为不直接构造 ServerStatus 而是通过 `getStatus()` 获取（现有测试已经这样做了）。如果测试直接调用 `ServerStatus` 静态方法导致编译失败，更新调用点的参数匹配。

运行确认修复后 PASS：
```powershell
mvn '-Dtest=McpToolProviderTest' test
```

- [ ] **Step 2: 新增 McpConfigMergeUtilTest — headers 解析和 apiKey 注入**

在 `McpConfigMergeUtilTest` 末尾（类闭合大括号之前）新增以下测试：

```java
@Test
void resolveHeadersParsesPlaceholdersInValues() {
    McpProperties.McpServerInfo info = new McpProperties.McpServerInfo();
    info.setHeaders(Map.of(
            "Authorization", "Bearer ${TEST_TOKEN}",
            "X-Tenant-ID", "static-tenant"));

    Map<String, String> resolved = McpConfigMergeUtil.resolveHeaders(info);

    // ${TEST_TOKEN} 当前环境变量不存在，解析为空
    assertThat(resolved).containsEntry("Authorization", "Bearer ");
    assertThat(resolved).containsEntry("X-Tenant-ID", "static-tenant");
}

@Test
void hasAuthorizationHeaderDetectsCaseInsensitively() {
    assertThat(McpConfigMergeUtil.hasAuthorizationHeader(
            Map.of("Authorization", "Bearer x"))).isTrue();
    assertThat(McpConfigMergeUtil.hasAuthorizationHeader(
            Map.of("authorization", "Bearer x"))).isTrue();
    assertThat(McpConfigMergeUtil.hasAuthorizationHeader(
            Map.of("X-API-Key", "abc"))).isFalse();
    assertThat(McpConfigMergeUtil.hasAuthorizationHeader(Map.of())).isFalse();
    assertThat(McpConfigMergeUtil.hasAuthorizationHeader(null)).isFalse();
}

@Test
void resolveHeadersSkipsBlankNamesAndNullValues() {
    McpProperties.McpServerInfo info = new McpProperties.McpServerInfo();
    Map<String, String> raw = new java.util.LinkedHashMap<>();
    raw.put("Authorization", "Bearer x");
    raw.put("  ", "should-skip");
    raw.put("X-Valid", "value");
    raw.put(null, "should-skip");
    info.setHeaders(raw);

    Map<String, String> resolved = McpConfigMergeUtil.resolveHeaders(info);

    assertThat(resolved).hasSize(2);
    assertThat(resolved).containsKeys("Authorization", "X-Valid");
}
```

- [ ] **Step 3: 新增 McpServerConfigServiceTest — headers 和 apiKey 安全拦截**

在 `McpServerConfigServiceTest` 的 `rejectsInvalidValuesAndMissingDeleteTargets` 测试方法末尾新增断言：

```java
// headers 中的明文密钥应被拦截
McpProperties.McpServerInfo headerKey = server(
        "header-key", "https://example.com", "/sse", "bad", true, List.of());
headerKey.setHeaders(Map.of("X-API-Key", "secret-value"));
assertThatThrownBy(() -> service.create(headerKey))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("${ENV_NAME}");

// headers 中使用占位符应通过
McpProperties.McpServerInfo headerPlaceholder = server(
        "header-placeholder", "https://example.com", "/sse", "ok", true, List.of());
headerPlaceholder.setHeaders(Map.of("X-API-Key", "${EXAMPLE_API_KEY}"));
assertThat(service.create(headerPlaceholder).getHeaders())
        .containsEntry("X-API-Key", "${EXAMPLE_API_KEY}");

// apiKey 明文应被拦截
McpProperties.McpServerInfo apiKeyPlain = server(
        "apikey-plain", "https://example.com", "/sse", "bad", true, List.of());
apiKeyPlain.setApiKey("sk-plain-secret");
assertThatThrownBy(() -> service.create(apiKeyPlain))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("${ENV_NAME}");

// apiKey 占位符应通过
McpProperties.McpServerInfo apiKeyPlaceholder = server(
        "apikey-placeholder", "https://example.com", "/sse", "ok", true, List.of());
apiKeyPlaceholder.setApiKey("${MODELSCOPE_API_TOKEN}");
assertThat(service.create(apiKeyPlaceholder).getApiKey())
        .isEqualTo("${MODELSCOPE_API_TOKEN}");
```

- [ ] **Step 4: 运行全部 MCP 测试**

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=McpConfigMergeUtilTest,McpToolProviderTest,McpServerConfigServiceTest' test
```

Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add src/test/java/top/lanshan/manmu/mcp/McpConfigMergeUtilTest.java src/test/java/top/lanshan/manmu/mcp/McpServerConfigServiceTest.java src/test/java/top/lanshan/manmu/mcp/McpToolProviderTest.java
git commit -m "test: 补充 MCP headers 和 apiKey 相关测试用例"
```

---

### Task 6: 更新 mcp-config.json 示例

**Files:**
- Modify: `src/main/resources/mcp-config.json`

- [ ] **Step 1: 将高德 MCP 改为 apiKey 模式演示**

将 `mcp-amap` 的 sse-endpoint 从 `"/sse?key=${AMAP_MAPS_API_KEY}"` 改为 `"/sse"`，并新增 `"api-key": "${AMAP_MAPS_API_KEY}"`：

```json
{
    "id": "mcp-amap",
    "url": "https://mcp.amap.com",
    "sse-endpoint": "/sse",
    "description": "高德地图 MCP - 天气、地址解析、POI 搜索、路线规划和距离测量",
    "enabled": true,
    "api-key": "${AMAP_MAPS_API_KEY}",
    "allowed-tools": [
        "maps_weather",
        "maps_geo",
        "maps_regeo",
        "maps_text_search",
        "maps_around_search",
        "maps_ip_location",
        "maps_direction_driving",
        "maps_direction_walking",
        "maps_direction_bicycling",
        "maps_distance"
    ]
}
```

- [ ] **Step 2: 编译 + 测试确认 JSON 解析兼容**

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn test
```

Expected: ALL PASS

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/mcp-config.json
git commit -m "feat: mcp-config.json 高德 MCP 改为 apiKey 认证模式示例"
```
