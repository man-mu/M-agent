# Graph 高级执行骨架实现计划

## 背景

当前项目已经完成 Graph 动态编排迁移：

- 默认运行路径是 `GraphResearchRunner`。
- 旧 `SimpleResearchRunner`、runner 选择属性和 simple fallback 已移除。
- 当前 Graph 已覆盖直接回答、自动研究、人工计划门、接受恢复、拒绝重规划、停止、报告持久化和 session history。
- 当前项目仍是 DeepResearch 风格的精简后端，主包名为 `top.lanshan.manmu`。

下一阶段目标是对齐 `C:\MainData\code\Codex_project\deepresearch-main` 的高级执行骨架，但仍保持精简版可运行能力，不在本阶段一次性搬入 RAG、Redis、MCP、前端、短期记忆、专业知识库或完整观测体系。

## 与 deepresearch-main 的对齐点

主项目中高级执行骨架的关键形态如下：

- `ResearchTeamNode` 不再直接路由到单个 `researcher` / `processor`，而是在还有未完成 step 时路由到 `parallel_executor`。
- `ParallelExecutorNode` 只负责分配任务，不负责执行任务。
- 执行状态使用节点名前缀表达所有权，例如：
  - `assigned_researcher_0`
  - `processing_researcher_0`
  - `completed_researcher_0`
  - `error_researcher_0`
  - `assigned_coder_0`
  - `completed_coder_0`
- `researcher_n` 执行 `RESEARCH` step。
- `coder_n` 执行 `PROCESSING` step。主项目没有单独 `CODING` step type，`PROCESSING` 对应 coder 执行器。
- 图结构是：

```text
research_team
  -> parallel_executor
      -> researcher_0
      -> researcher_1
      -> coder_0
      -> coder_1
  -> research_team
  -> reporter
```

M-agent 应保留自己的简化边界：

- 继续使用 Java 17 / Maven / Spring Boot 3.4.x。
- 继续使用 WebFlux SSE。
- 继续使用 PostgreSQL 保存报告、会话历史和会话上下文。
- 继续使用真实模型供应商和真实 HTTP/SSE E2E 验证。
- 不引入 mock agent / mock search fallback。

## 总体策略

- 先稳定事件协议和 step 执行状态，再改图结构。
- 先实现“可调度的执行骨架”，再实现真正的 Docker coder、MCP 工具、RAG 和前端。
- 每个阶段都必须能独立完成：代码、测试、真实 HTTP/SSE E2E、关闭服务、中文 commit。
- 每个阶段结束前使用 `$task-handoff pause <task-id>` 写入英文 handoff 文件，下一阶段通过 `$task-handoff resume <next-task-id>` 或读取本计划继续。
- 阶段 handoff 文件统一写入 `.codex/tasks/<task-id>.md`，遵守 `C:\Users\20232\.codex\skills\task-handoff\SKILL.md`。
- 计划文档使用中文；handoff 文件按 skill 要求使用英文。

## 全阶段通用验收要求

每个阶段都需要：

- 执行 Maven 命令前临时设置 `JAVA_HOME=C:\WorkResources\JDKs\JDK17`。
- 至少运行本阶段相关 focused tests。
- 风险较高或触及共享模型/Graph/Controller 时运行 `mvn test`。
- 启动本地后端服务，用 `curl.exe` 通过真实 HTTP/SSE API 验证生产形态路径。
- 真实验证必须经过 Docker/PostgreSQL 和真实模型供应商；如 DashScope 限流，可通过 `/api/model/switch` 临时切到已有 key 的 DeepSeek。
- 请求体优先写到 `target/http-check/*.json`，再用 `curl.exe --data-binary "@..."`。
- 测试完成必须关闭服务，并用 `Get-NetTCPConnection -LocalPort 18080` 确认端口释放。
- 不读取、不打印、不提交 `.local/model-providers.json` 中的 API Key。

## 阶段 1：稳定 SSE 事件协议

### 任务 ID

`add-stable-stream-event-contract`

### 目标

在不改变现有业务路径的前提下，建立前端和后续高级节点可长期消费的事件 envelope。

### 主要改动

- 新增或扩展统一事件模型，例如：
  - `ResearchStreamEvent`
  - `ResearchStreamEventType`
  - `ResearchNodeMetadata`
