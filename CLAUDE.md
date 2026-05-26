# 项目级 CLAUDE 指令

## 项目概况

- 这是一个 Java 17 / Maven / Spring Boot 3.4.x 项目。
- 当前项目是 DeepResearch 风格的精简后端，主包名为 `top.lanshan.manmu`。
- 后端使用 WebFlux SSE 输出研究工作流进度。
- 当前运行路径使用真实大模型调用，不保留 mock agent / mock search fallback。

## 本地命令

- 执行 Maven 命令前，优先使用 `JAVA_HOME=C:\WorkResources\JDKs\JDK17`。
- 验证命令：`mvn test`。

## 本地文件与凭证

- `.local/` 被 `.gitignore` 忽略，用于保存本地模型供应商 API Key 等敏感配置。
- `target/`、`.idea/` 被 `.gitignore` 忽略，不应提交。
- 不要把 `.local/model-providers.json` 中的 API Key 写入源码、测试断言或提交记录。
- 不用管 `.claude/` 文件夹；不要修改、删除或提交它，除非用户明确要求。

## 计划执行偏好

- 执行实现计划时统一使用 **Inline Execution**（`superpowers:executing-plans`），不使用 Subagent-Driven Development。
- 计划保存在 `docs/superpowers/plans/` 目录下。

## 语言偏好

- 思考、分析和内部推理过程使用英文。
- 最终回复用户时使用中文，除非用户明确要求使用其他语言。

## 开发约束

- 新增或修改说明文件时使用中文。
- 每完成一个小的阶段任务就提交一次，commit 说明使用中文。
- 开发新功能时禁止使用 mock，必须接入真实生产环境需要的数据。
- 与 DeepResearch 主项目对齐时，优先做精简版可运行能力，不要一次性搬入 RAG、MCP、Redis、前端等完整复杂模块。
- 以下模块已确认现有实现足够，不再向 `deepresearch-main` 参考项目对齐：搜索能力（多搜索引擎/智能选择）、智能体系统（问题分类/领域专项 Agent/反思机制）、代码执行（Python 沙箱/Docker 执行）、RAG 增强（混合检索/HyDe/RRF 融合/批量上传）。
- 保持真实 API 调用路径可测；涉及 DashScope / DeepSeek 的测试依赖本地 `.local/model-providers.json` 中已有 key。
- 每完成一个阶段任务进行验证时，不能只停留在单元测试或 `mvn test` 成功；必须在本地启动后端服务，使用 `curl` 通过真实 HTTP API、真实数据库/中间件和真实模型供应商路径做生产环境形态的全链路测试，尽早暴露线程、序列化、配置、网络、持久化和流式响应等问题，避免为后续开发埋雷。
- 每次启动后端服务进行测试后，测试完毕必须关闭该服务。

## 近期踩坑记录

- 在 PowerShell 中执行 Maven 选择多个测试类时，`-Dtest=ClassA,ClassB` 需要整体加引号，例如 `mvn '-Dtest=ClassA,ClassB' test`，否则逗号会被 PowerShell 当成参数列表解析。
- 在 Windows PowerShell 中使用 `rg` 搜索 `target/http-check/review-*.sse` 这类通配路径可能触发 `os error 123`；优先搜索目录并用 `-g "review-*.sse"` / `-g "review-*.json"` 过滤文件名。
- 在 PowerShell 中给 `curl` 传 JSON 时，优先把请求体写到 `target/http-check/*.json`，再用 `curl.exe --data-binary "@target/http-check/request.json"`；不要依赖复杂内联转义，容易把 JSON 写坏导致 400。
- PowerShell here-string 的 `@'` / `'@` 必须单独成行；短 JSON 更适合用普通字符串写入文件。
- 真实 HTTP/SSE 验证时，如果默认 DashScope 遇到限流，可以通过 `/api/model/switch` 临时切到 DeepSeek 等已有 key 的供应商继续验证；不要修改或泄露 `.local/model-providers.json`。
- 不要只看 SSE error 的表面内容。Runner 可能把底层异常包装成 SSE 错误，日志里也可能没有完整堆栈；需要结合 `target/*run*.log`、SSE 输出文件和聚焦单测定位真实原因。
- Java 的 `Map.of(...)` 不允许 key 或 value 为 `null`。捕获外部模型、网络、超时异常时，`Throwable#getMessage()` 可能为 `null`，写入事件 payload、状态或日志前要先做 fallback，例如使用异常类名。
- Graph 执行流的最终输出或状态对象不要默认假设一定非空；收尾保存、暂停恢复等逻辑应尽量从最新 graph state 读取，并对终止输出做空值保护。
- 启动本地后端做验证时，记录 PID 到 `target/`，测试结束必须停止进程，并用 `Get-NetTCPConnection -LocalPort 18080` 确认端口已经释放。
- 真实全链路验证完成后，除了检查 SSE `done`，还要通过报告和会话历史接口确认 PostgreSQL 持久化状态，例如报告可读取、线程状态为 `COMPLETED` / `STOPPED`。

## 工程质量守则

以下是从过往实践中总结的通用守则，每次实现新功能时应当遵循，不要依赖事后审计或外部工具来修正。

### 输入防护

- **LLM 输出的结构化字段必须有白名单校验。** 不要假设 LLM 一定会返回 `"beginner"` 而不是 `"expert"` 或其他非规范值。用 `Set` 白名单 + 忽略无效值（而非抛出异常）的方式降级。
- **所有来自 LLM 的文本都需要长度截断。** 设置 `MAX_*_CHARS` 常量限制 profile summary、conversation context、prompt 注入内容等，防止 LLM 异常输出导致 prompt 膨胀或 DB 溢出。
- **Throwable.getMessage() 做 null fallback。** 已知此坑但容易遗忘。每个异常日志或事件写入点都应用 `error.getClass().getSimpleName()` 兜底。建议统一封装为 `safeErrorMessage(Throwable)` 工具方法。

### Prompt 护栏

- **用户上下文注入 prompt 时必须附带行为约束。** 注入画像、偏好、会话历史等外部信息时，同时告诉 LLM："Use this only to adapt explanation depth and style. Do not infer facts not present in research evidence." 否则 LLM 可能把画像当成研究结论的来源。
- **用户角色提示应放在 system prompt 或独立的标记段中**，与 query/plan/research evidence 等事实数据隔离开。

### 测试基础设施

- **新增测试类必须聚焦可验证的行为变更。** 例如 phase 3 新增 `LlmReporterAgentTest` 验证 profile guardrail 是否真的写入了 prompt，而不是泛泛的"报告生成了"。

### 功能闭环

- **新增持久化逻辑时必须接入主流程。** 例如实现 conversation memory 写入，不只是定义 `saveMessage()` 方法，而必须在 `GraphResearchRunner.runChat()` / `resume()` 中实际调用它并完成端到端验证。
- **非关键路径的失败不应该阻断主流程。** conversation memory 写入、用户画像提取等辅助能力使用 `.onErrorResume` 降级，记录日志后静默返回。
- **实现完成后盘点：新增的代码在哪些调用链中被使用？** 如果写了一个方法但没有任何调用方，那不是完成。
