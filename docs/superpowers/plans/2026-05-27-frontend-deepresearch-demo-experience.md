# Phase 7：DeepResearch 演示体验增强计划

## Background

`ui-vue3/` 已完成 Phase 0-6 的前端质量硬化：默认 Skill 禁用态、会话生命周期、Plan Gate / Resume、错误处理、自动化测试、E2E、体积拆分和移动端基础可用性都已经形成闭环。当前最新阶段提交为 `9ea6dd9 优化前端体积和交互细节`。

本计划承接用户提供的 `C:\Downloads\deep_research.mov` 演示方向，但不要求完整复刻视频中的后端能力。M-agent 当前后端是 Java 17 / Spring Boot 3.4.x / WebFlux SSE 的 DeepResearch 精简版，真实能力集中在：

- `/chat/stream`：真实模型 SSE 研究流。
- `/chat/resume`：Plan Gate 后继续执行。
- `/chat/stop`：停止当前研究。
- `/api/conversations` 与 `/api/conversations/{sessionId}`：会话历史。
- `/api/reports/{threadId}` 与 `/api/reports/session/{sessionId}`：持久化报告。
- `/api/model/*`：模型供应商读取、切换、测试。
- `/api/app/capabilities`：可选能力开关，默认 `skillEnabled=false`。

当前工作区有非本计划改动：`AGENTS.md`、`CLAUDE.md`、`.claude/`。执行本计划时不要回滚、整理、删除、提交这些文件；不要触碰 `.local/`、`target/`、`.idea/` 中的内容，除非只是测试过程中按项目约定写临时日志到 `target/`。

## Overall Goals

- 把现有真实研究流打磨成更接近 DeepResearch 的“研究工作台”演示体验。
- 让用户清楚看到：当前模式、研究计划、节点进度、等待反馈、恢复执行、报告生成、历史结果。
- 只展示后端真实返回的数据和真实可执行操作，不伪造搜索结果、引用、节点或报告。
- 保持 Phase 6 的体积优化成果，避免把 Markdown/报告/重组件重新塞回首屏。
- 保持桌面和 390px 移动宽度可用，不引入明显遮挡、横向溢出或报告不可关闭问题。

## Non-goals

- 不实现 deepresearch-main 的完整 RAG、MCP、Redis、复杂多 Agent 前端、登录系统或知识库管理。
- 不新增 mock agent、mock search、mock report、假引用或假节点。
- 不重写后端研究工作流，不替换真实模型调用路径。
- 不把不可用功能做成可点击入口。
- 不做大规模视觉重设计，不引入重型图表库或复杂动画库。
- 不提交 `.local/`、`target/`、`.idea/`、`.claude/`。

## Phase 7.0: Demo Baseline Audit

### Goal

在动手前固定 Phase 7 的演示基线，确认视频方向、现有页面能力、构建体积、真实后端状态和移动端状态，避免后续把体验增强误判成后端能力补齐。

### Main Changes

原则上不改实现代码，只补充审计记录或执行日志摘要。建议新建或更新：

- `docs/superpowers/plans/2026-05-27-frontend-deepresearch-demo-experience.md` 的执行备注，或
- 可选新建 `docs/superpowers/audits/2026-05-27-phase7-demo-baseline.md`。

审计内容：

- 阅读 `C:\Downloads\deep_research.mov`，记录其中只适合前端表现层借鉴的交互点。
- 记录当前 `/chat`、历史会话、报告面板、Plan Gate、`/settings`、`/skills` 禁用态。
- 记录 `npm run build` chunk 输出，尤其 `vue`、页面 chunk、`markdown`、`highlight`。
- 记录 1280x720 与 390x844 浏览器状态。

### Tests

```powershell
Set-Location C:\MainData\code\Claude_project\M-agent
git status --short

Set-Location C:\MainData\code\Claude_project\M-agent\ui-vue3
npm run build
```

### Real Verification

如需启动后端，使用 JDK 17，并在验证后关闭服务：