- 事件 envelope 至少包含：
  - `sequence`
  - `event_type`
  - `node_name`
  - `node_type`
  - `executor_id`
  - `step_id`
  - `phase`
  - `status`
  - `display_title`
  - `content`
  - `payload`
  - `site_information`
  - `done`
  - `timestamp`
  - `graph_id`
- 先保持 `/chat/stream` 当前 `ChatStreamResponse` 兼容，可通过新增字段或包装内部转换实现。
- 定义稳定事件类型，先覆盖当前生命周期：
  - `graph.started`
  - `node.started`
  - `node.delta`
  - `node.completed`
  - `node.failed`
  - `plan.generated`
  - `human_feedback.waiting`
  - `human_feedback.accepted`
  - `human_feedback.rejected`
  - `report.completed`
  - `graph.completed`
  - `graph.stopped`
  - `graph.failed`
- `GraphResearchRunner` 输出事件时为每个 thread 维护递增 `sequence`。
- 修正或集中化节点展示名逻辑，避免 Controller 内硬编码继续膨胀。

### 验收

- focused tests 覆盖：
  - direct answer 的 event envelope。
  - auto research 的 sequence 递增。
  - human feedback waiting 的事件类型。
  - stop 的事件类型。
- `mvn test` 通过。
- 真实 E2E：
  - `/chat/stream` direct answer。
  - `/chat/stream` auto accepted research。
  - `/chat/stream` manual pause。
  - `/chat/stop` paused thread。
- SSE 输出中能看到稳定字段，不破坏现有前端可能依赖的旧字段。

### 建议提交

`稳定图编排流式事件协议`

### handoff 要点

- 阶段结束执行：`$task-handoff pause add-stable-stream-event-contract`。
- handoff 需要记录新增事件字段、兼容策略、已验证的 SSE 文件路径和是否存在前端兼容风险。

## 阶段 2：升级 Step 执行状态模型

### 任务 ID

`add-step-execution-state-model`

### 目标

让每个 plan step 能被调度器和多执行器节点安全识别、分配、执行和汇总。

### 主要改动

- 扩展 `ResearchStep`，对齐主项目状态语义：
  - `id`
  - `assignedNode`
  - `attempt`
  - `error`
  - `startedAt`
  - `completedAt`
  - `executionStatus`
  - `executionRes`
- 新增执行状态 helper，例如 `StepExecutionStatus`：
  - `assigned_<nodeName>`
  - `processing_<nodeName>`
  - `completed_<nodeName>`
  - `error_<nodeName>`
  - `pending`
- 保留当前 `pending` / `completed` / `error` 的兼容判断，避免旧测试和旧报告路径一次性失效。
- 在 planner 输出映射后为缺失 id 的 step 生成稳定 id，例如 `step-1`、`step-2`。
- `ResearchTeamNode`、`ResearcherNode`、`ProcessorNode` 改用 helper 判断 terminal 状态。
- `ResearchState` 增加轻量执行状态字段：
  - `runningNodes`
  - `completedNodes`
  - `failedNodes`
  - `lastAssignedNodes`

### 验收

- focused tests 覆盖：
  - planner 后 step 自动补 id。
  - `assigned_researcher_0` 非 terminal。
  - `processing_researcher_0` 非 terminal。
  - `completed_researcher_0` terminal。
  - `error_researcher_0` terminal。
  - 旧 `completed` / `error: xxx` 仍可识别。
- `mvn test` 通过。
- 真实 E2E：
  - 自动研究能完成并保存报告。
  - 人工暂停和接受恢复不受影响。

### 建议提交

`升级研究步骤执行状态模型`

### handoff 要点

- 阶段结束执行：`$task-handoff pause add-step-execution-state-model`。
- handoff 需要记录状态前缀、兼容策略、哪些节点已迁移 helper、哪些旧状态仍保留。

## 阶段 3：新增 ParallelExecutorNode 任务分配器

### 任务 ID

`add-parallel-executor-assignment`

### 目标

先实现和验证任务分配逻辑，但不急于改变默认 Graph 路由。

### 主要改动

- 新增配置属性，例如：

```yaml
mvp:
  research:
    advanced-execution:
      enabled: false
      parallel-node-count:
        researcher: 2
        coder: 1
```

- 新增 `ParallelExecutorNode`，对齐主项目职责：
  - 读取当前 plan。
  - 跳过 terminal step。
  - 跳过已 assigned / processing 的 step。
  - `RESEARCH` step 分配给 `researcher_n`。
  - `PROCESSING` step 分配给 `coder_n`。
  - 仅当所有 research step terminal 后，才开始分配 processing step 给 coder。
  - 写入 `assigned_<nodeName>`、`assignedNode`、`attempt`。
  - 输出 `step.assigned` 事件。
