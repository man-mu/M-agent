# Demo 录制脚本

本文用于录制 M-Agent 学习项目 Demo。录制时只展示真实服务、真实数据库、真实模型和真实 MCP 路径，不展示 `.local/` 中的密钥内容。

## 录制前准备

1. 启动 Docker Desktop。
2. 确认 PostgreSQL 可启动：

```powershell
docker compose up -d postgres
docker ps --format "{{.Names}}\t{{.Status}}\t{{.Ports}}"
```

3. 准备模型 Key。可通过 `/api/model/providers/{providerId}/key` 保存，不录制 Key 明文。
4. 如录制天气 MCP，准备 `.local/mcp-keys.json`，但不要打开或展示文件内容。
5. 确认端口：

```powershell
Get-NetTCPConnection -LocalPort 5432 -ErrorAction SilentlyContinue
Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue
Get-NetTCPConnection -LocalPort 18090 -ErrorAction SilentlyContinue
```

## 启动顺序

### 本地天气 MCP

```powershell
cd tools/local-qweather-mcp
npm run build
npm start
```

### 后端

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18080"
```

### 前端

```powershell
cd ui-vue3
npm run dev
```

打开：

- `http://localhost:5173/chat`
- `http://localhost:5173/skills`
- `http://localhost:5173/mcp`
- `http://localhost:5173/settings`

## 片段 1：模型配置与切换

目标：展示多模型供应商配置和运行时切换。

录制步骤：

1. 打开 `/settings` 或模型设置入口。
2. 展示供应商列表，例如 DashScope、DeepSeek、MiniMax、Moonshot、智谱。
3. 展示当前模型。
4. 切换到一个已配置 Key 的模型。
5. 执行模型测试或发送一个快速回答。

可用接口证据：

```powershell
curl.exe http://localhost:18080/api/model/providers
curl.exe http://localhost:18080/api/model/current
```

## 片段 2：Skill 市场

目标：展示 Skill 注册、发现、启停、健康检查和显式调用。

录制步骤：

1. 打开 `/skills`。
2. 展示内置 Skill 列表，重点展示 3 个方向：`weather-now` 天气、`calculator` 计算器、`web-search` 网络搜索。
3. 打开 `weather-now`、`calculator`、`web-search` 详情，展示参数 schema、依赖和健康状态。
4. 停用再启用一个本地导入 Skill；内置 Skill 是只读内容目录，不从页面修改。
5. 切到“本地市场”，展示 Prompt Skill Zip 和 Jar Skill Zip 两个导入入口。
6. 如未启用 Jar 插件，上传 Jar Skill Zip 会显示 `Jar Skill plugins are disabled`；如启用了可信本地 Jar 插件，导入 `echo-json-skill.zip`，展示详情、健康检查、重载、停用、启用和卸载。
7. 回到 `/chat`，分别输入：

```text
@weather-now 查询上海实时天气
@calculator 计算 (128 + 256) * 3 / 6
@web-search 搜索并总结 Spring Boot 3 WebFlux SSE 的关键注意事项
@echo-json-skill --message=JarSkillWorks 请基于 Jar Skill 返回作答
```

注意：

- 天气结果必须来自本地 MCP 和真实和风天气 API。
- 计算器是 Prompt Skill，走真实模型链路输出步骤和结果。
- 网络搜索 Skill 是研究模式任务模板，应开启深度研究，让后端使用已有 Bocha 搜索路径；如果缺少 `BOCHA_API_KEY`，录制清晰错误提示。
- Jar Skill 默认关闭，只在本地可信演示时通过 `--mvp.skill.jar-plugins.enabled=true` 启用；ClassLoader 隔离不是安全沙箱。
- 如果天气 Key 不可用或供应商限流，可以录制清晰错误提示，不要编造结果。

