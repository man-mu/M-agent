# M-Agent

M-Agent 以 DeepResearch 风格的工作流为核心，展示模型供应商切换、WebFlux SSE 流式输出、PostgreSQL 持久化、Skill 市场、MCP 工具接入和研究流程编排。


## 技术栈

- 后端：Java 17、Spring Boot 3.4.x、Maven、WebFlux、R2DBC、Flyway
- Agent 编排：Spring AI、Spring AI Alibaba Graph
- 模型：DashScope 与 OpenAI-compatible provider，例如 DeepSeek、MiniMax、Moonshot、智谱等
- 持久化：PostgreSQL，包含报告、会话历史、事件历史、对话消息、用户画像
- 工具与扩展：Skill 市场、本地 Jar Skill 插件接口、MCP Client
- 前端：Vue 3、Vite、Pinia、Ant Design Vue
- 本地 MCP 示例：`tools/local-qweather-mcp`，提供 `weather_now` 实时天气工具


## 架构图

```mermaid
flowchart LR
  User["用户 / 浏览器"] --> UI["Vue 控制台<br/>/chat /skills /mcp /settings"]
  UI --> ChatApi["Chat SSE API<br/>/chat/stream"]
  UI --> AdminApi["管理 API<br/>/api/model /api/skills /api/mcp"]

  ChatApi --> Runner["GraphResearchRunner"]
  Runner --> Graph["Spring AI Alibaba Graph"]
  Graph --> Coordinator["Coordinator"]
  Graph --> Planner["Planner"]
  Graph --> Team["Research Team"]
  Team --> Executor["Parallel Executor"]
  Executor --> Researcher["Researcher / Coder"]
  Researcher --> Reporter["Reporter"]

  Coordinator --> ModelRouter["RoutingChatModel"]
  Planner --> ModelRouter
  Researcher --> ModelRouter
  Reporter --> ModelRouter
  ModelRouter --> Providers["模型供应商<br/>DashScope / DeepSeek / OpenAI-compatible"]

  Researcher --> SkillTools["Skill ToolProvider"]
  SkillTools --> SkillMarket["Skill 市场<br/>Prompt Skill / Jar Skill"]
  Researcher --> McpTools["MCP ToolProvider"]
  McpTools --> McpServers["MCP Servers<br/>local-qweather / amap"]

  Runner --> Pg["PostgreSQL"]
  Pg --> Reports["报告 / 会话历史 / 事件历史"]
  Pg --> Memory["短期对话窗口 / 用户画像"]
```

## Agent 工作流

```mermaid
flowchart TD
  Start["收到用户问题"] --> Coordinator["Coordinator<br/>判断快速回答或深度研究"]
  Coordinator -->|快速回答| Done["直接输出"]
  Coordinator -->|深度研究| Rewrite["Query Rewrite"]
  Rewrite --> Background["Background Investigator<br/>可触发真实搜索"]
  Background --> Planner["Planner<br/>生成研究计划"]
  Planner --> Validator["Plan Validator"]
  Validator -->|需要修改| Planner
  Validator -->|可执行| Info["Information / 上下文整理"]
  Info --> Team["Research Team<br/>分配待执行步骤"]
  Team --> Executor["Parallel Executor<br/>分发给 researcher_n / coder_n"]
  Executor --> Researcher["Researcher / Coder<br/>执行步骤并调用 Skill/MCP"]
  Researcher --> Team
  Team -->|全部完成| Reporter["Reporter<br/>汇总报告"]
  Reporter --> Persist["保存报告与历史"]
  Persist --> Done
```

后续 Agent Team 高分 Demo 将在此基础上补齐“活动策划”场景，把 Planner / Executor / Reviewer 的角色、任务分发和状态流展示得更清晰。

## 本地启动

### 1. 启动 PostgreSQL

先确认 Docker Desktop 已启动，然后在仓库根目录执行：

```powershell
docker compose up -d postgres
docker ps --format "{{.Names}}\t{{.Status}}\t{{.Ports}}"
```

期望看到 `manmu-postgres` 为 `healthy`，并映射 `5432`。

### 2. 配置模型 Key

不要把 Key 写入源码或提交记录。推荐通过控制台或接口保存：

```powershell
curl.exe -X POST http://localhost:18080/api/model/providers/deepseek/key `
  -H "Content-Type: application/json" `
  --data-binary "{\"apiKey\":\"你的 Key\"}"
```

本地敏感配置位于 `.local/`，该目录已被 `.gitignore` 忽略。

### 3. 启动本地和风天气 MCP

如需演示 `weather-now` Skill 或 `weather_now` MCP 工具，先配置和风天气 Key：

