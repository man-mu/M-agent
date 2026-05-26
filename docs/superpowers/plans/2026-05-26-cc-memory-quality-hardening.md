# CC 记忆与用户画像工程质量加固计划

> 本计划用于优化由 Claude Code / DeepSeek-V4-Pro 推进的对话记忆与用户画像相关工程质量。目标不是继续扩展新功能，而是先把已经进入代码库的记忆、画像、Reporter 注入能力做成可运行、可迁移、可测试、可维护的生产形态。

## 背景

当前项目已经在 `.codex` 旧 Graph 计划之后继续推进了记忆相关能力：

- Phase 1：新增 `conversation_messages` 数据层与 `ConversationMemoryService`。
- Phase 2：新增 `user_profiles` 数据层与 `UserProfileService`，并把用户画像注入 `CoordinatorNode`。
- Phase 3：部分代码已进入项目，包括结构化画像字段、`guide-reporter` 配置、`ReporterAgent` 参数扩展、`ReporterNode` 画像注入。

但当前实现存在几个工程质量风险：

- `UserProfileEntity` 已映射 `expertise_level`、`detail_preference`、`style_preference`，但迁移目录只到 `V5__create_user_profiles.sql`，缺少 `V6__alter_user_profiles.sql`。
- `ConversationMemoryService.saveMessage(...)` 暂未确认接入 `/chat/stream`、`/chat/resume` 等主流程，对话记忆可能没有真实数据来源。
- `UserProfileService` 在服务层直接 `.block()` R2DBC 仓库和模型调用，响应式边界比较粗，需要确认是否处于 boundedElastic 或改成响应式链路。
- JSON 提取失败、空字段、枚举值越界等情况缺少明确约束和测试。
- `ReporterAgent` 接口变更已经发生，需要确认所有生产代码和测试桩都适配。
- 现有计划与代码之间出现偏差，需要用测试、迁移和真实 E2E 把状态重新拉齐。

## 总体目标

1. 消除数据库 schema 与 Java 实体之间的不一致。
2. 让 conversation memory 真正接入用户请求/模型输出主链路。
3. 让用户画像提取和注入具备稳定失败降级，不影响主研究流程。
4. 降低阻塞调用对 WebFlux 执行链路的影响。
5. 补齐单元测试、集成测试和真实 HTTP/SSE 验证。
6. 保持当前项目边界：不引入 Redis、不引入完整复杂记忆系统、不泄露 `.local` 密钥。

## 非目标

- 不重写整个 Graph runner。
- 不引入 deepresearch-main 的完整长期记忆、置信度合并、用户画像枚举体系。
- 不新增前端。
- 不把记忆系统扩展成 RAG 或向量记忆。
- 不读取、不打印、不提交 `.local/model-providers.json`。

## 阶段 0：基线审计与故障复现

### 目标

在动代码前确认当前主干到底坏在哪里，避免凭感觉修。

### 主要检查

- 查看 `git status --short`，确认已有未提交变更，不覆盖用户或其他工具留下的修改。
- 检查迁移目录：
  - `V4__create_conversation_messages.sql`
  - `V5__create_user_profiles.sql`
  - 是否缺少 `V6__alter_user_profiles.sql`
- 检查实体字段：
  - `UserProfileEntity`
  - `UserProfileRecord`
  - `UserProfileRepository`
- 检查调用链：
  - `ChatController`
  - `GraphResearchRunner`
  - `CoordinatorNode`
  - `ReporterNode`
  - `ConversationMemoryService`
  - `UserProfileService`
- 搜索所有 `ReporterAgent` 实现和测试桩：
  - `rg -n "ReporterAgent|report\\(" src/main/java src/test/java`

### 验收

- 形成一份简短审计记录，列出当前必修问题和可延后问题。
- 至少能复现或静态确认 schema drift：实体字段多于数据库迁移字段。
- 不修改业务代码。

### 建议提交

`审计记忆画像工程质量问题`

## 阶段 1：修复数据库迁移与实体一致性

### 目标

优先修复最硬的生产风险：Java 实体字段和 PostgreSQL 表结构不一致。

### 主要改动

