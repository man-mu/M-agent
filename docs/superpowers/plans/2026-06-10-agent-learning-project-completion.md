# Agent 学习项目需求补全计划

## Background

本计划用于把当前项目从“核心代码能力大体具备，但交付验收不完整”补全到可按学习项目需求展示和验收的状态。

当前已确认事实：

- 后端是 Java 17 / Maven / Spring Boot 3.4.x，主包名 `top.lanshan.manmu`，WebFlux SSE 输出研究工作流进度。
- 已有模型供应商配置与运行时切换接口：`src/main/java/top/lanshan/manmu/api/ModelProviderController.java`、`src/main/java/top/lanshan/manmu/modelprovider/*`。
- 已有 PostgreSQL 会话消息持久化、用户画像和历史报告上下文能力：`src/main/java/top/lanshan/manmu/memory/*`、`src/main/java/top/lanshan/manmu/sessioncontext/*`。
- 已有 Skill 市场后端和 Vue 控制台：`src/main/java/top/lanshan/manmu/skill/*`、`ui-vue3/src/views/skills/*`。
- 已有 MCP 管理后端、Vue 控制台、本地和风天气 MCP 示例：`src/main/java/top/lanshan/manmu/mcp/*`、`ui-vue3/src/views/mcp/*`、`tools/local-qweather-mcp/*`。
- 已有 DeepResearch 风格图工作流，包含 coordinator、planner、research_team、parallel_executor、researcher、coder、reporter 等节点：`src/main/java/top/lanshan/manmu/graph/ResearchGraphBuilder.java`。
- 后端 `mvn test` 已通过，前端单测和本地 MCP 单测已通过。

当前主要缺口：

- 仓库根目录缺少 `README.md`，未交付架构图、Skill 开发指南、Agent Team 流程图和演示说明。
- 当前 `ConversationMemoryService.formatConversationHistory()` 有实现和测试，但未明显接入主推理提示词，短期对话窗口只能算部分完成。
- Jar Skill 上传和热加载实现存在，但默认 `mvp.skill.jar-plugins.enabled=false`，验收时需要可信本地启用方式、说明和真实演示。
- 已有 3 个 Skill，但方向偏天气、地点分析、代码审查；需求示例中的计算器和网络搜索 Skill 不够明确。
- Agent Team 目前更像研究图工作流，没有独立的“活动策划”团队协作 Demo、消息流展示和 README 设计说明。
- Demo 视频不在仓库内，至少需要可复现的录制脚本或清单。

## Overall Goals

1. 补齐需求验收所需文档：根目录 README、架构图、Skill 开发指南、MCP 示例说明、Agent Team 流程图和 Demo 流程。
2. 明确完成基础 Agent 架构：模型切换、短期/长期记忆、Skill 市场、MCP 工具发现与调用。
3. 补齐 3 个不同方向 Skill 演示：天气、计算器、网络搜索，并保证它们在控制台可发现、可启停、可验证。
4. 将短期会话窗口接入真实推理路径，同时保留 PostgreSQL 长期记忆和用户画像持久化能力。
5. 提供可信本地 Jar Skill 上传热加载的配置、控制台提示、测试和真实 HTTP 验证。
6. 补齐 Agent Team 高分项的最小可运行 Demo：Planner / Executor / Reviewer 角色、任务分发、状态事件和 Mermaid/前端可视化。
7. 每阶段完成后按项目要求提交一次中文 commit，并做单元测试 + 本地服务 HTTP/SSE + PostgreSQL 持久化验证。

## Non-goals

- 不重写现有 DeepResearch 图框架，不搬入完整复杂的 `deepresearch-main` RAG、Redis、MCP、前端大模块。
- 不实现真正不可信代码沙箱。Jar Skill 的 ClassLoader 隔离只用于本地可信插件的依赖和生命周期隔离，不宣称安全沙箱。
- 不把 Skill 市场做成远程 SaaS 市场，不实现用户权限、多租户、审核、支付和远程发布。
- 不把所有模型提供商改成数据库动态配置；本轮只补文档和可演示的本地/兼容 OpenAI 配置方式。
- 不提交 `.local/`、`target/`、`.idea/`、`.claude/`，不泄露任何 API Key。
- 不用 mock 替代真实模型、真实 MCP 或真实数据库路径。

