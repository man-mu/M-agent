# Agent 学习项目补全 Phase 4 验收记录

## 验收目标

本次收尾针对计划 `docs/superpowers/plans/2026-06-10-agent-learning-project-completion.md` 的 Phase 4：可信本地 Jar Skill 热加载演示与控制台收口。

验收范围：

- 默认保持 Jar Skill 上传热加载关闭。
- 启用可信本地 Jar 插件后，完成导入、加载、健康检查、重载、停用、启用、聊天链路调用和卸载。
- 控制台能够展示 Jar 插件状态，并区分 Prompt Skill Zip 与 Jar Skill Zip 导入入口。
- 文档说明可信本地边界，明确 ClassLoader 隔离不是安全沙箱。

## 代码与文档变更确认

- `AppInfoController` 的 `/api/app/capabilities` 增加 `jarPluginsEnabled`。
- `SkillHealthService` 对 Jar Skill 增加 `jarPlugins` 健康检查。
- `SkillAutoConfiguration` 将 `mvp.skill.jar-plugins.enabled` 传入健康检查服务。
- `/skills` 控制台增加 Jar 插件状态摘要、Jar Skill 统计、Prompt/Jar 双导入入口、Jar Skill 只读/不可导出提示。
- 新增 `JarSkillDemoPackageGeneratorTest`，用于按需生成 `target/demo-packages/echo-json-skill.zip`。
- `README.md`、`docs/skill-development-guide.md`、`docs/demo-script.md` 增加 Jar Skill 演示、接口和安全边界说明。

## 自动化测试

后端聚焦测试：

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=SkillServiceTest,SkillControllerTest,SkillToolProviderTest,SkillPackageArchiveServiceTest,SkillHealthServiceTest,AppInfoControllerTest' test
```

结果：

- BUILD SUCCESS
- Tests run: 35
- Failures: 0
- Errors: 0

Jar Skill 演示包生成：

```powershell
mvn '-Dtest=JarSkillDemoPackageGeneratorTest' '-Dmvp.demo.jar-skill-package=true' test
```

结果：

- BUILD SUCCESS
- 生成 `target/demo-packages/echo-json-skill.zip`

后端全量测试：

```powershell
mvn test
```

结果：

- BUILD SUCCESS
- Tests run: 288
- Failures: 0
- Errors: 0
- Skipped: 4

前端测试与构建：

```powershell
cd ui-vue3
npm run test:unit
npm run build
```

结果：

- `npm run test:unit`：12 个测试文件通过，68 个测试通过。
- `npm run build`：type-check 与 Vite build 通过。

## 真实 HTTP 验收

前置：

```powershell
docker compose up -d postgres
```

结果：

- `manmu-postgres` 启动并 healthy。
- 后端启动日志确认连接真实 PostgreSQL：`jdbc:postgresql://localhost:5432/manmu`，schema 已是最新。

### 默认关闭模式

启动后端时不启用 Jar 插件。

接口证据：

```powershell
curl.exe -s http://localhost:18080/api/app/capabilities
curl.exe -s -F "file=@target/demo-packages/echo-json-skill.zip" http://localhost:18080/api/skills/packages/import-jar
```

保存文件：

- `target/http-check/jar-default-capabilities.json`
- `target/http-check/jar-default-import-disabled.json`

结果：

- `/api/app/capabilities` 返回 `jarPluginsEnabled:false`。
- `/api/skills/packages/import-jar` 返回 HTTP 403。
- 响应包含 `Jar Skill plugins are disabled`。

### 启用可信本地 Jar 插件

使用环境变量启用：

```powershell
$env:MVP_SKILL_JAR_PLUGINS_ENABLED='true'
$env:SERVER_PORT='18080'
mvn spring-boot:run
```

接口证据：

```powershell
curl.exe -s http://localhost:18080/api/app/capabilities
curl.exe -s -F "file=@target/demo-packages/echo-json-skill.zip" http://localhost:18080/api/skills/packages/import-jar
curl.exe -s http://localhost:18080/api/skills/echo-json-skill
curl.exe -s http://localhost:18080/api/skills/echo-json-skill/health
curl.exe -s -X POST http://localhost:18080/api/skills/echo-json-skill/reload
curl.exe -s -X PATCH http://localhost:18080/api/skills/echo-json-skill/toggle
curl.exe -s -X PATCH http://localhost:18080/api/skills/echo-json-skill/toggle
```

保存文件：

- `target/http-check/jar-enabled-capabilities.json`
- `target/http-check/jar-import.json`
- `target/http-check/jar-detail.json`
- `target/http-check/jar-health.json`
- `target/http-check/jar-reload.json`
- `target/http-check/jar-toggle-off.json`
- `target/http-check/jar-toggle-on.json`

结果：

- `/api/app/capabilities` 返回 `jarPluginsEnabled:true`。
- Jar Skill 导入返回 HTTP 201。
- 详情、健康检查、重载、停用、启用均返回 HTTP 200。
- 健康检查包含：
  - `jarPackage`: `plugin.jar is present`
  - `jarPlugins`: `Jar Skill plugins are enabled`
  - `parameterSchema`: `Parameter schema is valid JSON`

### 聊天链路调用

请求：

```powershell
@{
  session_id = 'jar-demo'
  thread_id = 'jar-demo-phase4'
  enable_deepresearch = $false
  auto_accepted_plan = $true
  max_step_num = 3
  query = '@echo-json-skill --message=JarSkillWorks 请基于 Jar Skill 返回作答'
} | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 target/http-check/jar-chat.json

curl.exe -N -H "Content-Type: application/json" --data-binary "@target/http-check/jar-chat.json" http://localhost:18080/chat/stream > target/http-check/jar-chat.sse
```

结果：

- `curl_exit=0`
- SSE 包含 `event:done`。
- 最终输出包含 `echo:JarSkillWorks|loader=top.lanshan.manmu.skill.plugin.SkillPluginClassLoader`。
- `/api/skills/echo-json-skill/invocations` 返回两条成功记录：
  - `source=TOOL`
  - `source=EXPLICIT`
- 调用历史输入包含 `message=JarSkillWorks`。

### 卸载与清理

接口：

```powershell
curl.exe -s -X DELETE http://localhost:18080/api/skills/packages/echo-json-skill
curl.exe -s http://localhost:18080/api/skills/echo-json-skill
```

结果：

- 卸载返回 HTTP 204。
- 卸载后查询返回 HTTP 404。
- `.local/skills/installed/echo-json-skill` 已不存在。

服务清理：

```powershell
Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue
docker compose stop postgres
```

结果：

- 后端进程已关闭。
- `18080` 无监听。
- `manmu-postgres` 已停止。

## 旁路观察

本次 Jar Skill 验收期间，本地 MCP 示例端口 `18090` 未启动，后端日志出现 MCP client 初始化失败：

```text
Failed to initialize MCP client http://127.0.0.1:18090
```

该错误来自 MCP 工具发现路径，不影响 Phase 4 Jar Skill 导入、热加载、调用、重载、启停和卸载验收。Phase 5 会单独验收 MCP 示例。

## Phase 4 验收结论

- Jar Skill 默认关闭的安全姿态已验证。
- 启用可信本地 Jar 插件后，Jar Skill 完整生命周期已通过真实 HTTP 验收。
- 聊天 SSE 链路已证明 Jar Skill 返回进入真实模型调用路径，并输出独立 `SkillPluginClassLoader` 标识。
- 控制台与文档已补齐 Jar Skill 状态、导入入口、错误提示和可信边界说明。
- 验收完成后后端服务、PostgreSQL 容器和 18080 端口均已清理。
