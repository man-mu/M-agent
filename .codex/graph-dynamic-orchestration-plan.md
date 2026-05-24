# Graph 动态编排迁移计划

## 背景

当前项目已经完成 Graph 迁移前置节点化工作：

- `coordinator`：负责深度研究与直接回答的预路由。
- `plan_validator`：负责 planner 后的计划校验、重试、人工反馈、研究执行路由。
- `human_feedback`：负责等待反馈、接受继续、拒绝重规划、达到迭代上限继续执行。
- `research_team`：负责在 `researcher`、`processor`、`reporter` 之间路由。

下一阶段目标是引入 Spring AI Alibaba Graph 动态编排当前已实现节点。迁移原则是先做等价 Graph 编排，不引入 RAG、MCP、Redis、前端、专业知识库、coder agent 或完整并行执行。

## 总体策略

- 新增 `GraphResearchRunner`，先与 `SimpleResearchRunner` 并行存在。
- 复用现有 `ResearchNode`、agent、search、report、session history、session context 服务。
- Controller 先依赖统一 runner 接口，再通过配置选择 `simple` 或 `graph`。
- Graph 跑通并成为默认路径后，再单独安排删除 `SimpleResearchRunner`。
- 每个阶段必须独立完成：代码、测试、真实 HTTP/SSE E2E、关闭服务、中文 commit。

## 阶段 1：引入 Graph 依赖与 Runner 抽象

### 目标

为 Graph runner 预留入口，但不改变现有默认运行路径。

### 主要改动

- 在 `pom.xml` 引入 Spring AI Alibaba Graph 依赖，优先使用当前项目已有 `spring-ai-alibaba.version=1.0.0.4` 对应版本。
- 新增统一接口，例如 `ResearchRunner`：
  - `run(ResearchRequest request)`
  - `runChat(ResearchRequest request, String sessionId)`
  - `runUntilPlanGate(ResearchRequest request, String sessionId)`
  - `resume(String threadId, ResumeDecision decision)`
  - `stopAndRecord(String threadId)`
- 让 `SimpleResearchRunner` 实现该接口。
- Controller 改为依赖 `ResearchRunner` 接口。
- 新增配置项：

```yaml
mvp:
  research:
    runner: simple
```

### 验收

- 默认仍走 `SimpleResearchRunner`。
- `/api/research/stream`、`/chat/stream`、`/chat/resume`、`/chat/stop` 行为不变。
- `mvn test` 通过。
- 启动后端，使用 curl 跑一条现有简单 SSE 流，确认接口未回归。
- 测试后关闭服务并确认端口释放。

### 建议提交

`抽象研究运行器接口`

## 阶段 2：建立 Graph 状态适配层

### 目标

让 Graph state 能承载现有 `ResearchState` 和 SSE 事件，而不急于拆散所有字段。

### 主要改动

- 新增包：`top.lanshan.manmu.graph`。
- 新增状态 key 常量，例如 `ResearchGraphStateKeys`：
  - `research_state`
  - `events`
  - `terminal_status`
  - `resume_decision`
- 新增工具类，例如 `ResearchGraphState`：
  - 从 `ResearchRequest` 创建 `ResearchState`。
  - 从 Graph state 读取/写入 `ResearchState`。
  - 追加 `ResearchEvent`。
  - 读取最新路由状态。
- 暂时保持 `ResearchState` 为业务主状态，Graph state 只做编排容器。

### 验收

- 纯单元测试覆盖：
  - 创建 graph state。
  - 读取/写入 `ResearchState`。
  - 追加事件。
  - 保存 resume decision。
- 不接入真实 Graph runner。
- `mvn test` 通过。

### 建议提交

`添加图状态适配层`

## 阶段 3：封装通用节点适配器

### 目标

把现有 `ResearchNode` 包成 Graph node/action，避免重写业务逻辑。

### 主要改动

- 新增通用适配器，例如 `ResearchNodeGraphAction`。
- 适配逻辑：
  - 从 Graph state 读取 `ResearchState`。
  - 调用现有 `ResearchNode.run(state)`。
  - 收集节点产生的 `ResearchEvent`。
  - 把事件追加回 Graph state。
  - 返回更新后的 Graph state map。
- 对以下普通节点优先验证：
  - `rewrite_multi_query`
  - `background_investigator`
  - `planner`
  - `information`
  - `researcher`
  - `processor`
  - `reporter`

### 验收

- 单元测试用 stub `ResearchNode` 验证 adapter 能更新状态和事件。
- 不改变现有 runner 默认行为。
- `mvn test` 通过。

### 建议提交

`添加研究节点图适配器`

## 阶段 4：实现最小 GraphResearchRunner 自动完成路径

### 目标

先跑通无需人工暂停的自动完成路径。

### 主要改动

- 新增 `GraphResearchRunner`。
- 新增 `ResearchGraphConfiguration` 或等价图构建类。
- 先支持这些动态边：
  - `coordinator -> __END__`：直接回答。
  - `coordinator -> rewrite_multi_query`：深度研究。
  - `planner -> plan_validator`。
  - `plan_validator -> planner`：无效计划且仍可重试。
  - `plan_validator -> information`：有效且自动通过。
  - `information -> research_team`。
  - `research_team -> researcher | processor | reporter`。
  - `researcher -> research_team`。
  - `processor -> research_team`。
  - `reporter -> __END__`。
- `GraphResearchRunner.run(...)` 和 `runChat(...)` 先支持自动完成。
- 报告保存和 session history 更新保持与当前行为一致。

### 验收