## Phase 0: Baseline Audit

### Goal

在动手前记录当前功能边界，避免重复实现已经可用的能力，并确认 Docker Desktop / PostgreSQL / 本地端口状态。

### Main Changes

新增阶段审计记录：

- `docs/superpowers/audits/2026-06-10-agent-learning-project-completion-phase0.md`

审计内容：

- 根目录 `README*` 是否存在。
- `mvp.skill.jar-plugins.enabled` 默认值和启用方式。
- `ConversationMemoryService.formatConversationHistory()` 当前调用链。
- `/api/model`、`/api/skills`、`/api/mcp`、`/chat/stream`、`/api/research/stream` 的可用接口。
- Docker PostgreSQL 是否可启动，`localhost:5432`、`localhost:18080`、`localhost:18090` 是否占用。

### Tests

```powershell
git status --short
rg --files | rg -i "readme|demo|video|architecture|skill.*guide"
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn test
cd ui-vue3
npm run test:unit
cd ..\tools\local-qweather-mcp
npm test
```

### Real Verification

```powershell
docker compose up -d postgres
docker ps --format "{{.Names}}\t{{.Status}}\t{{.Ports}}"
Get-NetTCPConnection -LocalPort 5432 -ErrorAction SilentlyContinue
Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue
Get-NetTCPConnection -LocalPort 18090 -ErrorAction SilentlyContinue
```

### Acceptance Criteria

- 审计文档列出当前完成项、缺口、端口状态和验证结果。
- 没有修改业务代码。
- 明确后续阶段是否需要先启动 Docker Desktop。

### Suggested Commit

`docs: 记录 Agent 学习项目补全基线审计`

## Phase 1: README 与交付说明补全

### Goal

补齐验收最容易扣分的交付物，让评审能从根目录直接理解、启动、验证和录制 Demo。

### Main Changes

新增：

- `README.md`
- `docs/skill-development-guide.md`
- `docs/demo-script.md`

更新或补充：

- `docs/mcp-tools.md`，修复现有中文乱码内容，并补充本地和风天气 MCP 启动说明。

README 必须包含：

- 项目定位：学习用 Agent 后端 + Vue 控制台。
- 技术栈：Java 17、Spring Boot 3.4.x、Maven、WebFlux SSE、PostgreSQL、Vue 3、MCP。
- 架构图 Mermaid：模型供应商、Agent 图、记忆、Skill 市场、MCP、前端控制台。
- 本地启动步骤：Docker PostgreSQL、后端、前端、本地天气 MCP。
- API/页面入口：`/chat`、`/skills`、`/mcp`、`/settings`。
- 真实验证步骤：模型切换、Skill 启停、MCP 天气、深度研究 SSE、报告和会话历史持久化。
- 安全说明：`.local/` 保存密钥，不提交 API Key；Jar Skill 仅用于本地可信插件。
- Agent Team 章节占位：先说明当前工作流能力，后续 Phase 6 补齐可运行 Demo 后再更新为最终说明。

Skill 开发指南必须包含：

- Prompt Skill 包结构：`skill.json` + `SKILL.md`。
- Jar Skill 包结构：`skill.json` + `plugin.jar` + 可选 `README.md`。
- `SkillPlugin` 接口说明、ServiceLoader 声明方式。
- 参数 schema、版本、依赖、启停、导入、导出、健康检查。
- 本地可信 Jar Skill 的风险说明。

Demo 脚本必须包含：

- 录屏前准备项。
- 3 条基础功能演示路径。
- Agent Team 演示路径。
- 录屏验收清单。

### Tests

```powershell
rg -n "架构图|Skill 开发|MCP|Agent Team|Demo|模型切换|短期记忆|长期记忆" README.md docs
```

