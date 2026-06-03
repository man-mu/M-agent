# Skill 市场与 MCP 管理优化计划

## Background

当前项目是 Java 17 / Maven / Spring Boot 3.4.x 后端，主包名为 `top.lanshan.manmu`，前端为 `ui-vue3`。现有能力已经覆盖了 Prompt 型 Skill 管理、MCP 状态展示、本地和风天气 MCP 示例，以及 `@weather-now` 聊天真实调用链路。

图中与本计划直接相关的要求包括：

- 插件化 Skill 市场：Agent 可动态加载/卸载技能，技能以独立模块打包，市场提供技能注册、发现、启用/禁用功能。
- MCP 兼容：支持通过 MCP 服务端发现和调用外部工具，至少实现一个 MCP 示例。
- Skill 市场设计：每个 Skill 包含元数据（名称、描述、参数 schema、版本、依赖）。
- 允许用户自行上传新 Skill（上传 Jar/配置文件并热加载，注意类加载器隔离）。
- 提供简单管理界面（CLI 或 Web 控制台）。

现有代码事实：

- Skill 后端：
  - `src/main/java/top/lanshan/manmu/skill/service/SkillDefinition.java`
  - `src/main/java/top/lanshan/manmu/skill/service/SkillFileRepository.java`
  - `src/main/java/top/lanshan/manmu/skill/service/SkillRegistry.java`
  - `src/main/java/top/lanshan/manmu/skill/service/SkillService.java`
  - `src/main/java/top/lanshan/manmu/skill/service/SkillController.java`
  - `src/main/java/top/lanshan/manmu/skill/service/SkillToolProvider.java`
  - `src/main/java/top/lanshan/manmu/skill/service/SkillToolCallback.java`
- Skill 内容目录：`src/main/java/top/lanshan/manmu/skill/content/{name}/skill.json + SKILL.md`
- Skill 前端：
  - `ui-vue3/src/views/skills/index.vue`
  - `ui-vue3/src/views/skills/skillForm.ts`
  - `ui-vue3/src/services/api/skills.ts`
- MCP 后端：
  - `src/main/java/top/lanshan/manmu/config/McpProperties.java`
  - `src/main/java/top/lanshan/manmu/mcp/McpConfigMergeUtil.java`
  - `src/main/java/top/lanshan/manmu/mcp/McpToolProvider.java`
  - `src/main/java/top/lanshan/manmu/mcp/McpStatusController.java`
  - `src/main/resources/mcp-config.json`
- MCP 前端：
  - `ui-vue3/src/views/mcp/index.vue`
  - `ui-vue3/src/views/mcp/mcpTools.ts`
  - `ui-vue3/src/services/api/app.ts`
- 本地 MCP 示例：
  - `tools/local-qweather-mcp`
  - 工具：`weather_now`
  - Skill：`src/main/java/top/lanshan/manmu/skill/content/weather-now`

当前差距：

- Skill 还不是完整“市场”：没有 catalog、安装/卸载包、版本升级、导入/导出、来源校验、依赖健康检查。
- Skill 上传只支持页面表单创建 Prompt 模板，不支持上传 zip/Jar 包。
- Skill 文件默认写在源码目录，不适合作为用户上传市场内容的长期存储。
- 当前热加载是内存注册表 register/unregister 级别，还没有包级生命周期、启用状态持久化、插件实例释放。
- 没有 Java Jar 插件型 Skill，也没有类加载器隔离。
- MCP 页是状态展示，不支持前端新增/编辑/删除 MCP Server、测试连接、直接调试工具调用。

## Overall Goals

1. 将现有 Prompt Skill 升级为可导入、导出、安装、卸载、启用、禁用的本地 Skill 市场。
2. 保留现有 `skill.json + SKILL.md` 轻量 Skill 形态，优先支持 zip 包上传和热加载。
3. 为后续 Java Jar 插件型 Skill 增加清晰接口、包结构、类加载器隔离和生命周期边界。
4. 增强 MCP 管理能力：Web 控制台支持 MCP Server 配置管理、连接测试、工具调试。
5. 保持真实模型 + MCP + Skill 调用路径可测，不引入 mock fallback。
6. 不泄露 `.local/model-providers.json`、`.local/mcp-keys.json` 或任何 API Key。

## Non-goals