- 新增单元测试覆盖 round-robin 分配和 research-before-processing 规则。
- 暂不接入 Graph 主路径，避免同时改路由和节点行为。

### 验收

- focused tests 覆盖：
  - 多个 research step 分配到 `researcher_0`、`researcher_1`。
  - research 未完成时 processing 不分配给 coder。
  - research 完成后 processing 分配给 `coder_0`。
  - 已 assigned / processing / completed / error 的 step 不重复分配。
- `mvn test` 通过。
- 真实 E2E：
  - 旧 Graph 路径仍可完成自动研究。
  - 旧暂停/恢复/停止路径不受影响。

### 建议提交

`添加并行执行任务分配器`

### handoff 要点

- 阶段结束执行：`$task-handoff pause add-parallel-executor-assignment`。
- handoff 需要记录默认配置仍为 `enabled=false`，下一阶段开始把 researcher 改造成多执行器。

## 阶段 4：支持多 Researcher 执行器节点

### 任务 ID

`add-multi-researcher-executors`

### 目标

让 `researcher_0`、`researcher_1` 等多实例节点能够执行被 `parallel_executor` 分配的 research step。

### 主要改动

- 抽取或改造 `ResearcherNode`：
  - 保留当前 `researcher` 作为兼容节点，或改为默认 `researcher_0` 包装。
  - 新增支持 `executorId` 的构造路径。
  - `name()` 返回 `researcher_<executorId>`。
  - 只处理 `assigned_<nodeName>` 或需要重试的 step。
  - 执行时写入 `processing_<nodeName>`。
  - 成功后写入 `completed_<nodeName>` 和 observation。
  - 失败后写入 `error_<nodeName>` 和 step error。
- `ResearchGraphBuilder` 增加创建 researcher 执行器节点的能力，但默认主图暂不一定启用高级路由。
- `ResearchNodeGraphAction` 需要能处理动态节点名和 executor metadata。
- SSE 事件需包含：
  - `node_name=researcher_0`
  - `node_type=researcher`
  - `executor_id=0`
  - `step_id`

### 验收

- focused tests 覆盖：
  - `researcher_0` 只处理分配给自己的 step。
  - `researcher_1` 不处理 `assigned_researcher_0`。
  - 成功后状态为 `completed_researcher_0`。
  - 失败事件携带非空 error。
- `mvn test` 通过。
- 真实 E2E：
  - 旧自动研究路径不回归。
  - 可用测试配置或临时 graph 验证单个 `researcher_0` 执行真实模型路径。

### 建议提交

`支持多研究执行器节点`

### handoff 要点

- 阶段结束执行：`$task-handoff pause add-multi-researcher-executors`。
- handoff 需要记录兼容 `researcher` 节点是否仍保留，以及下一阶段 coder 如何复用状态 helper。

## 阶段 5：新增最小 CoderNode

### 任务 ID

`add-minimal-coder-node`

### 目标

对齐主项目的 coder 执行器骨架，但先不接 Docker Python executor、MCP 或真实代码执行沙箱。

### 主要改动

- 新增 `CoderAgent` 或等价 LLM agent。
- 新增 `CoderNode`：
  - 节点名为 `coder_<executorId>`。
  - 处理 `PROCESSING` step。
  - 只处理 `assigned_coder_<executorId>` 的 step。
  - 成功后写入 `completed_coder_<executorId>` 和 execution result。
  - 失败后写入 `error_coder_<executorId>`。
- 新增 `src/main/resources/prompts/coder.md` 的精简版，参考 `deepresearch-main/src/main/resources/prompts/coder.md`，但不要复制主项目的完整复杂上下文。
- `ProcessorNode` 暂时保留为旧线性路径兼容节点；高级执行开启后 `PROCESSING` step 由 `coder_n` 执行。
- SSE 事件需包含：
  - `node_name=coder_0`
  - `node_type=coder`
  - `executor_id=0`
  - `step_id`

### 验收

- focused tests 覆盖：
  - `coder_0` 只处理 `PROCESSING` step。
  - `coder_0` 不处理 research step。
  - 成功后状态为 `completed_coder_0`。
  - 空异常 message 有 fallback，避免 `Map.of` null。