### Real Verification

按 README 启动步骤手动执行一遍，记录命令是否可运行。若 Docker Desktop 未启动，README 中必须明确提示先启动 Docker Desktop。

### Acceptance Criteria

- 根目录存在中文 `README.md`。
- README 有 Mermaid 架构图和 Agent Team 流程图。
- `docs/skill-development-guide.md` 足够让新会话按说明创建一个 Prompt Skill 或 Jar Skill。
- `docs/demo-script.md` 可直接用于录制 Demo 视频。
- 文档不编造未实现功能；未完成项必须标注为后续阶段或可选挑战。

### Suggested Commit

`docs: 补齐项目 README 和 Skill 开发指南`

## Phase 2: 短期记忆接入真实推理路径

### Goal

让“短期记忆（对话窗口）”不只停留在 PostgreSQL 保存和接口展示，而是进入模型推理上下文；长期记忆继续由 PostgreSQL 会话消息、用户画像和历史报告承担。

### Main Changes

重点检查并修改：

- `src/main/java/top/lanshan/manmu/memory/ConversationMemoryService.java`
- `src/main/java/top/lanshan/manmu/memory/PostgresConversationMemoryService.java`
- `src/main/java/top/lanshan/manmu/runner/GraphResearchRunner.java`
- `src/main/java/top/lanshan/manmu/model/ResearchState.java`
- `src/main/java/top/lanshan/manmu/node/CoordinatorNode.java`
- `src/main/java/top/lanshan/manmu/node/PlannerNode.java`
- `src/main/java/top/lanshan/manmu/node/ReporterNode.java`
- `src/main/java/top/lanshan/manmu/agent/LlmCoordinatorAgent.java`
- `src/main/java/top/lanshan/manmu/agent/LlmPlannerAgent.java`
- `src/main/java/top/lanshan/manmu/agent/LlmReporterAgent.java`

推荐实现：

- 在 `ResearchState` 增加 `conversationHistoryContext` 字段。
- 在 `GraphResearchRunner.runChat()` 和 `runUntilPlanGate()` 保存当前用户消息后，读取 `ConversationMemoryService.formatConversationHistory(sessionId)`，写入 `ResearchState`。
- 将短期记忆传给 Coordinator / Planner / Reporter 的 prompt section。
- Prompt 中明确：短期记忆用于理解上下文和偏好，不得把历史内容当成外部事实证据。
- 保持异常降级：记忆读取失败不阻断主流程，但要写日志和测试覆盖。

### Tests

新增或更新：

- `src/test/java/top/lanshan/manmu/runner/GraphResearchRunnerTest.java`
- `src/test/java/top/lanshan/manmu/agent/LlmCoordinatorAgentTest.java`
- `src/test/java/top/lanshan/manmu/agent/LlmPlannerAgentTest.java`
- `src/test/java/top/lanshan/manmu/agent/LlmReporterAgentTest.java`
- `src/test/java/top/lanshan/manmu/memory/PostgresConversationMemoryServiceTest.java`

命令：

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=GraphResearchRunnerTest,PostgresConversationMemoryServiceTest,LlmCoordinatorAgentTest,LlmPlannerAgentTest,LlmReporterAgentTest' test
mvn test
```

### Real Verification

```powershell
docker compose up -d postgres
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18080" *> target/memory-run.log
```

另开 PowerShell：

```powershell
New-Item -ItemType Directory -Force target/http-check | Out-Null
'{"message":"我正在学习 Java Agent 项目，请记住我偏好简洁中文回答。","session_id":"memory-demo","enable_deepresearch":false}' | Set-Content -Encoding UTF8 target/http-check/memory-1.json
curl.exe -N -H "Content-Type: application/json" --data-binary "@target/http-check/memory-1.json" http://localhost:18080/chat/stream > target/http-check/memory-1.sse