- 不实现完整 SaaS 市场、远程支付、公开发布审核、用户权限、多租户。
- 不把所有现有 Agent 架构重写成插件系统。
- 不新增 Redis、RAG 完整复杂模块或前端大型低代码平台。
- 不把 MCP 从 SSE 客户端整体迁移到 Streamable HTTP；第一版继续兼容当前 WebFlux SSE MCP Client。
- 不宣称 Java `ClassLoader` 可以安全执行不可信代码。类加载器隔离只作为 API/依赖隔离，不等价于安全沙箱；Jar 插件默认仅允许本地可信用户安装。
- 不把 `.local/`、`target/`、`.idea/`、`.claude/` 纳入提交。

## Phase 0: Baseline Audit

### Goal

明确当前 Skill/MCP 运行边界、文件写入位置、前端行为和真实链路状态，避免后续计划误改已有可用能力。

### Main Changes

原则上不改业务代码，只新增或更新 `docs/superpowers/plans/` 下的审计记录。

重点检查：

- `src/main/java/top/lanshan/manmu/skill/service/*`
- `src/main/java/top/lanshan/manmu/skill/content/*`
- `src/main/java/top/lanshan/manmu/mcp/*`
- `src/main/resources/mcp-config.json`
- `ui-vue3/src/views/skills/*`
- `ui-vue3/src/views/mcp/*`
- `tools/local-qweather-mcp/*`
- `.gitignore`

确认点：

- 当前 Skill 默认内容目录是否仍为源码目录。
- 当前 CRUD 写文件时是否允许路径穿越、非法名称、空 schema。
- 当前 registry 是否支持 reload、启停、删除后 ToolCallback 重新生成。
- 当前 MCP 状态是否需要重启后端才能刷新工具列表。
- 前端 `/skills`、`/mcp` 的移动端与桌面端现状。

### Tests

```powershell
git status --short
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=top.lanshan.manmu.skill.service.*Test,top.lanshan.manmu.mcp.*Test' test
cd ui-vue3
npm run test:unit -- skillForm.spec.ts mcpTools.spec.ts
```

### Real Verification

启动 PostgreSQL、本地和风天气 MCP、后端、前端后验证：

```powershell
curl.exe http://localhost:18080/api/app/capabilities
curl.exe http://localhost:18080/api/skills
curl.exe http://localhost:18080/api/mcp/status
```

打开：

- `/skills`
- `/mcp`
- `/chat`

测试结束后关闭后端、本地 MCP、前端，并确认：

```powershell
Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue
Get-NetTCPConnection -LocalPort 18090 -ErrorAction SilentlyContinue
Get-NetTCPConnection -LocalPort 5173 -ErrorAction SilentlyContinue
```

### Acceptance Criteria

- 明确当前实现差距和后续改动边界。
- 没有读取或输出任何 Key。
- 没有修改 `.local/`、`.claude/`、`target/`、`.idea/`。

### Suggested Commit

`审计 Skill 市场和 MCP 管理现状`

## Phase 1: Skill 包规范与本地市场目录

### Goal

把现有 Prompt Skill 从“源码目录中的若干文件”升级为“可安装包”的基础形态，为上传、导入、导出、发现、版本管理打底。

### Main Changes

新增或调整建议：

- 新增 `src/main/java/top/lanshan/manmu/skill/market/SkillPackageManifest.java`
- 新增 `src/main/java/top/lanshan/manmu/skill/market/SkillPackageType.java`
- 新增 `src/main/java/top/lanshan/manmu/skill/market/SkillPackageStatus.java`
- 新增 `src/main/java/top/lanshan/manmu/skill/market/SkillPackageValidator.java`
- 新增 `src/main/java/top/lanshan/manmu/skill/market/SkillCatalogEntry.java`
- 新增 `src/main/java/top/lanshan/manmu/skill/market/SkillCatalogRepository.java`
- 调整 `SkillDefinition`，补充市场字段：
  - `displayName`
  - `category`
  - `author`
  - `homepage`
  - `tags`
  - `packageType`
  - `installedAt`
  - `updatedAt`
  - `source`
- 保留兼容：旧 `skill.json` 只有 `name/description/version/enabled/parameters/dependencies` 时仍可加载。

本地目录建议：

- 内置 Skill：继续保留 `src/main/java/top/lanshan/manmu/skill/content`
- 用户安装 Skill：`.local/skills/installed/{skillName}`
- 上传暂存：`.local/skills/uploads`
- catalog：`.local/skills/catalog.json`