```powershell
New-Item -ItemType Directory -Force .local | Out-Null
'{"QWEATHER_API_KEY":"你的和风天气 Key"}' | Set-Content -Encoding UTF8 .local/mcp-keys.json
```

启动 MCP Server：

```powershell
cd tools/local-qweather-mcp
npm install
npm run build
npm start
```

默认监听 `http://127.0.0.1:18090/sse`。

### 4. 启动后端

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18080"
```

Jar Skill 默认关闭。仅在本地可信演示时临时启用：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18080 --mvp.skill.jar-plugins.enabled=true"
```

### 5. 启动前端

```powershell
cd ui-vue3
npm install
npm run dev
```

常用页面：

- `http://localhost:5173/chat`
- `http://localhost:5173/skills`
- `http://localhost:5173/mcp`
- `http://localhost:5173/settings`

## 常用验证

### 短期记忆与长期记忆

短期记忆来自同一 `session_id` 下最近的对话消息。后端会在保存当前用户消息后读取会话窗口，并将其传入 Coordinator、Planner 和 Reporter 的 prompt，用于理解上下文、追问和偏好；prompt 中明确要求不要把历史对话当成外部事实证据。

长期记忆由 PostgreSQL 中的会话消息、用户画像、历史报告和事件历史体现，可通过会话历史、报告和对话接口验证。

```powershell
New-Item -ItemType Directory -Force target/http-check | Out-Null
'{"query":"我偏好简洁中文回答，请记住。","session_id":"memory-demo","enable_deepresearch":false}' | Set-Content -Encoding UTF8 target/http-check/memory-1.json
curl.exe -N -H "Content-Type: application/json" --data-binary "@target/http-check/memory-1.json" http://localhost:18080/chat/stream > target/http-check/memory-1.sse

'{"query":"刚才我说我偏好什么风格？","session_id":"memory-demo","enable_deepresearch":false}' | Set-Content -Encoding UTF8 target/http-check/memory-2.json
curl.exe -N -H "Content-Type: application/json" --data-binary "@target/http-check/memory-2.json" http://localhost:18080/chat/stream > target/http-check/memory-2.sse

curl.exe http://localhost:18080/api/conversations/memory-demo > target/http-check/memory-conversation.json
```

### 能力开关

```powershell
curl.exe http://localhost:18080/api/app/capabilities
```

### 模型供应商与切换

```powershell
curl.exe http://localhost:18080/api/model/providers
curl.exe http://localhost:18080/api/model/current
'{"providerId":"deepseek","modelName":"deepseek-chat"}' | Set-Content -Encoding UTF8 target/http-check/model-switch.json
curl.exe -X POST -H "Content-Type: application/json" --data-binary "@target/http-check/model-switch.json" http://localhost:18080/api/model/switch
```

### Skill 市场

```powershell
curl.exe http://localhost:18080/api/skills
curl.exe http://localhost:18080/api/skills/weather-now/health
curl.exe http://localhost:18080/api/skills/calculator/health
curl.exe http://localhost:18080/api/skills/web-search/health
curl.exe -X PATCH http://localhost:18080/api/skills/weather-now/toggle
curl.exe -X PATCH http://localhost:18080/api/skills/weather-now/toggle
```

### MCP 状态和天气工具

```powershell
curl.exe http://localhost:18080/api/mcp/status
curl.exe http://localhost:18080/api/mcp/servers
curl.exe -X POST http://localhost:18080/api/mcp/servers/mcp-qweather/test
curl.exe -X POST http://localhost:18080/api/mcp/reload
'{"location":"上海"}' | Set-Content -Encoding UTF8 target/http-check/weather-now-request.json
curl.exe -X POST -H "Content-Type: application/json" --data-binary "@target/http-check/weather-now-request.json" http://localhost:18080/api/mcp/tools/weather_now/invoke
```

### Jar Skill 热加载演示

Jar Skill 默认关闭。控制台 `/skills` 会展示 Jar 插件开关状态；默认关闭时上传 Jar 包会返回 `Jar Skill plugins are disabled`。生成演示包：

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=JarSkillDemoPackageGeneratorTest' '-Dmvp.demo.jar-skill-package=true' test
```

启用可信本地 Jar 插件后启动后端，再验证导入、健康、重载、启停和卸载：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18080 --mvp.skill.jar-plugins.enabled=true"
curl.exe -F "file=@target/demo-packages/echo-json-skill.zip" http://localhost:18080/api/skills/packages/import-jar > target/http-check/jar-import.json
curl.exe http://localhost:18080/api/skills/echo-json-skill > target/http-check/jar-detail.json
curl.exe http://localhost:18080/api/skills/echo-json-skill/health > target/http-check/jar-health.json
curl.exe -X POST http://localhost:18080/api/skills/echo-json-skill/reload > target/http-check/jar-reload.json
curl.exe -X PATCH http://localhost:18080/api/skills/echo-json-skill/toggle > target/http-check/jar-toggle-off.json
curl.exe -X PATCH http://localhost:18080/api/skills/echo-json-skill/toggle > target/http-check/jar-toggle-on.json
curl.exe -X DELETE http://localhost:18080/api/skills/packages/echo-json-skill
```