'{"message":"刚才我说我偏好什么风格？","session_id":"memory-demo","enable_deepresearch":false}' | Set-Content -Encoding UTF8 target/http-check/memory-2.json
curl.exe -N -H "Content-Type: application/json" --data-binary "@target/http-check/memory-2.json" http://localhost:18080/chat/stream > target/http-check/memory-2.sse

curl.exe http://localhost:18080/api/conversations/memory-demo > target/http-check/memory-conversation.json
```

验证后关闭后端，并确认端口释放：

```powershell
Get-Content target/http-check/memory-2.sse
Get-Content target/http-check/memory-conversation.json
Get-Process -Id (Get-Content target/backend.pid) -ErrorAction SilentlyContinue | Stop-Process
Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue
```

### Acceptance Criteria

- 第二轮对话能基于同一 `session_id` 回忆短期偏好。
- `conversation_messages` 可查到 USER / ASSISTANT 消息。
- 记忆读取失败不会导致 `/chat/stream` 失败。
- README 更新短期/长期记忆说明。

### Suggested Commit

`feat: 将短期会话记忆接入 Agent 推理上下文`

## Phase 3: 补齐 3 个方向 Skill 演示

### Goal

让 Skill 市场清晰满足“至少 3 个不同方向 Skill 演示”，并在控制台可管理、可验证。

### Main Changes

新增或调整内置 Skill：

- `src/main/java/top/lanshan/manmu/skill/content/weather-now/`：保留，作为天气 Skill，依赖本地和风天气 MCP。
- `src/main/java/top/lanshan/manmu/skill/content/calculator/`：新增计算器 Skill。
- `src/main/java/top/lanshan/manmu/skill/content/web-search/`：新增网络搜索 Skill。

推荐方案：

- `calculator` 第一版使用 Prompt Skill，要求模型输出计算步骤和结果；若想更稳，可在 Phase 4 通过 Jar Skill Demo 做确定性计算器。
- `web-search` 使用现有真实搜索能力或提示模型调用可用工具，不引入 mock 搜索；如当前搜索能力不作为 Spring AI tool 暴露，则在 Skill 文档中明确它是“研究模式网络搜索任务模板”，并在 README 中如实说明。
- 每个 Skill 的 `skill.json` 包含：`name`、`description`、`version`、`enabled`、`parameters`、`dependencies`、`category`、`tags`。
- 前端 `/skills` 不需要大改，但要确认新 Skill 分类、参数、健康状态显示正确。

可能涉及：

- `src/main/java/top/lanshan/manmu/skill/service/SkillRegistry.java`
- `src/main/java/top/lanshan/manmu/skill/service/SkillHealthService.java`
- `ui-vue3/src/views/skills/skillMarket.ts`
- `ui-vue3/src/views/chat/skillPicker.ts`

### Tests

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=SkillRegistryTest,SkillServiceTest,SkillToolProviderTest,SkillHealthServiceTest' test
cd ui-vue3
npm run test:unit -- skillMarket.spec.ts skillPicker.spec.ts skillForm.spec.ts
```

### Real Verification

启动后端后：

```powershell
curl.exe http://localhost:18080/api/skills > target/http-check/skills.json
curl.exe http://localhost:18080/api/skills/weather-now/health > target/http-check/skill-weather-health.json
curl.exe http://localhost:18080/api/skills/calculator/health > target/http-check/skill-calculator-health.json
curl.exe http://localhost:18080/api/skills/web-search/health > target/http-check/skill-web-search-health.json
```

用真实聊天链路验证显式 Skill：

```powershell
'{"message":"@calculator 计算 (128 + 256) * 3 / 6","session_id":"skill-demo","enable_deepresearch":false}' | Set-Content -Encoding UTF8 target/http-check/calculator.json
curl.exe -N -H "Content-Type: application/json" --data-binary "@target/http-check/calculator.json" http://localhost:18080/chat/stream > target/http-check/calculator.sse

'{"message":"@web-search 搜索并总结 Spring Boot 3 WebFlux SSE 的关键注意事项","session_id":"skill-demo","enable_deepresearch":true,"auto_accepted_plan":true}' | Set-Content -Encoding UTF8 target/http-check/web-search.json
curl.exe -N -H "Content-Type: application/json" --data-binary "@target/http-check/web-search.json" http://localhost:18080/chat/stream > target/http-check/web-search.sse
```

