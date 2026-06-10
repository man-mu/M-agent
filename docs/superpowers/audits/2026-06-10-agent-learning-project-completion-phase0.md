# Agent 学习项目需求补全 Phase 0 基线审计

## 审计目标

本阶段只记录当前项目状态，不修改业务代码。目标是确认补全计划执行前的功能边界、验证状态、Docker/PostgreSQL 与端口状态，避免后续阶段重复实现已有能力或误判缺口。

对应计划：

- `docs/superpowers/plans/2026-06-10-agent-learning-project-completion.md`

## 工作树与忽略规则

- 执行 `git status --short --untracked-files=all`：工作树无可见未提交变更。
- `.gitignore` 当前包含 `docs/`，因此 `docs/superpowers/plans/*` 与 `docs/superpowers/audits/*` 默认不会显示在 `git status` 中。
- 本阶段若提交计划和审计文档，需要使用 `git add -f` 精确加入目标文件。
- 未读取或输出 `.local/model-providers.json`、`.local/mcp-keys.json` 等密钥文件。

## 文档与交付物现状

命令：

```powershell
rg --files | rg -i "readme|demo|video|architecture|skill.*guide"
```

结果：

- 仅发现 `tools/local-qweather-mcp/README.md`。
- 根目录缺少 `README.md`。
- 未发现项目级 Demo 脚本、Demo 视频、架构图文档或 Skill 开发指南。

结论：

- Phase 1 需要优先补齐根目录 README、Skill 开发指南、Demo 脚本和 MCP 文档。

## 短期记忆调用链现状

命令：

```powershell
rg -n "formatConversationHistory|ConversationMemoryService|conversationHistory|Previous conversation" src\main\java src\test\java
```

观察：

- `ConversationMemoryService.formatConversationHistory(String sessionId)` 已定义。
- `PostgresConversationMemoryService.formatConversationHistory(...)` 已实现，并会输出 `Previous conversation in this session:`。
- `PostgresConversationMemoryServiceTest` 覆盖了格式化、截断和排序。
- `GraphResearchRunner` 注入并使用 `ConversationMemoryService` 保存 USER / ASSISTANT 消息。
- 未发现 `formatConversationHistory(...)` 在主流程中被读取并写入 Coordinator / Planner / Reporter 等模型提示词。

结论：

- 当前已具备对话消息持久化和格式化能力。
- “短期对话窗口进入推理上下文”仍是缺口，Phase 2 需要接入真实推理路径。

## Skill 与 Jar 插件配置现状

命令：

```powershell
rg -n "jar-plugins|mvp:\s*$|skill:\s*$|enabled: false|enabled: true|config-location|mcp:" src\main\resources\application.yml src\main\resources\mcp-config.json
```

观察：

- `mvp.skill.enabled=true`。
- `mvp.skill.jar-plugins.enabled=false`。
- `mvp.mcp.enabled=true`。
- `mvp.mcp.config-location=classpath:mcp-config.json`。

结论：

- Skill 市场默认启用。
- Jar Skill 上传和热加载能力已有代码与测试支撑，但默认关闭，符合“可信本地插件默认不暴露”的安全姿态。
- Phase 4 需要补充可信启用方式、演示包、控制台提示和真实 HTTP 验证。

## API 边界现状

命令：

```powershell
rg -n "@RequestMapping|@GetMapping|@PostMapping|@PatchMapping|@DeleteMapping|/api/model|/api/skills|/api/mcp|/chat|/api/research" src\main\java\top\lanshan\manmu\api src\main\java\top\lanshan\manmu\skill\service\SkillController.java src\main\java\top\lanshan\manmu\mcp\McpStatusController.java
```

已确认主要接口：

- 模型：`/api/model/providers`、`/api/model/current`、`/api/model/providers/{providerId}/key`、`/api/model/switch`、`/api/model/test`。
- Skill：`/api/skills`、`/api/skills/{name}`、`/api/skills/{name}/health`、`/api/skills/{name}/validate`、`/api/skills/{name}/invocations`、`/api/skills/packages/import`、`/api/skills/packages/import-jar`、`/api/skills/{name}/export`、`/api/skills/{name}/toggle`、`/api/skills/{name}/reload`、`/api/skills/packages/{name}`。
- MCP：`/api/mcp/status`、`/api/mcp/servers`、`/api/mcp/servers/{id}`、`/api/mcp/servers/{id}/toggle`、`/api/mcp/servers/{id}/test`、`/api/mcp/reload`、`/api/mcp/tools/{toolName}/invoke`。
- Chat / Research：`/chat/stream`、`/chat/resume`、`/chat/stop`、`/api/research/stream`。
- 持久化查询：`/api/conversations`、`/api/reports`、`/api/sessions`。

结论：

- 基础管理 API 边界已比较完整。
- 后续阶段重点是补齐缺口和交付说明，而不是重写这些接口。

## 测试结果

### 后端

命令：

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn test
```

结果：

- BUILD SUCCESS。
- Tests run: 275。
- Failures: 0。
- Errors: 0。
- Skipped: 3。

### 前端

命令：

```powershell
cd ui-vue3
npm run test:unit
```

结果：

- Test Files: 12 passed。
- Tests: 68 passed。

### 本地和风天气 MCP

命令：

```powershell
cd tools/local-qweather-mcp
npm test
```

结果：

- Test Files: 3 passed。
- Tests: 10 passed。

## Docker 与端口状态

命令：

```powershell
docker compose up -d postgres
docker ps --format "{{.Names}}\t{{.Status}}\t{{.Ports}}"
```

结果：

- `manmu-postgres` 已启动并处于 `healthy`。
- 端口映射：`0.0.0.0:5432->5432/tcp`、`[::]:5432->5432/tcp`。

端口复查命令：

```powershell
$ports = 5432,18080,18090
foreach ($port in $ports) {
  $items = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue |
    Select-Object LocalAddress,LocalPort,State,OwningProcess
  if ($items) {
    "PORT ${port}"
    $items | Format-Table -AutoSize | Out-String
  } else {
    "PORT ${port}: free"
  }
}
```

结果：

- `5432`：监听中，PostgreSQL 容器占用。
- `18080`：空闲。
- `18090`：空闲。

结论：

- Docker Desktop 当前可用。
- PostgreSQL 已可作为后续真实 HTTP/SSE 验证依赖。
- 后端默认验证端口 `18080` 空闲。
- 本地 MCP 示例端口 `18090` 空闲，后续 Phase 5 可启动 `tools/local-qweather-mcp` 验证。

## Phase 0 验收结论

- 审计文档已记录当前完成项、缺口、端口状态和验证结果。
- 本阶段没有修改业务代码。
- 后续阶段无需先解决 Docker Desktop 不可用问题；当前 PostgreSQL 容器已健康。
- 后续提交需要注意 `docs/` 被忽略，计划和审计文档需精确 `git add -f`。

## 下一步建议

进入 Phase 1：补齐根目录 README、Skill 开发指南、Demo 脚本，并修复 MCP 文档中的中文乱码与启动说明。