- 新增 `src/main/resources/db/migration/V6__alter_user_profiles.sql`：

```sql
ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS expertise_level VARCHAR(32),
    ADD COLUMN IF NOT EXISTS detail_preference VARCHAR(32),
    ADD COLUMN IF NOT EXISTS style_preference VARCHAR(32);
```

- 检查 `UserProfileEntity` 的 `@Column` 与 SQL 列名一致。
- 检查 `UserProfileRecord` JSON 字段名与 API 输出预期一致。
- 如测试资源或 H2/R2DBC 测试需要 schema 初始化，补齐对应测试迁移或测试配置。

### 测试

- focused tests：
  - `UserProfilePropertiesTest`
  - 新增或补齐 `UserProfileRepository` / `UserProfileService` 相关测试。
- 数据库迁移真实验证：
  - Docker PostgreSQL 启动。
  - 启动后端触发 Flyway。
  - 用 `docker exec manmu-postgres psql -U manmu -d manmu -c "\d user_profiles"` 确认 7 列存在。

### 验收

- `user_profiles` 表包含：
  - `id`
  - `session_id`
  - `profile_summary`
  - `expertise_level`
  - `detail_preference`
  - `style_preference`
  - `updated_at`
- 应用启动不再因列缺失失败。
- `mvn test` 通过。
- 本地后端测试完成后关闭服务，确认 `18080` 端口释放。

### 建议提交

`补齐用户画像结构化字段迁移`

## 阶段 2：打通 conversation memory 写入链路

### 目标

让 `conversation_messages` 不只是有表和服务，而是真正记录用户输入和助手输出，为用户画像提供可靠数据源。

### 设计原则

- 只记录必要文本，不记录 API Key、内部异常堆栈或敏感本地配置。
- 失败不阻断主研究流程：记忆保存失败应记录 warn，并继续 SSE。
- 避免重复保存：同一个 `threadId` 的同一阶段不要重复写入同一条 assistant 最终回答。

### 主要改动候选

- 在 `/chat/stream` 请求开始时保存用户消息：
  - `role=USER`
  - `sessionId`
  - `threadId`
  - `content=request.query()`
- 在研究完成时保存助手消息：
  - `role=ASSISTANT`
  - `content=最终 report 或 direct answer`
- 对 `/chat/resume`：
  - 若 `feedbackContent` 非空，可保存为 `USER` 消息，内容带上“用户反馈”语义。
  - resume 完成后保存最终 assistant 输出。
- 推荐接入层：
  - 优先在 `GraphResearchRunner` 的完成/错误边界附近接入，便于拿到最终 report。
  - 或在 `ChatController` 包装 SSE 时接入，但要避免流式多次写入。

### 测试

- 单元测试或集成测试覆盖：
  - `runChat` 开始时保存用户消息。
  - direct answer 完成后保存 assistant 消息。
  - auto research 完成后保存 assistant report。
  - memory 保存失败不影响 SSE done。
- Repository 测试确认按 session 查询、排序、窗口截断。

### 真实 E2E

- 启动 PostgreSQL 和后端。
- 调用 `/chat/stream` 完成一次 direct answer 或 auto accepted research。
- 查询 `conversation_messages`：
  - 同一 `session_id` 下存在 USER 和 ASSISTANT。
  - `thread_id` 与 SSE 返回一致。
- 调用第二轮同 session 请求，确认 `UserProfileService` 能读到历史消息。

### 验收

- `conversation_messages` 有真实主流程数据。
- 保存失败不会中断主研究。
- 不保存空消息。
- 不泄露 `.local` 密钥。

### 建议提交

`接入对话记忆主流程写入`

## 阶段 3：加固 UserProfileService

### 目标

让画像提取在模型输出不稳定、JSON 不合法、字段缺失、数据库失败时有明确降级策略。

### 主要改动

- 把 `ObjectMapper` 改为依赖注入，避免静态实例和测试替换困难。
- 增加字段规范化：
  - `expertise_level` 只接受 `beginner` / `intermediate` / `advanced`，否则为空或 `intermediate`。
  - `detail_preference` 只接受 `concise` / `balanced` / `comprehensive`。
  - `style_preference` 只接受 `practical` / `theoretical` / `mixed`。