Jar Skill 演示包生成和接口证据：

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=JarSkillDemoPackageGeneratorTest' '-Dmvp.demo.jar-skill-package=true' test
curl.exe -F "file=@target/demo-packages/echo-json-skill.zip" http://localhost:18080/api/skills/packages/import-jar
curl.exe http://localhost:18080/api/skills/echo-json-skill/health
curl.exe -X POST http://localhost:18080/api/skills/echo-json-skill/reload
curl.exe -X PATCH http://localhost:18080/api/skills/echo-json-skill/toggle
curl.exe -X PATCH http://localhost:18080/api/skills/echo-json-skill/toggle
curl.exe -X DELETE http://localhost:18080/api/skills/packages/echo-json-skill
```

## 片段 3：MCP 管理与工具调试

目标：展示 MCP 服务发现、连接测试、reload 和工具调用。

录制步骤：

1. 打开 `/mcp`。
2. 展示本地和风天气 MCP 状态。
3. 点击连接测试。
4. 打开 `weather_now` 调试输入，填入：

```json
{
  "location": "上海"
}
```

5. 展示真实工具返回或清晰错误信息。

可用接口证据：

```powershell
curl.exe http://localhost:18080/api/mcp/status
curl.exe http://localhost:18080/api/mcp/servers
curl.exe -X POST http://localhost:18080/api/mcp/servers/mcp-qweather/test
curl.exe -X POST http://localhost:18080/api/mcp/reload
'{"location":"上海","lang":"zh","unit":"m"}' | Set-Content -Encoding UTF8 target/http-check/weather-now-request.json
curl.exe -X POST -H "Content-Type: application/json" --data-binary "@target/http-check/weather-now-request.json" http://localhost:18080/api/mcp/tools/weather_now/invoke
```

## 片段 4：深度研究工作流

目标：展示 Planner / Researcher / Reporter 的 SSE 流程和 PostgreSQL 持久化。

录制输入：

```text
解释为什么 Agent 工作流要区分 Planner、Researcher 和 Reporter。
```

建议开启深度研究和自动执行计划。

录制重点：

- 计划生成。
- 节点时间线。
- 研究步骤执行。
- 最终报告。
- 会话历史和报告可回看。

可用接口证据：

```powershell
New-Item -ItemType Directory -Force target/http-check | Out-Null
'{"query":"解释为什么 Agent 工作流要区分 Planner、Researcher 和 Reporter。","session_id":"demo-video","enable_deepresearch":true,"auto_accepted_plan":true}' | Set-Content -Encoding UTF8 target/http-check/demo-video.json
curl.exe -N -H "Content-Type: application/json" --data-binary "@target/http-check/demo-video.json" http://localhost:18080/chat/stream > target/http-check/demo-video.sse
curl.exe http://localhost:18080/api/sessions/demo-video/history
curl.exe http://localhost:18080/api/reports/session/demo-video
```

## 片段 5：Agent Team 协作流程

当前状态：

- 项目已有 DeepResearch 图工作流和并行执行节点。
- 独立“活动策划”Agent Team Demo 计划在 Phase 6 补齐。

Phase 6 完成后录制输入：

```text
帮我策划一个周六下午在上海适合 20 人的技术读书会活动，考虑天气、场地、流程和风险。
```

录制重点：

- Planner 拆解任务。
- Research Team 分配步骤。
- Executor 执行天气、场地、流程、风险等子任务。
- Reviewer 或 Reporter 汇总最终方案。
- 前端时间线或 Mermaid 说明展示协作流程。

## 收尾检查

录制结束后关闭后端和本地 MCP，并确认端口释放：

```powershell
Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue
Get-NetTCPConnection -LocalPort 18090 -ErrorAction SilentlyContinue
```

不要提交：

- `.local/`
- `target/`
- `.idea/`
- `.claude/`
- 录制产生的大视频文件，除非课程交付明确要求纳入仓库。

## 验收清单

- 展示模型供应商和运行时切换。
- 展示 Skill 市场、三类内置 Skill 元数据、健康检查和显式调用。
- 展示 MCP 连接和工具调用。
- 展示 SSE 深度研究过程。
- 展示 PostgreSQL 持久化查询。
- 展示或说明 Agent Team 协作流程。
- 全程不暴露 API Key。