- `mvn test` 通过。
- 真实 E2E：
  - 临时请求生成至少一个 processing step，验证 coder 真实模型调用成功。
  - 报告包含 coder 产生的 observation。

### 建议提交

`添加最小编程执行器节点`

### handoff 要点

- 阶段结束执行：`$task-handoff pause add-minimal-coder-node`。
- handoff 需要明确 Docker executor、MCP tool callback 和反思机制仍是后续阶段，不属于本阶段。

## 阶段 6：接入高级执行 Graph 路由

### 任务 ID

`wire-advanced-execution-graph`

### 目标

把 `parallel_executor`、`researcher_n`、`coder_n` 接入 Graph，但先通过配置显式开启。

### 主要改动

- `ResearchGraphBuilder` 支持两套执行子图：
  - 旧子图：`research_team -> researcher | processor | reporter`。
  - 高级子图：`research_team -> parallel_executor -> researcher_n/coder_n -> research_team -> reporter`。
- `advanced-execution.enabled=false` 时保持旧行为。
- `advanced-execution.enabled=true` 时：
  - `research_team` 如果所有 step terminal，路由到 `reporter`。
  - 否则路由到 `parallel_executor`。
  - `parallel_executor` 分配 step 后进入对应 executor 节点。
  - executor 节点执行后回到 `research_team`。
- 如果 Spring AI Alibaba Graph 的多普通边并发语义与预期不一致，先采用“每轮选择一个已分配 executor 节点”的顺序执行方式，但节点命名、状态和事件协议必须与并行形态一致。
- 增加图构建测试，验证高级路由节点存在。

### 验收

- focused tests 覆盖：
  - 高级开关关闭时旧图路径不变。
  - 高级开关开启时 `research_team` 路由到 `parallel_executor`。
  - research step 可进入 `researcher_0`。
  - processing step 可进入 `coder_0`。
  - executor 完成后回到 `research_team`，全部 terminal 后进入 `reporter`。
- `mvn test` 通过。
- 真实 E2E：
  - 启动服务并显式启用高级执行配置。
  - `/chat/stream` 自动研究路径产生 `parallel_executor`、`researcher_0` 或 `coder_0` 事件。
  - 报告可读取，history 为 `COMPLETED`。
  - 人工暂停、接受恢复、拒绝重规划、stop 不回归。

### 建议提交

`接入高级图执行路由`

### handoff 要点

- 阶段结束执行：`$task-handoff pause wire-advanced-execution-graph`。
- handoff 需要记录是否采用真正 fan-out，还是顺序执行的简化兼容策略。

## 阶段 7：高级执行成为默认路径

### 任务 ID

`enable-advanced-execution-default`

### 目标

在高级执行路径通过完整真实 E2E 后，把它设为默认运行方式。

### 主要改动

- 默认配置改为：

```yaml
mvp:
  research:
    advanced-execution:
      enabled: true
      parallel-node-count:
        researcher: 2
        coder: 1
```

- 保留旧线性执行子图作为短期 fallback，或明确删除条件：
  - 如果高级路径已覆盖所有旧行为，可计划下一阶段删除。
  - 如果仍存在 provider/token/Graph API 风险，保留一阶段显式 fallback。
- 更新测试期望，默认路径应出现 `parallel_executor` 和 executor 节点。
- 更新 README 或项目说明中的运行路径说明，如当前项目已有对应说明文件再修改。

### 验收

- `mvn test` 通过。
- 真实 E2E 使用默认配置，不额外传 `advanced-execution.enabled=true`：
  - direct answer。
  - auto accepted full research。
  - manual pause。
  - accepted resume。
  - rejected resume/replan。
  - stop paused thread。
  - `/api/research/stream` smoke。
- 验证报告和 session history：
  - `COMPLETED` 报告可读取。
  - `STOPPED` 线程状态可读取。
- 日志和 SSE 输出不含 `event:error`、`NullPointerException`、provider quota 或 timeout 错误。
- 服务关闭且 18080 释放。

### 建议提交

`默认启用高级图执行骨架`

### handoff 要点

- 阶段结束执行：`$task-handoff pause enable-advanced-execution-default`。
- handoff 需要记录默认配置变更、真实 E2E 的 thread id、报告 id 和残留风险。

## 阶段 8：收尾、代码审查与后续能力入口

### 任务 ID

`review-advanced-execution-readiness`

### 目标

做一次专项 code review 和全链路回归，确认高级执行骨架可以承接后续 RAG、MCP、Docker coder、前端调试台。