- Graph runner focused tests 覆盖：
  - direct answer。
  - auto accepted research completion。
  - invalid plan retry。
  - research team 到 researcher/processor/reporter 的循环。
- 配置 `mvp.research.runner=graph` 时，自动完成路径可用。
- `mvn test` 通过。
- 启动后端，用 curl 跑 `/api/research/stream` 或 `/chat/stream` 自动完成路径。
- 验证 SSE 节点序列、报告存在、session history 为 `COMPLETED`。
- 测试后关闭服务并确认端口释放。

### 建议提交

`添加图编排自动研究路径`

## 阶段 5：支持 Graph 人工暂停与接受恢复

### 目标

让 Graph runner 支持 `auto_accepted_plan=false` 的暂停，以及接受反馈后继续执行。

### 主要改动

- Graph runner 增加 paused graph state 管理。
- `plan_validator -> human_feedback` 时：
  - 运行 `human_feedback` 节点。
  - 保存 paused state。
  - session history 标记 `PAUSED`。
  - SSE 输出 `human_feedback waiting`。
- `/chat/resume` 接受反馈时：
  - 找回 paused graph state。
  - 写入 accepted decision。
  - 从 `human_feedback` 继续到 `information`。
  - 完成后保存报告并标记 `COMPLETED`。

### 验收

- Graph runner tests 覆盖：
  - manual plan gate pauses at `human_feedback`。
  - accepted resume continues without replanning。
  - accepted resume can complete and save report。
- `mvn test` 通过。
- 真实 E2E：
  - `/chat/stream` 暂停到 `human_feedback`，history 为 `PAUSED`，report 不存在。
  - `/chat/resume` with `feedback=true` 后完成，history 为 `COMPLETED`，report 存在。
- 测试后关闭服务并确认端口释放。

### 建议提交

`支持图编排人工接受恢复`

## 阶段 6：支持 Graph 拒绝反馈、重规划与停止

### 目标

补齐当前 chat 生命周期：拒绝反馈重规划、达到迭代上限继续、运行中 stop、暂停后 stop。

### 主要改动

- `/chat/resume` 拒绝反馈：
  - `human_feedback -> planner`。
  - planner 使用 feedback content。
  - `plan_validator -> human_feedback` 后再次暂停。
- 达到 `max_plan_iterations` 时：
  - `human_feedback -> information`，避免无限重规划。
- stop 行为：
  - running graph execution 可停止。
  - paused graph state 可清理。
  - session history 标记 `STOPPED`。

### 验收

- Graph runner tests 覆盖：
  - rejected resume replans and pauses again。
  - rejected resume at max iterations continues to research execution。
  - stop running graph。
  - stop paused graph。
  - missing paused state returns `human_feedback` error。
- `mvn test` 通过。
- 真实 E2E：
  - `/chat/resume` with `feedback=false` 后重新规划并回到 `PAUSED`。
  - stop 路径能标记 `STOPPED`。
- 测试后关闭服务并确认端口释放。

### 建议提交

`补齐图编排反馈和停止流程`

## 阶段 7：Graph 成为默认 Runner

### 目标

把默认运行路径切到 Graph，但保留 `SimpleResearchRunner` 作为临时 fallback。

### 主要改动

- 默认配置改为：

```yaml
mvp:
  research:
    runner: graph
```

- Controller 不感知具体 runner。
- 测试中保留 simple 和 graph 两套关键行为覆盖。
- 文档或 handoff 中明确：simple runner 只作为短期回退路径。

### 验收

- `mvn test` 通过。
- 真实 E2E 至少覆盖：
  - direct answer。
  - auto accepted full research。
  - manual pause。
  - accepted resume。
  - rejected resume/replan。
  - stop。
- 所有真实验证后关闭服务并确认端口释放。

### 建议提交

`默认启用图编排运行器`

## 阶段 8：移除 SimpleResearchRunner

### 目标

在 Graph 默认路径稳定后，删除旧串行 runner。

### 前置条件

- Graph runner 已作为默认路径通过完整真实 E2E。
- Controller 和测试不再依赖 `SimpleResearchRunner`。
- Graph runner 已覆盖 `SimpleResearchRunnerTest` 中的核心行为。
- 暂停、恢复、停止、报告持久化、session history、错误事件均已验证。

### 主要改动

- 删除 `SimpleResearchRunner`。
- 删除或迁移 simple 专属测试。
- 移除 `mvp.research.runner=simple` fallback 配置。
- 清理不再使用的 helper 和测试 stub。

### 验收

- `mvn test` 通过。
- 真实 HTTP/SSE E2E 全部通过。
- 服务关闭且端口释放。
- git diff 不包含无关重构。

### 建议提交

`移除串行研究运行器`

## 风险与控制

- Spring AI Alibaba Graph API 版本可能和示例文档存在差异。优先查官方文档和当前 Maven 依赖源码，再实现最小可运行版本。
- Graph state 不要过早拆散 `ResearchState`，第一轮以封装现有状态为主。
- 不要在 Graph 迁移阶段同时引入 RAG、Redis、MCP、并行 executor 或前端。
- 每个阶段都必须真实启动后端做 curl SSE 验证，不能只依赖单元测试。
- 每次真实验证后必须停止后端服务并确认端口释放。

## 推荐执行顺序

1. `add-research-runner-interface`
2. `add-graph-state-adapter`
3. `add-graph-node-adapter`
4. `add-graph-auto-research-runner`
5. `add-graph-human-feedback-accept-resume`
6. `add-graph-feedback-replan-stop`
7. `switch-default-runner-to-graph`
8. `remove-simple-research-runner`