```powershell
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
curl.exe -sS http://localhost:18080/api/model/current
curl.exe -sS http://localhost:18080/api/app/capabilities
curl.exe -sS http://localhost:18080/api/conversations
```

浏览器检查：

- `1280x720`：`/chat`、历史详情、报告面板、`/settings`、`/skills`。
- `390x844`：同上，重点看布局、按钮、报告关闭。

### Acceptance Criteria

- 明确记录哪些视频效果可以在前端用真实数据表达，哪些需要后端能力暂不纳入。
- 确认没有读取或泄露 `.local/model-providers.json`。
- 若启动服务，测试结束后确认 18080 释放。

### Suggested Commit

如只补审计记录，可提交：`记录 Phase 7 演示体验基线`。

## Phase 7.1: 研究工作台首屏与模式状态

### Goal

把 `/chat` 从普通聊天页打磨成“研究工作台”入口，让用户第一眼能理解当前是快速回答还是深度研究、是否自动执行计划、当前模型是否可用、下一步可以做什么。

### Main Changes

主要文件：

- `ui-vue3/src/views/chat/index.vue`
- `ui-vue3/src/components/layout/index.vue`
- `ui-vue3/src/store/MessageStore.ts`
- `ui-vue3/src/services/api/model.ts`
- 可选新增：`ui-vue3/src/components/research-workbench/index.vue`

建议实现：

- 在 `/chat` 顶部或输入区附近展示紧凑的研究状态条：
  - 当前模式：快速回答 / 深度研究。
  - Plan Gate：自动执行计划开关状态。
  - 当前模型供应商与模型名，只读展示，跳转 `/settings` 修改。
  - 当前会话状态：草稿、运行中、等待计划确认、已完成、已停止、失败。
- 首屏空态文案只描述真实能力，不承诺未实现的知识库、MCP、复杂搜索编排。
- 输入区按钮和开关在运行中、等待确认、恢复中、停止中保持清晰禁用态。
- 保持首屏轻量，不把报告/Markdown 重依赖提前加载。

### Tests

```powershell
Set-Location C:\MainData\code\Claude_project\M-agent\ui-vue3
npm run type-check
npm run build
npm run test:unit
```

### Real Verification

真实后端 18080 运行后：

- `/api/model/current` 可读取当前模型。
- `/chat` 空态显示研究工作台状态，不创建幽灵会话。
- 切换快速回答 / 深度研究，状态条同步变化。
- 390px 移动宽度下输入区、状态条、按钮不重叠。

### Acceptance Criteria

- `/chat` 首屏更像研究工作台，但没有新增假功能入口。
- 快速回答 / 深度研究 / 自动执行计划状态清晰。
- 当前模型只读状态可见，且能进入 `/settings`。
- 构建无大 chunk warning。

### Suggested Commit

`优化研究工作台首屏状态`

## Phase 7.2: SSE 工作流可视化增强

### Goal

基于真实 SSE 事件增强工作流可视化，让用户能看懂研究正在经历哪些阶段、哪些节点已完成、哪里等待反馈或失败。

### Main Changes

主要文件：

- `ui-vue3/src/views/chat/index.vue`
- `ui-vue3/src/store/MessageStore.ts`
- `ui-vue3/src/services/api/chat.ts`
- 可选新增：`ui-vue3/src/components/research-timeline/index.vue`
- 可选新增测试：`ui-vue3/src/store/MessageStore.spec.ts`

建议实现：

- 从 `messageStore.events` 派生稳定的节点视图，不在模板里堆复杂逻辑。
- 统一识别这些状态：
  - `graph.started` / 节点 `started`
  - `plan.generated`
  - `human_feedback.waiting`
  - 节点 `completed`
  - `graph.completed`
  - `graph.failed`
  - `graph.stopped`
- 节点卡片展示真实字段：
  - 节点名 / 展示名。
  - 状态 tag。
  - 简短摘要。
  - 后端真实返回的来源链接。
  - 时间戳或序号（若事件包含）。