### 主要改动

- 代码审查重点：
  - 事件协议是否稳定。
  - step 状态是否没有混用旧状态导致路由错判。
  - paused/resume/stop 是否能清理运行状态和分配状态。
  - report 是否从最新 graph state 保存。
  - executor node 命名是否和主项目对齐。
  - `.local`、`target`、`.idea` 没有被提交。
- 如果旧线性子图已经无意义，单独评估是否删除。
- 为后续阶段写出清晰入口：
  - RAG：`user_file_rag` / `professional_kb_decision` / `professional_kb_rag`。
  - MCP：`McpProviderFactory`、按 node/agent 配置工具。
  - Docker coder：Python executor、容器超时、stdout/stderr、安全限制。
  - 前端：基于稳定事件协议的节点时间线。

### 验收

- 源码扫描不应出现无意的旧路径或死代码。
- `mvn test` 通过。
- 真实全链路 E2E 全部通过。
- 服务关闭且 18080 释放。
- 形成一个中文 review 结论，明确“可进入 RAG/MCP/Docker coder/前端哪个下一阶段”。

### 建议提交

`审查高级图执行骨架`

### handoff 要点

- 阶段结束执行：`$task-handoff pause review-advanced-execution-readiness`。
- 如果用户决定进入 RAG/MCP/Docker coder/前端其中一条线，再创建新的计划或从本 handoff 派生下一阶段 task id。

## 推荐执行顺序

1. `add-stable-stream-event-contract`
2. `add-step-execution-state-model`
3. `add-parallel-executor-assignment`
4. `add-multi-researcher-executors`
5. `add-minimal-coder-node`
6. `wire-advanced-execution-graph`
7. `enable-advanced-execution-default`
8. `review-advanced-execution-readiness`

## task-handoff 衔接规则

每个阶段开始前：

1. 读取本计划。
2. 读取 `AGENTS.md`。
3. 如果存在对应 handoff，执行或模拟 `$task-handoff resume <task-id>` 的恢复流程：读 `.codex/tasks/<task-id>.md`、检查 git 状态、确认 scope safety。
4. 检查当前工作树中是否有不属于本阶段的未提交修改。

每个阶段结束前：

1. 完成代码、测试、真实 HTTP/SSE E2E、服务关闭和端口释放。
2. 按项目规范提交中文 commit。
3. 使用 `$task-handoff pause <task-id>` 写入或更新本阶段 handoff。
4. handoff 中必须写明：
   - 本阶段在主线中的位置。
   - 已完成能力。
   - 真实验证命令和结果。
   - 服务是否已关闭、端口是否释放。
   - 下一阶段 1 到 3 个可执行动作。
   - 允许写入范围、只读参考范围和禁止写入范围。

## 风险与控制

- Spring AI Alibaba Graph 多普通边是否真正并发需要通过小型 focused test 验证。若行为不稳定，先采用顺序执行的简化策略，但保留 `parallel_executor`、`researcher_n`、`coder_n` 命名和事件协议。
- 不要在本计划阶段同时引入 RAG、Redis、MCP、Docker executor 或前端；这些能力应建立在高级执行骨架稳定之后。
- 不要把主项目代码大段复制进 M-agent。优先复刻职责边界、状态语义和最小可运行行为。
- `PROCESSING` step 在主项目中由 `coder_n` 执行，M-agent 当前由 `ProcessorNode` 执行。迁移时需要明确兼容窗口，避免同一 step 被 processor 和 coder 重复执行。
- 真实模型输出可能不稳定。测试断言应关注节点序列、状态、持久化和事件协议，不应依赖完整自然语言文本。
- 所有 provider 异常写入事件 payload 前都要做非空 fallback，避免 `Map.of` null。

## 完成后的状态

本计划完成后，M-agent 应具备以下能力：

- 默认 Graph 运行路径包含高级执行骨架。
- Plan step 有稳定 id 和执行状态。
- `parallel_executor` 能分配 research 和 processing step。
- `researcher_n` 能执行 research step。
- `coder_n` 能执行 processing step 的最小真实模型路径。
- SSE 事件协议可支撑前端节点时间线。
- 直接回答、自动研究、人工计划门、接受恢复、拒绝重规划、停止、报告持久化和 session history 均不回归。

这时再进入 RAG、MCP、Docker coder、Redis 或前端，后续改动会落在清晰的扩展点上，而不是继续重写工作流主干。