### 聊天 SSE

```powershell
New-Item -ItemType Directory -Force target/http-check | Out-Null
'{"query":"@weather-now 查询上海实时天气","session_id":"demo-session","enable_deepresearch":false}' | Set-Content -Encoding UTF8 target/http-check/chat-weather.json
curl.exe -N -H "Content-Type: application/json" --data-binary "@target/http-check/chat-weather.json" http://localhost:18080/chat/stream > target/http-check/chat-weather.sse

'{"query":"@calculator 计算 (128 + 256) * 3 / 6","session_id":"demo-session","enable_deepresearch":false}' | Set-Content -Encoding UTF8 target/http-check/chat-calculator.json
curl.exe -N -H "Content-Type: application/json" --data-binary "@target/http-check/chat-calculator.json" http://localhost:18080/chat/stream > target/http-check/chat-calculator.sse

'{"query":"@web-search 搜索并总结 Spring Boot 3 WebFlux SSE 的关键注意事项","session_id":"demo-session","enable_deepresearch":true,"auto_accepted_plan":true}' | Set-Content -Encoding UTF8 target/http-check/chat-web-search.json
curl.exe -N -H "Content-Type: application/json" --data-binary "@target/http-check/chat-web-search.json" http://localhost:18080/chat/stream > target/http-check/chat-web-search.sse
```

### 深度研究 SSE 与持久化

```powershell
'{"query":"解释为什么 Agent 工作流要区分 Planner、Researcher 和 Reporter。","session_id":"research-demo","enable_deepresearch":true,"auto_accepted_plan":true}' | Set-Content -Encoding UTF8 target/http-check/research.json
curl.exe -N -H "Content-Type: application/json" --data-binary "@target/http-check/research.json" http://localhost:18080/chat/stream > target/http-check/research.sse
curl.exe http://localhost:18080/api/sessions/research-demo/history > target/http-check/research-history.json
curl.exe http://localhost:18080/api/reports/session/research-demo > target/http-check/research-reports.json
```

## 测试

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

本地 MCP：

```powershell
cd tools/local-qweather-mcp
npm test
npm run build
```

## Skill 开发

详见 [Skill 开发指南](docs/skill-development-guide.md)。

当前支持两类 Skill：

- Prompt Skill：`skill.json + SKILL.md`，适合模板化任务说明。
- Jar Skill：`skill.json + plugin.jar`，通过 `SkillPlugin` 和 `ServiceLoader` 接入，默认关闭，仅用于本地可信插件。

Jar Skill 不是安全沙箱。它通过独立 `SkillPluginClassLoader` 做依赖和生命周期隔离，但仍然只能上传自己编译、可审计、可信的本地 Jar 包。

## MCP 配置

详见 [MCP 工具配置](docs/mcp-tools.md)。

内置配置位于 `src/main/resources/mcp-config.json`：

- 高德 MCP：ID 为 `mcp-amap`，默认关闭，需要 `AMAP_MAPS_API_KEY`。
- 本地和风天气 MCP：ID 为 `mcp-qweather`，默认启用，连接 `http://127.0.0.1:18090/sse`，需要启动 `tools/local-qweather-mcp`。

## Demo 录制

详见 [Demo 脚本](docs/demo-script.md)。建议录制以下路径：

1. 模型供应商查看和切换。
2. Skill 市场查看、健康检查、启停和显式调用。
3. MCP 页面连接测试和 `weather_now` 调试。
4. 深度研究 SSE 过程、报告与会话历史持久化。
5. Agent Team 活动策划流程（Phase 6 完成后录制）。

## 安全与提交约束

- 不提交 `.local/`、`target/`、`.idea/`、`.claude/`。
- 不在源码、测试断言、README 示例输出或提交记录中写入 API Key。
- Jar Skill 不是安全沙箱。ClassLoader 隔离只用于本地可信插件的依赖和生命周期隔离。
- 真实 HTTP/SSE 验证后必须关闭后端服务，并确认 `18080` 端口释放。
