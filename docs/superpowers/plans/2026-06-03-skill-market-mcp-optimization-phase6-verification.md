# Skill 市场与 MCP 管理优化 Phase 6 验证记录

验证时间：2026-06-05 14:10 +08:00

## 范围

- 验证计划 `2026-06-03-skill-market-mcp-optimization.md` 的 Phase 6：可信本地 Jar Skill 插件接口、独立类加载器、上传热加载、启停、重载、卸载释放，以及真实聊天链路可见 Jar Skill 返回。
- 本记录只保存命令、结果和验收证据，不包含 `.local/model-providers.json`、`.local/mcp-keys.json` 或任何 API Key。
- Jar 插件上传能力保持默认关闭，仅在本次真实 HTTP 验证启动后端时通过环境变量临时启用。

## 自动化验证

聚焦验证：

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=SkillServiceTest,SkillToolProviderTest,SkillPackageArchiveServiceTest,SpringAiAgentClientSkillInvocationTest' test
```

结果：

- 26 个聚焦测试通过，失败 0，错误 0，跳过 0。
- 覆盖 Jar Skill 包解析、动态编译测试插件、`ServiceLoader` 加载、独立 `SkillPluginClassLoader`、启停、重载、卸载关闭、坏包清理，以及显式 `@echo-json-skill` 聊天注入。

全量验证：

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn test
```

结果：

- 264 个测试通过，失败 0，错误 0，跳过 3。
- Jar Skill 相关测试包含在全量测试中。

## 真实 HTTP / E2E 验证

环境：

- Docker Desktop 中 PostgreSQL 容器 `manmu-postgres` 为 healthy，`5432` 映射到 Windows localhost。
- 启动前 `18080`、`18090` 无监听；`5173` 有验证前已存在的前端监听进程，本阶段未启动也未停止它。
- 后端使用 JDK 17 启动到 `18080`，profile 为 `real-model`。
- 本次后端验证临时设置：
  - `SERVER_PORT=18080`
  - `MVP_RAG_ENABLED=false`
  - `MVP_SKILL_JAR_PLUGINS_ENABLED=true`
- `/api/model/current` 返回当前真实模型供应商为 DeepSeek `deepseek-chat`，`apiKeyConfigured=true`；未读取或输出 Key。

测试 Jar Skill 包：

- `target/http-check/phase6-echo-json-skill.zip`
- zip 内容：
  - `skill.json`
  - `plugin.jar`
  - `README.md`
- `plugin.jar` 通过 `javac` 和 `jar` 构建，声明 `META-INF/services/top.lanshan.manmu.skill.plugin.SkillPlugin`。
- 插件返回标记：
  - `PHASE6_JAR_SKILL_ACTIVE`
  - `SkillPluginClassLoader`

API 验证：

```powershell
curl.exe -F "file=@target/http-check/phase6-echo-json-skill.zip" `
  http://localhost:18080/api/skills/packages/import-jar

curl.exe http://localhost:18080/api/skills/phase6-echo-json-skill
curl.exe -X POST http://localhost:18080/api/skills/phase6-echo-json-skill/reload
curl.exe -X PATCH http://localhost:18080/api/skills/phase6-echo-json-skill/toggle
curl.exe -X PATCH http://localhost:18080/api/skills/phase6-echo-json-skill/toggle
```

结果：

- 导入返回 `packageType=JAR`、`storageLocation=LOCAL`、`enabled=true`。
- 后端日志确认 `Jar Skill loaded: phase6-echo-json-skill (example.EchoJsonSkill)`。
- `/api/skills/phase6-echo-json-skill` 返回 `promptTemplate=null`，说明 Jar Skill 不依赖 `SKILL.md` Prompt 模板。
- reload 成功重新加载 Jar Skill。
- 第一次 toggle 后 `enabled=false`，第二次 toggle 后恢复 `enabled=true`。

真实聊天链路：

```powershell
curl.exe -N --max-time 180 -H "Content-Type: application/json" `
  --data-binary "@target/http-check/phase6-chat-request.json" `
  http://localhost:18080/chat/stream
```

请求要点：

- `query`: `@phase6-echo-json-skill --message=Phase6JarWorks 请基于 Jar Skill 返回作答`
- `enable_deepresearch`: `false`
- `auto_accepted_plan`: `true`
- `session_id`: `phase6-jar-skill-e2e-20260605`
- `thread_id`: `phase6-jar-skill-e2e-20260605-thread`

结果：

- SSE 返回 `event:done`。
- SSE 输出包含 `PHASE6_JAR_SKILL_ACTIVE:Phase6JarWorks:loader=top.lanshan.manmu.skill.plugin.SkillPluginClassLoader`。
- 后端日志显示 `@phase6-echo-json-skill explicitly invoked`，且 `Skill tools ready: 4 tools`。
- 这证明 Jar Skill 返回进入真实模型路径，而不是 mock 或单元测试替代。

持久化验证：

```powershell
curl.exe http://localhost:18080/api/reports/phase6-jar-skill-e2e-20260605-thread
curl.exe http://localhost:18080/api/sessions/phase6-jar-skill-e2e-20260605/threads/phase6-jar-skill-e2e-20260605-thread
curl.exe http://localhost:18080/api/sessions/phase6-jar-skill-e2e-20260605/threads/phase6-jar-skill-e2e-20260605-thread/events
```

结果：

- 报告接口返回 `report_information`，内容包含 `PHASE6_JAR_SKILL_ACTIVE` 和 `SkillPluginClassLoader`。
- session history 返回线程状态 `COMPLETED`。
- event history 可读取，包含 `node.delta` 与 `graph.completed` 事件。

卸载与清理：

```powershell
curl.exe -X DELETE http://localhost:18080/api/skills/packages/phase6-echo-json-skill
curl.exe http://localhost:18080/api/skills
```

结果：

- 卸载接口返回 204。
- 日志确认 `.local/skills/installed/phase6-echo-json-skill` 被删除。
- 卸载后 `/api/skills` 不再出现 `phase6-echo-json-skill`。

关闭验证：

- 已停止本阶段启动的 Maven/Java 后端进程。
- `Get-NetTCPConnection -LocalPort 18080,18090 -ErrorAction SilentlyContinue` 无返回，端口已释放。
- `5173` 仍有验证前已存在的前端监听进程，本阶段未启动该进程，因此未停止。
- PostgreSQL Docker 容器为本地基础依赖，本阶段未停止该容器。

## Phase 6 结论

- 支持显式启用后的可信本地 Jar Skill zip 上传与热加载。
- 默认配置 `mvp.skill.jar-plugins.enabled=false`，避免默认暴露高风险上传能力。
- 每个 Jar Skill 通过独立 `SkillPluginClassLoader` 加载。
- Jar Skill 支持启停、重载、卸载，并在卸载/禁用时释放插件生命周期。
- 真实 HTTP、真实 DeepSeek 模型路径、PostgreSQL 持久化和 SSE 输出均验证通过。
- 验证过程未读取、输出或提交任何 API Key。
