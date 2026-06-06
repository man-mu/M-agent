# 前端 RAG 上传真实聊天闭环 Phase 3 验证记录

## 验证时间

- 2026-06-06

## 阶段目标

- 验证用户上传文档后，同一 `session_id` 的真实深度研究聊天流程能够触发 `user_file_rag`。
- 验证 `user_file_rag` 读取到上传资料，并将资料中的关键词纳入最终报告。
- 验证会话历史和事件历史可持久化读取。

## 自动化测试

- 后端聚焦测试：
  - 命令：`mvn '-Dtest=top.lanshan.manmu.node.UserFileRagNodeTest,top.lanshan.manmu.rag.*Test' test`
  - JDK：`C:\WorkResources\JDKs\JDK17`
  - 结果：通过，5 个测试成功。
- 前端上传逻辑测试：
  - 命令：`npm run test:unit -- ragUpload.spec.ts`
  - 结果：通过，7 个测试成功。
- 前端构建：
  - 命令：`npm run build`
  - 结果：通过。

## 真实 HTTP / E2E 验证

- Docker PostgreSQL：`manmu-postgres` 为 `healthy`。
- 后端：JDK 17，`real-model` profile，`http://localhost:18080`。
- 前端：Vite，`http://localhost:5173`。
- 能力接口：
  - `GET /api/app/capabilities`
  - 返回 `ragEnabled=true`。

### 成功闭环证据

- 验证 session：`rag-e2e-session-phase3-20260606`
- 验证 thread：`rag-e2e-session-phase3-20260606-thread`
- 上传资料关键词：`RAG-FRONTEND-CHECK-20260606`
- 后端日志显示：
  - `VectorStoreDataIngestionService` 已写入 1 个 chunk 到该 session。
  - `UserFileRagNode` 按同一 session 检索。
  - `RagRetriever` 返回 5 条候选并按阈值过滤为 1 条命中。
- SSE 文件：`target/http-check/rag-phase3-chat.sse`
  - 出现 `node_name=user_file_rag` 的 started/completed 事件。
  - `displayTitle` 为“读取上传资料”。
  - `user_file_rag` payload 和最终报告都包含 `RAG-FRONTEND-CHECK-20260606`。
  - 出现 `event:done` 和 `event_type=graph.completed`。
- 会话历史：
  - `GET /api/sessions/rag-e2e-session-phase3-20260606/threads/rag-e2e-session-phase3-20260606-thread`
  - 返回 `status=COMPLETED`。
- 事件历史：
  - `GET /api/sessions/rag-e2e-session-phase3-20260606/threads/rag-e2e-session-phase3-20260606-thread/events`
  - 返回 23 条事件。
  - 其中 `user_file_rag` 事件 2 条。
  - 其中 `graph.completed` 事件 1 条。
- 报告接口：
  - `GET /api/reports/rag-e2e-session-phase3-20260606-thread`
  - 返回 `status=success`，报告内容包含 `RAG-FRONTEND-CHECK-20260606`。

### 本轮额外重试记录

- 额外使用 `rag-e2e-session` 重试上传时，接口返回 500。
- 日志显示真实原因是 DashScope embedding 请求读超时，上传流程已经读取文档并切出 1 个 chunk，但在向量写入时外部 embedding 调用超时。
- 该重试失败属于外部模型/embedding 服务波动，不改变上述成功闭环证据。

## 前端展示确认

- `MessageStore` 已将 `user_file_rag` 映射为“读取上传资料”。
- `/chat` 上传成功后已有提示：深度研究流程会读取上传资料。
- 快速回答模式下有轻量提示：开启深度研究可读取上传资料。

## 敏感信息检查

- 未读取、输出或提交 `.local/model-providers.json`。
- 未读取、输出或提交 `.local/mcp-keys.json`。
- 验证记录中不包含 API Key 或本地敏感凭证。