- 对缺失字段做空值保护，不显示“未知但像真实”的假内容。
- 移动端使用纵向时间线或折叠式节点，避免横向流程图挤压。

### Tests

```powershell
Set-Location C:\MainData\code\Claude_project\M-agent\ui-vue3
npm run type-check
npm run build
npm run test:unit
```

建议新增或补充单测：

- `MessageStore` 能从多种 SSE event_type 派生运行/等待/完成/失败状态。
- 缺失 `content`、`payload`、`graph_id` 时不抛错。
- `human_feedback.waiting` 能稳定进入等待计划确认状态。

### Real Verification

真实后端 18080：

- 快速回答路径：发送一个短问题，看到工作流从运行到完成。
- 深度研究 + `auto_accepted_plan=false`：看到计划生成和等待反馈。
- 接受计划后：通过 `/chat/resume` 继续，节点追加而不是清空。
- 失败路径如可安全触发，确认 UI 显示失败原因；不要用 mock。

### Acceptance Criteria

- 用户能区分运行中、等待计划确认、恢复中、完成、失败、停止。
- 工作流只展示真实事件和真实来源。
- 移动端无横向溢出。
- 无新增控制台 error。

### Suggested Commit

`增强研究工作流可视化`

## Phase 7.3: Plan Gate 审阅体验增强

### Goal

让深度研究计划确认更接近“研究计划审阅”：计划内容更易读，接受/修改反馈更明确，resume 后状态不混乱。

### Main Changes

主要文件：

- `ui-vue3/src/views/chat/index.vue`
- `ui-vue3/src/services/api/chat.ts`
- `ui-vue3/src/store/MessageStore.ts`
- 可选新增：`ui-vue3/src/components/plan-review/index.vue`
- 可选新增：`ui-vue3/src/components/plan-review/planReview.spec.ts`

建议实现：

- 将现有计划确认 UI 抽成小组件，降低 `chat/index.vue` 模板复杂度。
- 计划步骤展示：
  - 步骤标题、描述。
  - `need_web_search` 用真实标记显示。
  - `step_type`、`assigned_node` 若存在则显示。
  - `has_enough_context` 和 `thought` 若存在则展示。
- 反馈输入：
  - 接受计划、修改计划、提交反馈分状态。
  - resume 中按钮 loading，避免重复提交。
  - 修改意见为空时给出清晰提示。
- Stop 在等待计划确认状态下的语义要清楚：退出等待态或调用 `/chat/stop`，不要让 UI 悬挂。

### Tests

```powershell
Set-Location C:\MainData\code\Claude_project\M-agent\ui-vue3
npm run type-check
npm run build
npm run test:unit
```

建议补充 E2E：

- 深度研究关闭自动执行计划后，页面出现计划确认卡。
- 点击接受计划后调用 resume 并继续到完成或可解释失败。
- 输入修改意见后调用 resume，UI 不重复提交。

### Real Verification

准备真实请求体时写入 `target/http-check/*.json`，避免 PowerShell JSON 转义：

```powershell
Set-Location C:\MainData\code\Claude_project\M-agent
New-Item -ItemType Directory -Force target\http-check | Out-Null
Set-Content -Encoding UTF8 target\http-check\phase7-plan.json '{"session_id":"phase7-plan-gate-check","query":"请用三步研究 Java 17 record 的适用场景，给出简短报告。","enable_deepresearch":true,"auto_accepted_plan":false,"max_step_num":3}'
curl.exe -N -sS -H "Content-Type: application/json" --data-binary "@target/http-check/phase7-plan.json" http://localhost:18080/chat/stream > target\http-check\phase7-plan.sse
```

浏览器验证：

- 计划等待态出现。
- 接受计划继续执行。
- 修改意见路径至少能发出 `/chat/resume` 并得到真实后端响应。

### Acceptance Criteria

- Plan Gate 卡片独立、易读、可测试。
- 接受/修改/恢复/停止状态清晰。
- Resume 后事件、报告、历史不丢。