### Acceptance Criteria

- `/api/skills` 至少返回天气、计算器、网络搜索 3 个不同方向 Skill。
- 每个 Skill 都有完整元数据、参数 schema、版本和依赖字段。
- `/skills` 页面能展示、筛选、查看健康状态。
- 显式 `@calculator` 和 `@web-search` 能走真实聊天或深度研究路径。
- 不引入 mock agent 或 mock search fallback。

### Suggested Commit

`feat: 补齐天气计算器和网络搜索 Skill 演示`

## Phase 4: Jar Skill 热加载演示与控制台收口

### Goal

让“用户上传 Jar/配置文件并热加载，注意类加载器隔离”从代码能力变成可验收演示能力，同时保持默认安全姿态。

### Main Changes

后端重点：

- `src/main/java/top/lanshan/manmu/skill/plugin/*`
- `src/main/java/top/lanshan/manmu/skill/service/SkillService.java`
- `src/main/java/top/lanshan/manmu/skill/service/SkillController.java`
- `src/main/resources/application.yml`

前端重点：

- `ui-vue3/src/views/skills/index.vue`
- `ui-vue3/src/services/api/skills.ts`
- `ui-vue3/src/views/skills/skillMarket.ts`

文档重点：

- `docs/skill-development-guide.md`
- `README.md`
- `docs/demo-script.md`

推荐实现：

- 保持默认 `mvp.skill.jar-plugins.enabled=false`。
- 在 README 和控制台提示：Jar Skill 仅本地可信启用。
- 提供 `target/demo-packages/` 生成脚本或测试支持说明，不提交生成产物。
- 确认前端上传 Jar 包失败时能显示“Jar Skill plugins are disabled”，启用后可上传、reload、toggle、uninstall。
- 如当前前端只支持导入入口但提示不清晰，补 UI 文案和状态展示。

### Tests

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=SkillServiceTest,SkillControllerTest,SkillToolProviderTest,SkillPackageArchiveServiceTest' test
cd ui-vue3
npm run test:unit -- skillMarket.spec.ts skillForm.spec.ts
```

### Real Verification

启用 Jar 插件启动后端：

```powershell
docker compose up -d postgres
$env:MVP_SKILL_JAR_PLUGINS_ENABLED='true'
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18080 --mvp.skill.jar-plugins.enabled=true" *> target/jar-skill-run.log
```

使用测试支持或临时构建一个 echo/calculator Jar Skill zip，验证：

```powershell
curl.exe -F "file=@target/demo-packages/echo-json-skill.zip" http://localhost:18080/api/skills/packages/import-jar > target/http-check/jar-import.json
curl.exe http://localhost:18080/api/skills/echo-json-skill > target/http-check/jar-detail.json
curl.exe -X POST http://localhost:18080/api/skills/echo-json-skill/reload > target/http-check/jar-reload.json
curl.exe -X PATCH http://localhost:18080/api/skills/echo-json-skill/toggle > target/http-check/jar-toggle-off.json
curl.exe -X PATCH http://localhost:18080/api/skills/echo-json-skill/toggle > target/http-check/jar-toggle-on.json

'{"message":"@echo-json-skill --message=JarSkillWorks 请基于 Jar Skill 返回作答","session_id":"jar-demo","enable_deepresearch":false}' | Set-Content -Encoding UTF8 target/http-check/jar-chat.json
curl.exe -N -H "Content-Type: application/json" --data-binary "@target/http-check/jar-chat.json" http://localhost:18080/chat/stream > target/http-check/jar-chat.sse

