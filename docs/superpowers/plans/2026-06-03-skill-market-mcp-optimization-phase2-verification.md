# Skill 市场与 MCP 管理优化 Phase 2 验证记录

验证时间：2026-06-04 12:08 +08:00

## 范围

- 验证计划 `2026-06-03-skill-market-mcp-optimization.md` 的 Phase 2：Prompt Skill zip 导入、导出、安装、卸载、重载和真实聊天调用链路。
- 本记录只保存命令、结果和验收证据，不包含 `.local/model-providers.json`、`.local/mcp-keys.json` 或任何 API Key。
- 本阶段未修改 `.local/` 内容作为提交对象；真实验证过程中通过 API 临时安装的本地 Skill 已在验证结束前卸载。

## 自动化验证

后端聚焦测试：

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dtest=top.lanshan.manmu.skill.service.*Test,top.lanshan.manmu.skill.market.*Test' test
```

结果：

- 40 个后端测试通过，失败 0，错误 0，跳过 0。
- 覆盖 `SkillPackageArchiveServiceTest`、`SkillCatalogRepositoryTest`、`SkillControllerTest`、`SkillServiceTest`、`SkillRegistryTest`、`SkillFileRepositoryTest`、`SkillToolProviderTest` 等 Phase 2 相关测试。

前端聚焦测试与构建：

```powershell
cd ui-vue3
npm run test:unit -- skillForm.spec.ts
npm run build
```

结果：

- `skillForm.spec.ts` 6 个测试通过。
- `npm run build` 通过，包含 `vue-tsc --build --force` 和 `vite build`。

## 真实 HTTP / E2E 验证

环境：

- Docker Desktop 中 PostgreSQL 容器 `manmu-postgres` 处于 healthy 状态，`5432` 映射到 Windows localhost。
- 启动前 `18080`、`18090`、`5173` 均无监听。
- 后端使用 JDK 17 启动到 `18080`，profile 为 `real-model`，并通过环境变量临时关闭 RAG：`MVP_RAG_ENABLED=false`。
- 后端日志确认连接 PostgreSQL 17.9，Flyway 8 个迁移校验通过，内置 Skill 从源码目录加载，本地市场目录为 `.local/skills/installed`。
- `/api/model/current` 返回当前真实模型供应商为 DeepSeek `deepseek-chat`，`apiKeyConfigured=true`；没有读取或输出 Key 内容。

准备的测试包：

- `target/http-check/sample-skill.zip`
- zip 内容只有 `skill.json` 和 `SKILL.md`。
- Skill 名称为 `phase2-echo-skill`，类型为 Prompt Skill，模板要求模型输出标记短语 `PHASE2_SKILL_ACTIVE`。

API 验证：

```powershell
curl.exe -F "file=@target/http-check/sample-skill.zip" http://localhost:18080/api/skills/packages/import
curl.exe http://localhost:18080/api/skills
curl.exe http://localhost:18080/api/skills/phase2-echo-skill
curl.exe -L http://localhost:18080/api/skills/phase2-echo-skill/export -o target/http-check/phase2-exported-skill.zip
curl.exe -X POST http://localhost:18080/api/skills/phase2-echo-skill/reload
curl.exe -X DELETE http://localhost:18080/api/skills/packages/weather-now
curl.exe -X PATCH http://localhost:18080/api/skills/phase2-echo-skill/toggle
curl.exe -X PATCH http://localhost:18080/api/skills/phase2-echo-skill/toggle
```

结果：

- 导入返回 `storageLocation=LOCAL`、`packageType=PROMPT`、`enabled=true`。
- `/api/skills` 中立即出现 `phase2-echo-skill`，无需重启后端。
- `/api/skills/phase2-echo-skill` 返回 definition 和 promptTemplate。
- 导出 zip 大小为 948 bytes，包含 `skill.json` 和 `SKILL.md`。
- reload 返回 `phase2-echo-skill` 当前定义。
- 卸载内置 `weather-now` 返回错误：`Built-in Skill 'weather-now' is read-only`。
- 本地 Skill 启停成功：第一次 toggle 后 `enabled=false`，第二次 toggle 后恢复 `enabled=true`。

真实聊天链路：

```powershell
curl.exe -N --max-time 180 -H "Content-Type: application/json" `
  --data-binary "@target/http-check/phase2-chat-request.json" `
  http://localhost:18080/chat/stream
```

请求内容要点：

- `query`: `@phase2-echo-skill --text=Phase2ZipImportWorks`
- `enable_deepresearch`: `false`
- `auto_accepted_plan`: `true`
- `thread_id`: `phase2-skill-e2e-20260604-thread`
- `session_id`: `phase2-skill-e2e-20260604`

结果：

- SSE 返回 `event:done`。
- 输出包含 `PHASE2_SKILL_ACTIVE` 和 `Phase2ZipImportWorks`。
- 这证明上传后的 Prompt Skill 无需重启后端即可被真实模型路径读取和执行。

持久化验证：

```powershell
curl.exe http://localhost:18080/api/reports/phase2-skill-e2e-20260604-thread
curl.exe http://localhost:18080/api/sessions/phase2-skill-e2e-20260604/threads/phase2-skill-e2e-20260604-thread
curl.exe http://localhost:18080/api/sessions/phase2-skill-e2e-20260604/threads/phase2-skill-e2e-20260604-thread/events
```

结果：

- 报告接口返回 `report_information`，内容包含 `PHASE2_SKILL_ACTIVE`。
- session history 返回线程状态 `COMPLETED`。
- event history 可读取，响应文件大小为 1928 bytes。

卸载与清理：

```powershell
curl.exe -X DELETE http://localhost:18080/api/skills/packages/phase2-echo-skill
curl.exe http://localhost:18080/api/skills
```

结果：

- 本地 Skill 卸载接口返回 204。
- 卸载后 `/api/skills` 中不再出现 `phase2-echo-skill`。

关闭验证：

- 已停止本阶段启动的 Maven/Java 后端进程。
- `Get-NetTCPConnection -LocalPort 18080,18090,5173 -ErrorAction SilentlyContinue` 无返回，端口已释放。
- PostgreSQL Docker 容器是验证前已有基础依赖，本阶段未停止该容器。

## Phase 2 结论

- 用户可以上传 Prompt Skill zip 包。
- 上传后无需重启后端即可被发现并被真实聊天模型路径读取。
- 本地 Skill 可以导出、重载、启用/禁用和卸载。
- 内置 Skill 保持只读，不能通过包卸载接口卸载。
- 导出包只包含 `skill.json` 与 `SKILL.md`。
- 自动化测试、前端构建、真实 HTTP/API、真实模型供应商路径和 PostgreSQL 持久化验证均通过。
- 验证过程未读取、输出或提交任何 API Key。