### Suggested Commit

`优化研究计划审阅体验`

## Phase 7.4: 报告阅读与来源呈现 polish

### Goal

让报告面板更像最终研究产物，增强阅读、复制、刷新和空/错状态，但仍只使用真实报告接口和 SSE 产物。

### Main Changes

主要文件：

- `ui-vue3/src/components/report/index.vue`
- `ui-vue3/src/components/md/index.vue`
- `ui-vue3/src/views/chat/index.vue`
- `ui-vue3/src/services/api/reports.ts`
- 可选新增：`ui-vue3/src/components/reference-sources/index.vue`

建议实现：

- 报告面板增加轻量工具：
  - 复制报告正文。
  - 刷新报告。
  - 显示报告来源：来自当前 SSE 产物 / 来自持久化接口。
  - 显示报告状态：未生成、加载中、已生成、读取失败。
- 若报告内容包含 Markdown 标题，可以派生一个轻量目录；若没有标题，不伪造目录。
- 来源链接只展示 `site_information` 或事件中真实返回的链接；没有来源时显示“本次后端未返回来源链接”。
- 390px 移动宽度下报告面板保持可关闭、可滚动。
- 继续保持 `MD` 异步加载。

### Tests

```powershell
Set-Location C:\MainData\code\Claude_project\M-agent\ui-vue3
npm run type-check
npm run build
npm run test:unit
```

建议补充单测：

- 从 Markdown 标题派生目录。
- 空报告、未生成报告、读取失败时状态文案稳定。
- 来源链接去重和空值保护。

### Real Verification

真实后端 18080：

- 打开已有完成会话的报告。
- 点击刷新，确认 `/api/reports/{threadId}` 成功。
- 复制报告正文，确认按钮状态反馈。
- 打开一个无报告或报告未生成的会话，确认不是空白。
- 移动端 390px 下展开/关闭报告面板。

### Acceptance Criteria

- 报告阅读体验比 Phase 6 更完整。
- 不显示假来源、不伪造目录。
- 报告面板桌面/移动端均可关闭，无遮挡。

### Suggested Commit

`优化研究报告阅读体验`

## Phase 7.5: 历史会话与演示闭环

### Goal

让历史列表更适合演示：用户可以快速识别完成、停止、失败、是否有报告，并从历史恢复到可读状态。

### Main Changes

主要文件：

- `ui-vue3/src/components/layout/index.vue`
- `ui-vue3/src/store/ConversationStore.ts`
- `ui-vue3/src/services/api/conversation.ts`
- `ui-vue3/src/services/api/reports.ts`
- `ui-vue3/src/views/chat/index.vue`

建议实现：

- 在历史项展示：
  - 标题。
  - 消息数。
  - 最近时间。
  - 状态：本地草稿、已完成、已停止、失败、未知。
  - 报告可用标记，前提是后端已有字段或可用轻量接口确认。
- 避免对每个历史项都发大量报告请求；如需要报告可用状态，限制为当前会话或最近少量会话，或只在打开详情后确认。
- 历史详情打开后，报告按钮状态与 `threadId` / 报告是否存在一致。
- 移动端历史列表仍使用横向滚动卡片，避免压缩主内容。

### Tests

```powershell
Set-Location C:\MainData\code\Claude_project\M-agent\ui-vue3
npm run type-check
npm run build
npm run test:unit
```

建议补充单测：

- `ConversationStore.loadFromBackend()` 合并本地草稿和后端历史时保留状态。
- 历史项状态派生对缺失字段安全。

### Real Verification

真实后端 18080：

- `/api/conversations` 可读。
- 打开完成会话，报告按钮可用。
- 打开停止/失败会话如存在，状态能被区分；若无样本，记录未覆盖。
- 删除当前会话后回到 `/chat` 空态，不出现幽灵会话。

### Acceptance Criteria

- 历史列表更适合演示扫描。
- 不产生 N+1 过量请求。
- 历史详情、报告和当前会话状态一致。