配置建议：

- 新增 `mvp.skill.builtin-content-path`
- 新增 `mvp.skill.local-market-path`
- 旧 `mvp.skill.content-path` 保留兼容。

实现策略：

- `SkillFileRepository` 升级为可读多个根目录：内置只读、本地可写。
- 新增 `SkillStorageLocation` 标识 built-in / local。
- 内置 Skill 不允许从页面删除，只允许本地用户 Skill 删除。
- 所有写入路径必须校验 Skill name，禁止 `..`、路径分隔符、空白、控制字符。

### Tests

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=top.lanshan.manmu.skill.service.*Test,top.lanshan.manmu.skill.market.*Test' test
```

重点覆盖：

- 旧版 `skill.json` 兼容加载。
- 新字段能反序列化并返回前端。
- 内置目录只读、本地目录可写。
- 非法 Skill 名称被拒绝。
- catalog 不存在时自动创建空 catalog，不影响启动。

### Real Verification

启动后端到 `18080`，验证：

```powershell
curl.exe http://localhost:18080/api/skills
curl.exe http://localhost:18080/api/skills/weather-now
```

确认内置 `weather-now`、`code-review` 仍可见，且未把源码 Skill 复制到 `.local/`。

### Acceptance Criteria

- 现有 Skill 不破坏。
- 用户 Skill 有独立本地存储位置。
- Skill 元数据满足图中“名称、描述、参数 schema、版本、依赖”要求，并为市场展示预留字段。

### Suggested Commit

`定义 Skill 包元数据和本地市场目录`

## Phase 2: Prompt Skill Zip 导入/导出与安装卸载

### Goal

支持用户上传配置型 Skill 包，而不是只能在页面手动创建。先实现低风险的 Prompt Skill zip 包，作为“插件化 Skill 市场”的第一层可用能力。

### Main Changes

新增后端能力：

- `POST /api/skills/packages/import`
  - `multipart/form-data`
  - 上传 `.zip`
  - 解包到 `.local/skills/installed/{skillName}`
  - 校验 `skill.json`、`SKILL.md`
  - 热注册到 `SkillRegistry`
- `GET /api/skills/{name}/export`
  - 导出 zip，包含 `skill.json`、`SKILL.md`
- `DELETE /api/skills/packages/{name}`
  - 卸载本地安装的 Skill
  - 内置 Skill 返回 400，不允许卸载
- `POST /api/skills/{name}/reload`
  - 从磁盘重新加载单个 Skill

新增类建议：

- `SkillPackageArchiveService`
- `SkillPackageImportResult`
- `SkillPackageExportController` 或并入 `SkillController`

安全约束：

- 解压 zip 时必须防 Zip Slip。
- 限制包大小，例如 2MB。
- 只允许白名单文件：`skill.json`、`SKILL.md`、可选 `README.md`、`assets/*`。
- 不允许 zip 内出现 `.class`、`.jar`、脚本文件；Jar 插件在后续阶段单独处理。

前端增强：

- `ui-vue3/src/views/skills/index.vue`
  - 增加“导入 Skill 包”按钮。
  - 每个本地 Skill 增加“导出”“重载”“卸载”操作。
  - 内置 Skill 操作禁用并显示原因。
- `ui-vue3/src/services/api/skills.ts`
  - 增加 import/export/reload/uninstall API。

### Tests

后端：

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=top.lanshan.manmu.skill.service.*Test,top.lanshan.manmu.skill.market.*Test' test
```

前端：

```powershell
cd ui-vue3
npm run test:unit -- skillForm.spec.ts
npm run build
```

重点覆盖：

- zip import 成功后 registry 立即可见。
- export 结果可重新 import。
- Zip Slip 被拒绝。
- 超大文件被拒绝。
- 内置 Skill 不允许卸载。
- 本地 Skill 卸载后 ToolCallback 不再暴露。

### Real Verification

1. 准备一个本地 zip 包，内容为真实 Prompt Skill，不使用 mock。
2. 启动后端到 `18080`。
3. 上传：

```powershell
curl.exe -F "file=@target/http-check/sample-skill.zip" http://localhost:18080/api/skills/packages/import
curl.exe http://localhost:18080/api/skills
```

4. 在 `/skills` 确认该 Skill 出现、可启用/停用、可导出。
5. 在 `/chat` 使用 `@sample-skill ...` 验证真实模型路径能读取 Skill 指令。
6. 卸载后再次查询 `/api/skills`，确认消失。
7. 关闭后端并确认 `18080` 释放。

### Acceptance Criteria

- 用户可上传 Prompt Skill zip。
- 上传后无需重启后端即可被发现和使用。
- 可以导出和卸载本地 Skill。
- 不允许 zip 包写出 `.local/skills/installed` 边界。

### Suggested Commit

`支持 Prompt Skill 包导入导出和热加载`

## Phase 3: Skill 市场前端体验

### Goal

把 `/skills` 从 CRUD 页面升级为本地 Skill 市场控制台，满足图中“市场提供注册、发现、启用/禁用”和“简单管理界面”的要求。

### Main Changes

前端改造：

- `ui-vue3/src/views/skills/index.vue`
  - 增加 Tab：
    - 已安装
    - 本地市场
    - 导入记录
  - 增加 Skill 详情抽屉：
    - 元数据
    - 参数 schema
    - 依赖
    - 来源
    - 安装位置
    - 启用状态
    - Prompt 预览
  - 增加导入 zip 的 Upload 控件。
  - 增加导出、卸载、重载按钮。
  - 对内置 Skill、本地 Skill 使用不同 badge。
- `ui-vue3/src/views/skills/skillForm.ts`
  - 增强 metadata 表单校验。
  - 支持 tags/category/dependencies 展示和编辑。
- `ui-vue3/src/services/api/skills.ts`
  - 对齐新接口。

UI 约束：

- 保持操作型管理页面风格，不做营销式卡片堆叠。
- 桌面端保留高信息密度表格。
- 移动端使用卡片列表，但按钮不能溢出。
- 不在页面显示真实 Key 或本地敏感路径完整内容；安装位置可显示相对路径或 “本地市场目录”。

### Tests

```powershell
cd ui-vue3
npm run test:unit -- skillForm.spec.ts
npm run build
```

新增单测建议：

- `skillMarket.spec.ts`
- `skillPackageImport.spec.ts`

### Real Verification

启动 PostgreSQL、后端、前端后：

- 打开 `/skills`
- 导入 Prompt Skill zip
- 搜索并筛选该 Skill
- 查看详情抽屉
- 启用/停用
- 导出
- 卸载

同时在 `390x844` 和 `1280x720` 视口检查：

- 无水平溢出。
- 操作按钮不重叠。
- 表格/卡片文本可读。

测试结束后关闭后端和前端，确认 `18080`、`5173` 释放。

### Acceptance Criteria

- `/skills` 能作为本地 Skill 市场控制台使用。
- 用户能发现、安装、启用、禁用、卸载 Skill。
- 页面能清楚区分内置 Skill 与用户安装 Skill。

### Suggested Commit

`完善 Skill 市场前端控制台`

## Phase 4: MCP Server 配置管理与连接测试

### Goal

把 `/mcp` 从状态展示页升级为 MCP 管理页，支持配置、发现、测试连接和安全指引。

### Main Changes

后端新增：

- `src/main/java/top/lanshan/manmu/mcp/McpServerConfigService.java`
- `src/main/java/top/lanshan/manmu/mcp/McpServerConfigController.java`
- `src/main/java/top/lanshan/manmu/mcp/McpConnectionTestResult.java`

API 建议：

- `GET /api/mcp/servers`
- `POST /api/mcp/servers`
- `PUT /api/mcp/servers/{id}`
- `DELETE /api/mcp/servers/{id}`
- `PATCH /api/mcp/servers/{id}/toggle`
- `POST /api/mcp/servers/{id}/test`
- `POST /api/mcp/reload`

配置存储建议：

- 内置：`src/main/resources/mcp-config.json`
- 用户本地覆盖：`.local/mcp-servers.json`
- 运行时合并：沿用 `McpConfigMergeUtil`，但新增用户配置来源。

注意：

- 不保存真实 Key，只保存 `${ENV_NAME}` 占位符和非敏感字段。
- `QWEATHER_API_KEY` 仍来自环境变量或 `.local/mcp-keys.json`。
- 修改 MCP 配置后需要清空 `McpToolProvider` 缓存并重新初始化。

前端增强：

- `ui-vue3/src/views/mcp/index.vue`
  - 新增 MCP Server 表单。
  - 支持新增/编辑/删除/启停。
  - 支持测试连接。
  - 支持刷新工具列表。
- `ui-vue3/src/views/mcp/mcpTools.ts`
  - 支持未知 MCP 工具通用展示。

### Tests

后端：

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=top.lanshan.manmu.mcp.*Test' test
```

前端：

```powershell
cd ui-vue3
npm run test:unit -- mcpTools.spec.ts
npm run build
```

重点覆盖：

- 新增配置不写入源码 `mcp-config.json`。
- 删除本地配置不影响内置配置。
- Key 占位符能被识别为 required env vars。
- 测试连接失败时不影响后端启动。
- reload 后 toolCount 更新。

### Real Verification

1. 启动本地和风天气 MCP 到 `18090`。
2. 启动后端到 `18080`。
3. 通过 API 或页面添加一个本地 MCP 配置。
4. 测试连接，期望 connected。
5. 关闭本地 MCP 后再次测试，期望错误清晰。
6. 重新启动本地 MCP 并 reload，确认 `/api/mcp/status` 恢复 connected。
7. 关闭服务并确认端口释放。

### Acceptance Criteria

- `/mcp` 支持 MCP Server 配置管理。
- 配置变更不需要改源码。
- 连接测试和 reload 可用。
- 不泄露任何 Key。

### Suggested Commit

`支持 MCP 服务配置管理和连接测试`

## Phase 5: MCP 工具调试台与示例扩展

### Goal

让 MCP 管理页支持直接调试工具调用，并补齐图中“通过 MCP 服务端发现和调用外部工具”的可见闭环。

### Main Changes

后端新增：

- `POST /api/mcp/tools/{toolName}/invoke`
  - 请求体为 JSON 参数。
  - 只允许调用当前 connected 且 allowed 的工具。
  - 响应包含 toolName、input、output、durationMs、error。
- `McpToolInvocationService`
  - 使用当前 `McpToolProvider` 的 `ToolCallback`。
  - 对返回内容做长度限制，例如 16KB。
  - 错误消息做安全清洗，不输出 Key。

前端新增：

- `/mcp` 工具列表中增加“调试”按钮。
- 工具调试抽屉：
  - JSON 参数编辑器。
  - 示例参数。
  - 调用结果。
  - 错误提示。
- 对 `weather_now` 提供默认示例：

```json
{
  "location": "上海",
  "lang": "zh",
  "unit": "m"
}
```

可选新增 MCP 示例：

- 如果需要更贴近图中“文件系统/数据库查询工具”示例，可新增一个只读本地 MCP 工具：
  - `tools/local-file-info-mcp`
  - 只允许读取项目根目录下白名单文件元信息，不读取敏感文件内容。
- 或新增只读 PostgreSQL schema 查询 MCP 工具：
  - 只查询表名/列名，不执行任意 SQL。

第一版建议不新增数据库任意查询 MCP，避免权限和注入风险；天气 MCP 已满足“至少一个 MCP 示例”。

### Tests

后端：

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=top.lanshan.manmu.mcp.*Test' test
```

前端：

```powershell
cd ui-vue3
npm run test:unit -- mcpTools.spec.ts
npm run build
```

本地 MCP：

```powershell
cd tools/local-qweather-mcp
npm run test
npm run build
```

### Real Verification

1. 启动本地和风天气 MCP、后端、前端。
2. 打开 `/mcp`。
3. 对 `weather_now` 输入 `{"location":"上海","lang":"zh","unit":"m"}`。
4. 直接调试返回真实天气。
5. 再从 `/chat` 执行 `@weather-now 查询上海实时天气`，确认聊天链路仍通。
6. 查询会话历史接口确认 `COMPLETED`。
7. 关闭服务并确认 `18080`、`18090`、`5173` 释放。

### Acceptance Criteria

- MCP 工具可从 Web 控制台直接调试。
- 工具调试使用真实 MCP 服务，不使用 mock。
- 调试错误不会影响聊天链路。

### Suggested Commit

`新增 MCP 工具调试台`

## Phase 6: Java Jar Skill 插件接口与类加载器隔离

### Goal

实现图中“上传 Jar/配置文件并热加载，注意类加载器隔离”的可信本地插件能力。该阶段风险高，应在 Prompt Skill 市场稳定后执行。

### Main Changes

新增插件 API：

- `src/main/java/top/lanshan/manmu/skill/plugin/SkillPlugin.java`
- `src/main/java/top/lanshan/manmu/skill/plugin/SkillPluginContext.java`
- `src/main/java/top/lanshan/manmu/skill/plugin/SkillPluginResult.java`
- `src/main/java/top/lanshan/manmu/skill/plugin/SkillPluginDescriptor.java`

建议接口：

```java
public interface SkillPlugin extends AutoCloseable {
    SkillDefinition definition();
    String execute(Map<String, Object> input, SkillPluginContext context);
    default void close() {}
}
```

Jar 包结构：

```text
skill.json
plugin.jar
README.md
```

或：

```text
skill.json
lib/plugin.jar
META-INF/services/top.lanshan.manmu.skill.plugin.SkillPlugin
```

新增加载器：

- `JarSkillPackageLoader`
- `SkillPluginClassLoader`
- `SkillPluginRegistry`
- `JarSkillToolCallback`

隔离策略：

- 每个 Jar Skill 一个独立 `URLClassLoader`。
- 插件只共享 `top.lanshan.manmu.skill.plugin` API 包和 JDK 基础类。
- 卸载时调用 `close()` 并关闭 ClassLoader。
- 不允许插件覆盖 Spring Bean，不把插件放入 Spring ApplicationContext。
- 禁止插件直接访问 `.local/model-providers.json` 的任何内容；Context 不暴露 Key。
- 明确文档：这是本地可信插件加载，不是安全沙箱。

上传策略：

- Jar Skill 上传单独入口：
  - `POST /api/skills/packages/import-jar`
- 默认禁用 Jar 上传：
  - `mvp.skill.jar-plugins.enabled=false`
- 用户显式启用后才允许上传。

### Tests

后端：

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=top.lanshan.manmu.skill.*Test' test
```

新增测试：

- 示例 Jar Skill 加载成功。
- Jar Skill execute 返回真实结果。
- 卸载后 ClassLoader 关闭，ToolCallback 不再可见。
- `mvp.skill.jar-plugins.enabled=false` 时上传 Jar 返回 403 或 400。
- 非法 Jar 包、缺少 ServiceLoader、缺少 skill.json 均被拒绝。

### Real Verification

1. 构建一个本地可信示例 Jar Skill，例如 `echo-json-skill`，不调用外部网络。
2. 启动后端，启用 `mvp.skill.jar-plugins.enabled=true`。
3. 上传 Jar Skill。
4. `/api/skills` 能看到该 Skill。
5. `/chat` 中显式调用 `@echo-json-skill ...`，确认真实模型路径可见该 Skill。
6. 卸载后确认不可再调用。
7. 关闭后端并确认端口释放。

### Acceptance Criteria

- 支持 Jar Skill 上传和热加载。
- 每个 Jar Skill 使用独立 ClassLoader。
- 可卸载并释放插件生命周期。
- 默认关闭 Jar 插件上传，避免误暴露高风险能力。

### Suggested Commit

`支持可信 Jar Skill 插件加载`

## Phase 7: 依赖健康检查、调用记录与市场质量网

### Goal

补齐 Skill 市场的可观测性和质量保障：用户能知道 Skill 依赖是否可用、调用是否成功、问题在哪里。

### Main Changes

后端新增：

- `SkillHealthService`
- `SkillDependencyHealth`
- `SkillInvocationRecord`
- `SkillInvocationHistoryService`

存储建议：

- 轻量阶段可先文件或内存，后续如需要再落 PostgreSQL。
- 如果落 PostgreSQL，新增 Flyway：
  - `V9__create_skill_invocation_history.sql`

健康检查内容：

- Skill 是否启用。
- Prompt 模板是否存在。
- 参数 schema 是否可用。
- 依赖 MCP 是否 connected。
- 所需环境变量是否已配置。

API 建议：

- `GET /api/skills/{name}/health`
- `GET /api/skills/{name}/invocations`
- `POST /api/skills/{name}/validate`

前端：

- `/skills` 增加健康状态列。
- Skill 详情抽屉展示依赖健康和最近调用结果。
- `weather-now` 显示 `mcp-qweather` 是否 connected。

### Tests

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=top.lanshan.manmu.skill.*Test,top.lanshan.manmu.mcp.*Test' test
cd ui-vue3
npm run test:unit
npm run build
```

### Real Verification

1. 启动本地和风天气 MCP、后端、前端。
2. `/skills/weather-now/health` 显示依赖健康。
3. 关闭本地 MCP 后重新查询 health，显示依赖不可用且错误清晰。
4. 重新启动 MCP 后恢复健康。
5. 聊天调用 `@weather-now 查询上海实时天气`。
6. 查询 invocation history，确认记录成功调用，不包含 Key。
7. 关闭服务并确认端口释放。

### Acceptance Criteria

- Skill 能展示依赖健康。
- 调用记录可用于排查问题。
- 错误不泄露敏感信息。

### Suggested Commit

`补齐 Skill 依赖健康检查和调用记录`

## Phase 8: Full Validation And Handoff

### Goal

完成 Skill 市场 + MCP 管理优化的全量验证，确保导入/导出、启停、MCP 调试、真实聊天、持久化和前端页面都稳定。

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
5. Skill 市场：
   - 上传 Prompt Skill zip。
   - 启用/停用。
   - 导出。
   - 卸载。
   - 重载。
6. MCP 管理：
   - 查看本地和风天气 MCP。
   - 测试连接。
   - 调试 `weather_now`。
   - reload 后状态仍正确。
7. 聊天真实验证：

```text
@weather-now 查询上海实时天气
```

8. 持久化验证：
   - 查询会话历史接口。
   - 确认线程状态为 `COMPLETED`。
   - 消息内容可读取。
9. 浏览器验证：
   - `/skills`
   - `/mcp`
   - `/chat`
   - `390x844` 和 `1280x720` 视口无乱码、无重叠、无按钮溢出。
10. 关闭所有本地服务并确认：

```powershell
Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue
Get-NetTCPConnection -LocalPort 18090 -ErrorAction SilentlyContinue
Get-NetTCPConnection -LocalPort 5173 -ErrorAction SilentlyContinue
```

### Acceptance Criteria

- 自动化测试全通过，或明确记录外部依赖原因。
- Skill 市场满足发现、安装、启用/禁用、卸载、导出。
- MCP 管理满足配置、测试连接、工具调试。
- `weather-now` 真实链路继续可用。
- 后端、本地 MCP、前端验证后全部关闭。
- 没有提交任何 Key。

### Suggested Commit

`验证 Skill 市场和 MCP 管理全链路`

## Acceptance Checklist

- [ ] Skill 元数据包含名称、描述、参数 schema、版本、依赖。
- [ ] Skill 支持内置目录和本地市场目录。
- [ ] Skill 支持 Prompt zip 包上传、安装、热加载。
- [ ] Skill 支持导出、卸载、重载。
- [ ] `/skills` 提供市场式管理界面，支持发现、启用/禁用、安装、卸载。
- [ ] Jar Skill 插件默认关闭，但可在显式配置后上传可信 Jar。
- [ ] Jar Skill 使用独立 ClassLoader，并支持卸载释放。
- [ ] MCP Server 支持前端新增、编辑、删除、启停。
- [ ] MCP 支持连接测试和 reload。
- [ ] MCP 工具支持从 Web 控制台直接调试。
- [ ] 至少一个 MCP 示例保持真实可用：本地和风天气 MCP `weather_now`。
- [ ] `@weather-now` 聊天真实模型 + MCP 工具链路仍能返回真实天气。
- [ ] 所有新增上传/解压逻辑防 Zip Slip、防路径穿越、防敏感信息泄露。
- [ ] 前端 `/skills`、`/mcp`、`/chat` 桌面和移动端无明显布局问题。
- [ ] 每阶段真实验证后关闭本地后端、MCP、前端服务并确认端口释放。

## Recommended Execution Order

1. Phase 0：审计现状。
2. Phase 1：Skill 包规范与本地市场目录。
3. Phase 2：Prompt Skill zip 导入/导出与安装卸载。
4. Phase 3：Skill 市场前端体验。
5. Phase 4：MCP Server 配置管理与连接测试。
6. Phase 5：MCP 工具调试台与示例扩展。
7. Phase 6：可信 Jar Skill 插件接口与类加载器隔离。
8. Phase 7：依赖健康检查、调用记录与市场质量网。
9. Phase 8：全量验证和收尾。

建议先完成 Phase 1-5，形成稳定可用的“Prompt Skill 市场 + MCP 管理控制台”；确认体验和真实链路稳定后，再进入 Phase 6 的 Jar 插件能力。

每个阶段完成后按项目约定提交一次，commit 说明使用中文。