curl.exe -X DELETE http://localhost:18080/api/skills/packages/echo-json-skill
```

### Acceptance Criteria

- 默认关闭 Jar 上传热加载，启用后可完成导入、加载、调用、停用、重载、卸载。
- 每个 Jar Skill 通过独立 `SkillPluginClassLoader` 加载，并在卸载/停用时释放。
- 控制台能清楚显示 Jar Skill 状态和错误原因。
- README 和 Skill 指南说明可信本地边界。

### Suggested Commit

`feat: 完善可信 Jar Skill 热加载演示`

## Phase 5: MCP 示例与管理验收加固

### Goal

确保 MCP 兼容能力不仅有代码，还有可复现的外部工具发现、连接测试、工具调用和控制台调试流程。

### Main Changes

重点文件：

- `tools/local-qweather-mcp/README.md`
- `docs/mcp-tools.md`
- `src/main/resources/mcp-config.json`
- `src/main/java/top/lanshan/manmu/mcp/*`
- `ui-vue3/src/views/mcp/*`
- `ui-vue3/src/services/api/app.ts`

推荐实现：

- 修复所有 MCP 相关中文乱码文档。
- 在 README 中明确至少一个 MCP 示例：本地和风天气 `weather_now`。
- 在 `/mcp` 页面展示工具名称、连接状态、调试入口、失败排查步骤。
- 可选增加一个轻量本地文件系统 MCP 示例，仅限安全目录，例如 `target/mcp-demo-files`，但本阶段不是硬要求；若实现，必须避免任意文件读写风险。

### Tests

```powershell
cd tools/local-qweather-mcp
npm test
npm run build
cd ..\..\ui-vue3
npm run test:unit -- mcpTools.spec.ts
cd ..
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=McpToolProviderTest,McpToolInvocationServiceTest,McpStatusControllerTest,McpServerConfigServiceTest,McpConfigMergeUtilTest' test
```

### Real Verification

```powershell
cd tools/local-qweather-mcp
npm run build
node dist/index.js *> ..\..\target\local-qweather-mcp.log
```

另开后端并验证：

```powershell
curl.exe http://localhost:18080/api/mcp/status > target/http-check/mcp-status.json
curl.exe http://localhost:18080/api/mcp/servers > target/http-check/mcp-servers.json
curl.exe -X POST http://localhost:18080/api/mcp/reload > target/http-check/mcp-reload.json
'{"location":"上海"}' | Set-Content -Encoding UTF8 target/http-check/weather-now-request.json
curl.exe -X POST -H "Content-Type: application/json" --data-binary "@target/http-check/weather-now-request.json" http://localhost:18080/api/mcp/tools/weather_now/invoke > target/http-check/weather-now-result.json
```

### Acceptance Criteria

- `weather_now` 能被后端发现并调用。
- `/mcp` 页面能管理/测试 MCP Server。
- 文档说明 `.local/mcp-keys.json` 或环境变量配置方式，且不泄露 Key。
- 若天气供应商限流或无 Key，错误信息清楚，不编造天气。

### Suggested Commit

`docs: 加固 MCP 示例说明和真实验证流程`

## Phase 6: Agent Team 协作 Demo

### Goal

补齐可选高分项的最小可运行版本：多个角色协作完成“策划一场活动”的 Demo，并能在 SSE/前端/README 中展示协作流程。

### Main Changes

优先复用现有图工作流，不做大重写。

后端候选文件：

- `src/main/java/top/lanshan/manmu/graph/ResearchGraphBuilder.java`
- `src/main/java/top/lanshan/manmu/node/ResearchTeamNode.java`
- `src/main/java/top/lanshan/manmu/node/ParallelExecutorNode.java`
- `src/main/java/top/lanshan/manmu/model/ResearchEvent.java`
- `src/main/java/top/lanshan/manmu/model/ResearchStep.java`
- `src/main/java/top/lanshan/manmu/model/StepType.java`
- `src/main/resources/prompts/planner.md`
- `src/main/resources/prompts/researcher.md`
- `src/main/resources/prompts/processor.md`
- `src/main/resources/prompts/reporter.md`

前端候选文件：

- `ui-vue3/src/store/MessageStore.ts`
- `ui-vue3/src/components/plan-review/index.vue`
- `ui-vue3/src/views/chat/index.vue`

文档：

- `README.md`
- `docs/demo-script.md`

推荐实现：

- 不新建庞大的 Agent Team 框架，先把现有节点命名和事件展示成 Planner / Executor / Reviewer 角色协作。
- 增加一个“活动策划”Demo 请求模板，例如：
  `帮我策划一个周六下午在上海适合 20 人的技术读书会活动，考虑天气、场地、流程和风险。`
- Planner 将任务拆成天气查询、场地/交通查询、流程设计、风险审查等步骤。
- Executor 由 `researcher_0`、`researcher_1`、`coder_0` 或现有执行节点处理。
- Reviewer 可复用 Reporter 或新增轻量 `reviewer` 阶段，负责检查遗漏和汇总。
- SSE 事件中保留 `node_name`、`node_type`、`step.assigned`、`step.completed`，前端时间线可读。
- README Mermaid 展示协作流程。
- 如果要体现“LLM 动态决策”，在 planner/coordinator prompt 和测试中说明分支由 LLM 输出计划和步骤类型决定，不是固定脚本。

### Tests

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=ResearchGraphBuilderTest,ResearchTeamNodeTest,ParallelExecutorNodeTest,GraphResearchRunnerTest,PlannerOutputMapperTest' test
cd ui-vue3
npm run test:unit -- MessageStore.spec.ts planReview.spec.ts
```

如果没有 `ResearchGraphBuilderTest`，先用现有 `GraphResearchRunnerTest` 和 `ResearchTeamNodeTest` 覆盖，不为了名字新建空壳测试。

### Real Verification

```powershell
'{"message":"帮我策划一个周六下午在上海适合 20 人的技术读书会活动，考虑天气、场地、流程和风险。","session_id":"team-demo","enable_deepresearch":true,"auto_accepted_plan":true}' | Set-Content -Encoding UTF8 target/http-check/team-demo.json
curl.exe -N -H "Content-Type: application/json" --data-binary "@target/http-check/team-demo.json" http://localhost:18080/chat/stream > target/http-check/team-demo.sse
```

检查：

```powershell
rg "coordinator|planner|research_team|parallel_executor|researcher_|reporter|step.assigned|done" target/http-check/team-demo.sse
curl.exe http://localhost:18080/api/sessions/team-demo/history > target/http-check/team-history.json
curl.exe http://localhost:18080/api/reports/session/team-demo > target/http-check/team-reports.json
```

### Acceptance Criteria

- 一条真实 SSE 流展示 Planner 拆解、Executor 执行、Reviewer/Reporter 汇总。
- 活动策划 Demo 最终输出完整方案，包含天气/场地/流程/风险。
- 前端聊天页能展示计划步骤和执行状态。
- README 包含 Agent Team Mermaid 流程图和设计说明。
- 报告和会话历史在 PostgreSQL 中可读取，线程状态为 `COMPLETED`。

### Suggested Commit

`feat: 补齐 Agent Team 协作演示流程`

## Phase 7: 端到端验收脚本与 Demo 录制清单

### Goal

形成最终验收闭环：从干净启动到录制 Demo，每个需求点都有证据文件或可重复命令。

### Main Changes

新增：

- `docs/acceptance-checklist.md`
- `docs/demo-script.md` 最终版更新

可选新增脚本：

- `scripts/check-acceptance.ps1`

脚本原则：

- 不读取或输出 `.local/model-providers.json` 的 Key。
- 请求 JSON 写入 `target/http-check/*.json`，避免 PowerShell 内联转义问题。
- 启动后端时记录 PID 到 `target/backend.pid`。
- 每次验证后关闭服务并确认 `18080` 端口释放。

### Tests

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn test
cd ui-vue3
npm run test
cd ..\tools\local-qweather-mcp
npm test
```

### Real Verification

完整验收顺序：

1. 启动 Docker PostgreSQL。
2. 启动本地和风天气 MCP。
3. 启动后端 `18080`。
4. 启动前端。
5. 验证 `/api/app/capabilities`。
6. 验证 `/api/model/providers` 和 `/api/model/current`。
7. 切换一次模型或执行 `/api/model/test`。
8. 验证 `/api/skills` 返回 3 个演示 Skill。
9. 验证 Prompt Skill 导入/导出/启停。
10. 在可信启用模式下验证 Jar Skill 导入/调用/卸载。
11. 验证 `/api/mcp/status` 和 `weather_now` 调用。
12. 验证短期记忆二轮对话。
13. 验证 Agent Team 活动策划 SSE。
14. 验证报告、会话历史、事件历史持久化。
15. 关闭后端和本地 MCP，确认端口释放。

### Acceptance Criteria

- `docs/acceptance-checklist.md` 能逐项映射原始需求。
- Demo 录制脚本覆盖基础功能；若实现 Agent Team，也覆盖协作全过程。
- 所有验证输出保存到 `target/http-check/`，不纳入提交。
- 最终无后端或 MCP 残留进程占用端口。

### Suggested Commit

`docs: 增加最终验收清单和 Demo 脚本`

## Acceptance Checklist

- [ ] 根目录 `README.md` 存在，包含架构图、启动步骤、Skill 开发指南入口和 Agent Team 流程图。
- [ ] `docs/skill-development-guide.md` 说明 Prompt Skill 和 Jar Skill 开发、打包、导入、启停、卸载。
- [ ] `docs/demo-script.md` 能指导录制基础功能和 Agent Team 视频。
- [ ] 模型供应商列表、Key 保存、运行时切换、模型测试接口可用。
- [ ] 短期会话窗口进入模型推理上下文，长期记忆由 PostgreSQL 消息、用户画像和历史报告体现。
- [ ] Skill 市场支持注册、发现、启用/禁用、导入/导出、卸载、健康检查和调用历史。
- [ ] 至少有天气、计算器、网络搜索 3 个方向 Skill 演示。
- [ ] Jar Skill 默认关闭但可通过本地可信配置启用，并可上传热加载、调用、重载、停用、卸载。
- [ ] MCP 支持服务发现、连接测试、reload、工具调用，至少本地 `weather_now` 示例可运行。
- [ ] Vue 控制台能展示 Chat、Skill、MCP、Settings 主要管理页面。
- [ ] Agent Team Demo 能展示 Planner / Executor / Reviewer 或等价角色协作。
- [ ] PostgreSQL 持久化可通过报告、会话历史、事件历史接口验证。
- [ ] 所有测试通过：后端、前端、本地 MCP。
- [ ] 真实 HTTP/SSE 验证完成后关闭服务并释放端口。
- [ ] 没有提交 `.local/`、`target/`、`.idea/`、`.claude/` 或任何 API Key。

## Recommended Execution Order

1. Phase 0：先做基线审计，确认 Docker Desktop 和端口状态。
2. Phase 1：优先补 README 和文档，降低交付风险。
3. Phase 2：接入短期记忆，这是核心功能缺口。
4. Phase 3：补 3 个方向 Skill 演示，让基础需求闭环。
5. Phase 5：加固 MCP 示例和文档，天气 Skill 依赖它。
6. Phase 4：补 Jar Skill 热加载演示，风险较高，单独做。
7. Phase 6：补 Agent Team 高分项，可在基础验收稳定后做。
8. Phase 7：最终验收和 Demo 录制清单。

每完成一个 Phase 后：

```powershell
git status --short
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn test
```

涉及前端时额外执行：

```powershell
cd ui-vue3
npm run test:unit
npm run build
```

涉及本地 MCP 时额外执行：

```powershell
cd tools/local-qweather-mcp
npm test
npm run build
```

涉及运行时行为时必须执行真实服务验证，并在结束后关闭后端：

```powershell
Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue
```