### Suggested Commit

`增强历史会话演示信息`

## Phase 7.6: 自动化与演示验收脚本

### Goal

把 Phase 7 的演示体验纳入自动化验证，保证后续 polish 不破坏核心真实路径。

### Main Changes

主要文件：

- `ui-vue3/e2e/core.spec.ts`
- 可选新增：`ui-vue3/e2e/deepresearch-demo.spec.ts`
- `ui-vue3/playwright.config.ts`
- `ui-vue3/src/**/*.spec.ts`

建议覆盖：

- `/chat` 研究工作台空态和模式状态。
- 快速回答真实 SSE 完成，历史可恢复，报告可打开。
- 深度研究 Plan Gate 显示计划确认卡。
- 接受计划后 resume 路径可继续。
- `/settings` 当前模型可见。
- `/skills` 禁用态不出现新建入口。
- 移动视口 `390x844`：`/chat`、报告面板、`/settings`、`/skills` 无横向溢出。

### Tests

```powershell
Set-Location C:\MainData\code\Claude_project\M-agent\ui-vue3
npm run test
npm run test:e2e
```

### Real Verification

E2E 前：

- Docker `manmu-postgres` healthy。
- 后端 18080 使用 JDK17 启动，PID 写入 `target/`。
- 确认 `/api/model/current` 返回已配置供应商。

E2E 后：

- 查询报告和历史接口确认持久化状态。
- 停止后端。
- 用 `Get-NetTCPConnection -LocalPort 18080` 确认端口释放。

### Acceptance Criteria

- `npm run test` 通过。
- `npm run test:e2e` 在真实后端路径通过。
- 新增 E2E 不依赖 mock，不泄露 key。
- 移动端关键路径纳入自动化或手动记录。

### Suggested Commit

`补充演示体验自动化验收`

## Acceptance Checklist

- [ ] `git status --short` 已记录，未误碰 `.claude/`、`.local/`、`target/`、`.idea/`。
- [ ] `npm run type-check` 通过。
- [ ] `npm run build` 通过且无新的大 chunk warning。
- [ ] `npm run test:unit` 通过。
- [ ] `npm run test` 通过。
- [ ] 真实后端 18080 + PostgreSQL + 真实模型供应商路径验证通过。
- [ ] `npm run test:e2e` 通过，覆盖快速回答、历史、报告、设置、Skill 禁用态。
- [ ] 深度研究 Plan Gate / Resume 在浏览器中通过真实后端验证。
- [ ] `/chat` 在 1280x720 和 390x844 下无横向溢出、按钮遮挡或报告不可关闭。
- [ ] 报告、来源、历史状态只展示真实后端数据。
- [ ] 每个执行阶段提交一次中文 commit。

## Recommended Execution Order

1. Phase 7.0：先做演示基线审计，明确视频中哪些效果可以用现有真实数据表达。
2. Phase 7.1：先改 `/chat` 首屏工作台和模式状态，建立演示入口。
3. Phase 7.2：增强 SSE 工作流可视化，让真实研究过程更易懂。
4. Phase 7.3：打磨 Plan Gate 审阅体验，这是 DeepResearch 风格的核心交互。
5. Phase 7.4：优化报告阅读，让最终产物更适合展示。
6. Phase 7.5：增强历史会话信息，形成演示闭环。
7. Phase 7.6：补自动化和移动端验收，把 Phase 7 体验固定下来。

## Quick Start For Executor

```powershell
Set-Location C:\MainData\code\Claude_project\M-agent
git status --short

# 后端命令使用 JDK 17
$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn test

# 前端验证
Set-Location C:\MainData\code\Claude_project\M-agent\ui-vue3
npm run test
```

真实 HTTP/SSE 验证必须使用当前项目后端、真实 PostgreSQL 和真实模型供应商路径。若启动服务，记录 PID 到 `target/`，验证结束后停止服务，并确认 18080 / 5173 没有 Listen 进程。