- 对 `profile_summary` 设置 fallback：
  - JSON 缺失 summary 时使用 `general user`。
  - 原始模型输出过长时截断，避免污染 prompt 和数据库。
- 让 `formatProfileContext(...)` 输出稳定、简洁、可读，避免拼出空括号或多余逗号。
- 明确缓存策略：
  - 缓存命中时不调用 LLM。
  - 缓存过期时允许基于旧画像增量更新。
- 对 `AgentClient.call(...)` 异常只降级，不打断主流程。

### 响应式边界

当前服务通过 `.block()` 调用 R2DBC。需要二选一：

- 保守方案：保留同步接口，但确保只在 `Flux.defer(...).subscribeOn(Schedulers.boundedElastic())` 或已有 boundedElastic 执行链路中调用，并在代码注释中说明原因。
- 改良方案：新增响应式方法 `Mono<String> getOrCreateProfileReactive(String sessionId)`，`CoordinatorNode` / `ReporterNode` 使用 reactive 链路组合。

推荐优先采用改良方案；如果影响面过大，先做保守方案并补测试。

### 测试

- 缓存命中不调用 `AgentClient`。
- 无历史消息返回空画像。
- LLM 返回合法 JSON 时正确入库四个字段。
- LLM 返回非法 JSON 时不会抛出到主流程。
- LLM 返回未知枚举值时被规范化。
- 旧画像存在且过期时，prompt 包含 previous profile。

### 验收

- `UserProfileService` 的失败模式可预期。
- 不因画像提取失败导致研究失败。
- 画像上下文长度受控。
- `mvn test` 通过。

### 建议提交

`加固用户画像提取与降级策略`

## 阶段 4：统一 Coordinator 与 Reporter 的画像注入方式

### 目标

避免 Coordinator 和 Reporter 各自随意拼接用户画像，形成稳定的 prompt 注入边界。

### 主要改动

- 明确 `UserProfileService` 返回的是“可直接注入 prompt 的简短上下文”，还是返回结构化对象。
- 若继续返回 String：
  - `CoordinatorNode` 和 `ReporterNode` 都只获取一次。
  - prompt 文案统一，例如：
    - `User profile context: ...`
    - `Use this only to adapt explanation depth and style. Do not infer facts not present in research evidence.`
- 若改为结构化对象：
  - 新增轻量 record，例如 `UserProfileContext`。
  - `LlmCoordinatorAgent` 和 `LlmReporterAgent` 各自决定如何拼 prompt。
- `ReporterAgent` 接口变更后，确认所有测试桩和 lambda 实现已同步。

### 测试

- `LlmCoordinatorAgentTest` 覆盖 user profile context 被注入。
- `LlmReporterAgentTest` 覆盖 user profile context 被注入。
- `ReporterNodeTest` 覆盖：
  - `guideReporter=true` 时调用 `UserProfileService`。
  - `guideReporter=false` 时不调用。
  - `UserProfileService` 返回空时仍正常生成报告。

### 验收

- Coordinator 和 Reporter 画像注入一致且可测。
- `ReporterAgent` 只有一个生产实现，所有测试编译通过。
- 用户画像只影响表达方式，不覆盖研究证据。

### 建议提交

`统一画像上下文注入边界`

## 阶段 5：补齐测试矩阵

### 目标

把当前“看起来能跑”的记忆/画像代码变成可回归的工程能力。

### 必补测试

- `MemoryPropertiesTest`
  - 默认值。
  - 配置绑定。
- `UserProfilePropertiesTest`
  - `enabled`
  - `maxMessagesForExtraction`
  - `cacheMinutes`
  - `guideReporter`
- `PostgresConversationMemoryServiceTest`
  - 保存消息。
  - 按 session 查询排序。
  - `formatConversationHistory` 截断。
- `UserProfileServiceTest`
  - 合法 JSON。
  - 非法 JSON。
  - 缓存命中。
  - AgentClient 异常。
  - 空 session / 空历史。
- `CoordinatorNodeTest`
  - 画像注入不破坏 direct answer / deep research route。
- `ReporterNodeTest`
  - Reporter 画像开关。
- `GraphResearchRunnerTest`
  - 完成后写入 conversation memory。
  - memory 失败不影响完成事件。

### 验收

- focused tests 通过。
- `mvn test` 通过。
- 测试中不使用真实 `.local/model-providers.json` 密钥。
- 对 AgentClient 使用测试替身，不做无必要真实模型调用。

### 建议提交

`补齐记忆画像质量回归测试`

## 阶段 6：真实 HTTP/SSE 与 PostgreSQL E2E 验证

### 目标

按项目要求验证生产形态路径，而不是只停留在单元测试。

### 前置

- Docker Desktop 正常运行。
- `manmu-postgres` 容器健康。
- `.local/model-providers.json` 已有可用模型供应商 key，但测试过程不读取、不打印。
- Maven 命令前临时设置：

```powershell
$env:JAVA_HOME = 'C:\WorkResources\JDKs\JDK17'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

### 验证路径

1. 启动后端：

```powershell
mvn -B spring-boot:run -Dspring-boot.run.profiles=real-model "-Dspring-boot.run.arguments=--server.port=18080"
```

2. 写入请求体到 `target/http-check/memory-quality-request.json`。

3. 使用 `curl.exe --data-binary "@target/http-check/memory-quality-request.json"` 调用 `/chat/stream`。

4. 检查 SSE：
   - 有 `message`。
   - 最终有 `done`。
   - 没有 schema 相关错误。

5. 查询 PostgreSQL：
   - `conversation_messages` 有 USER / ASSISTANT。
   - `user_profiles` 有结构化画像字段。
   - `research_session_histories` 为 `COMPLETED`。
   - `research_reports` 可读取。

6. 第二轮同 session 请求：
   - 验证画像缓存或更新逻辑生效。
   - Reporter 输出仍基于研究证据，不凭画像编造事实。

7. 停止服务：

```powershell
$pid = (Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue).OwningProcess
if ($pid) { Stop-Process -Id $pid -Force }
Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue
```

### 验收

- 真实 HTTP/SSE 完成。
- PostgreSQL 四类数据可查：
  - conversation memory
  - user profile
  - report
  - session history
- 服务停止后 18080 端口释放。
- E2E 输出文件保存在 `target/http-check/`，不提交。

### 建议提交

`验证记忆画像真实链路`

## 阶段 7：清理文档与计划偏差

### 目标

把 `docs/superpowers/plans` 中 Phase 1/2/3 与真实代码状态对齐，避免后续执行者误读。

### 主要改动

- 在 Phase 3 计划顶部追加当前状态说明：
  - 哪些任务已部分实现。
  - 哪些任务仍缺失，例如 V6 迁移、测试、E2E。
- 如已有代码选择不同于原计划，记录原因。
- 在本计划末尾补充最终验收结果和遗留风险。

### 验收

- 后续工程师能从文档判断：
  - 当前做到哪一步。
  - 哪些问题已修。
  - 哪些问题还没修。
- 不再出现“计划要求 V6，但代码已经用字段、迁移却没有”的状态。

### 建议提交

`同步记忆画像计划与实现状态`

## 总体验收清单

- [ ] 数据库迁移到 V6，真实 PostgreSQL 表结构与实体一致。
- [ ] `/chat/stream` 至少保存 USER 和最终 ASSISTANT 消息。
- [ ] `UserProfileService` 能从真实 conversation messages 提取画像。
- [ ] Coordinator 和 Reporter 都能稳定接收画像上下文。
- [ ] 画像提取失败不影响主研究流程。
- [ ] `mvn test` 通过。
- [ ] 真实 HTTP/SSE + PostgreSQL + 真实模型供应商 E2E 通过。
- [ ] 测试结束后服务关闭，`18080` 端口释放。
- [ ] 未读取、打印或提交 `.local/model-providers.json`。
- [ ] 每个阶段按项目约束使用中文 commit。

## 建议执行顺序

优先级从高到低：

1. 阶段 1：修复 V6 迁移。
2. 阶段 2：打通 conversation memory 写入链路。
3. 阶段 3：加固 `UserProfileService`。
4. 阶段 4：统一画像注入。
5. 阶段 5：补测试。
6. 阶段 6：真实 E2E。
7. 阶段 7：同步文档。

如果时间有限，至少先完成阶段 1、2、5、6。这样能先把“代码能不能在真实数据库和真实 HTTP 链路跑起来”这件事定住。

## 执行结果（2026-05-26）

### 当前状态

- 阶段 0 已完成：形成基线审计记录 `2026-05-26-cc-memory-quality-baseline-audit.md`。审计时确认当前代码树已存在 `V6__alter_user_profiles.sql`，原计划中“缺少 V6”的风险在执行前已由既有代码部分解决。
- 阶段 1 已完成：补充迁移一致性静态测试，并在真实 PostgreSQL 启动链路中确认 Flyway 当前 schema 版本为 `6`，`user_profiles` 结构化字段可用。
- 阶段 2 已完成：`GraphResearchRunner` 已接入 conversation memory 主流程写入，覆盖 `/chat/stream` USER、最终 ASSISTANT，以及 `/chat/resume` 用户反馈消息；保存失败降级为 warn，不阻断 SSE。
- 阶段 3 已完成：`UserProfileService` 已加固 JSON 解析、字段规范化、summary fallback、上下文截断、AgentClient 异常降级和缓存命中逻辑。当前仍保留同步入口，调用边界依赖 Graph runner 的 `boundedElastic` 执行链路。
- 阶段 4 已完成：Coordinator 与 Reporter 使用统一画像 prompt 边界，明确画像只影响解释深度和表达风格，不替代研究证据。
- 阶段 5 已完成：补齐记忆/画像质量回归测试；默认 `mvn test` 不再读取本地真实模型 key，真实模型测试改为 `MANMU_RUN_REAL_MODEL_TESTS=true` 时显式启用。
- 阶段 6 已完成：新增 `2026-05-26-cc-memory-quality-e2e-verification.md` 记录真实 HTTP/SSE + PostgreSQL + 真实模型供应商验证结果。原始请求、SSE、HTTP 响应和数据库查询输出保存在 `target/http-check/phase6-*`，不提交。
- 阶段 7 已完成：本节同步计划偏差和最终验收状态。

### 与原计划的偏差

- V6 迁移：原计划以“缺少 V6”为风险描述，但阶段 0 审计发现代码树已存在 V6。实际处理改为补充测试和真实 PostgreSQL 验证，防止后续回归。
- 响应式改造：未引入新的 reactive `UserProfileService` API。考虑到当前 Graph 执行已在 `boundedElastic` 中运行，本轮采用保守加固策略：保留同步服务边界，补充代码说明和失败降级测试，避免扩大改动面。
- 真实模型测试：默认单元测试不再从 `.local/model-providers.json` 读取 key；需要真实供应商时通过显式环境变量或本地后端真实运行路径验证。

### 最终验收

- [x] 数据库迁移到 V6，真实 PostgreSQL 表结构与实体一致。
- [x] `/chat/stream` 至少保存 USER 和最终 ASSISTANT 消息。
- [x] `UserProfileService` 能从真实 conversation messages 提取画像。
- [x] Coordinator 和 Reporter 都能稳定接收画像上下文。
- [x] 画像提取失败不影响主研究流程。
- [x] `mvn test` 通过。
- [x] 真实 HTTP/SSE + PostgreSQL + 真实模型供应商 E2E 通过。
- [x] 测试结束后服务关闭，`18080` 端口释放。
- [x] 未手动打开、打印或提交 `.local/model-providers.json`；验证输出和提交记录不包含密钥。
- [x] 每个阶段按项目约束使用中文 commit。

### 遗留风险

- `UserProfileService` 仍是同步入口；如果后续把画像提取移出 Graph runner 的 `boundedElastic` 边界，需要优先改造成响应式 API。
- 当前 E2E 主要覆盖直答路径。深度研究路径已由单测和前序阶段验证支撑，后续若扩展 Reporter 能力，建议补一条低成本真实 deep research 场景。
