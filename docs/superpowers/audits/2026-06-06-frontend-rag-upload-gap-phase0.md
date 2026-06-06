# 前端 RAG 文档上传缺口 Phase 0 审计

## 审计范围

- 后端：`RagDataController`、`VectorStoreDataIngestionService`、`RagRetriever`、`UserFileRagNode`、`ResearchRunnerConfiguration`、`ResearchGraphBuilder`、`application.yml`。
- 前端：`views/chat/index.vue`、`services/api/chat.ts`、`services/api/app.ts`、`composables/useFileUploadHandler.ts`、`store/ConversationStore.ts`、`store/MessageStore.ts`、`router/defaultRoutes.ts`。
- 未读取 `.local/model-providers.json`、`.local/mcp-keys.json` 或任何 API Key 文件。

## 当前事实

- `/api/rag/upload` 位于 `RagDataController`，由 `@ConditionalOnProperty(prefix = "mvp.rag", name = "enabled", havingValue = "true")` 控制；`application.yml` 当前默认 `mvp.rag.enabled=true`。
- 上传接口接收 `file`、可选 `session_id`、可选 `user_id`；空白 `session_id` 会回退到 `__default__`，空白 `user_id` 会回退到 `anonymous`。
- `VectorStoreDataIngestionService` 使用 `TikaDocumentReader` 读取上传资源，使用 `TokenTextSplitter` 切块，写入向量库时会补充 `source_type=user_upload`、`session_id`、`user_id`、`original_filename`、`upload_timestamp`、`file_size` 等 metadata。
- `UserFileRagNode` 节点名为 `user_file_rag`，按 `source_type=user_upload` 和当前 `ResearchState.sessionId()` 检索上传资料；检索到内容后会追加到 state observation。
- `ResearchGraphBuilder` 只在 `mvp.rag.enabled=true` 且存在 `user_file_rag` 节点时，把该节点插入 `rewrite_multi_query` 与 `background_investigator` 之间。
- `ChatController` 使用请求中的 `session_id` 构造 `GraphId`，再把同一个 session id 传给 runner；前端 `chatService.stream(...)` 当前会发送 `session_id`。
- `/chat` 草稿会话在发送消息时通过 `ConversationStore.newOne(...)` 生成 UUID，并由 `ensureConversation()` 路由到 `/chat/{sessionId}`；上传入口后续应复用同一套 session 生成和绑定逻辑。
- `ui-vue3/src/composables/useFileUploadHandler.ts` 当前没有任何页面引用；它会直接调用 `/api/rag/upload`，但在没有会话 ID 时会上传到 `__default__`，且文案存在乱码，调用入口类型也较模糊。
- `defaultRoutes.ts` 当前只有 `/chat`、`/skills`、`/mcp`、`/settings`，没有独立知识库或文档上传页面。
- `MessageStore` 已有 `user_file_rag` 工作流节点映射文案，前端时间线具备展示该节点的基础能力。

## 风险与缺口

- 后端上传接口当前通过 `file.content().collectList()` 拼接字节数组，未看到显式大小限制、空文件拒绝或 `DataBuffer` 释放处理；后续 Phase 4 适合做轻量 hardening。
- 当前 RAG 节点只位于深度研究路径：如果协调器走 `DIRECT_ANSWER`，Graph 会直接结束，不会进入 `rewrite_multi_query` 和 `user_file_rag`。前端需要提示上传资料主要用于深度研究流程，或后续另行调整快速回答路径。
- 前端没有可操作上传入口，也没有展示上传状态、chunks、错误、当前会话绑定关系。
- 旧上传 composable 不应继续以未接入、乱码、可能错绑 `__default__` 的状态存在；后续应迁移为明确的 RAG API service 与上传状态模型。

## 后续改动边界

- Phase 1 优先新增 `ui-vue3/src/services/api/rag.ts` 与 `ui-vue3/src/views/chat/ragUpload.ts`，并用纯逻辑测试覆盖响应解析、错误清洗、文件边界和会话绑定提示。
- Phase 2 在 `ui-vue3/src/views/chat/index.vue` 的 composer 附近接入紧凑上传入口；上传前必须确保 session id 与后续聊天请求一致。
- 如需保存上传记录，仅保存文件名、chunks、状态、上传时间等非敏感元信息，不保存文件内容，不展示完整本地路径。

## Phase 0 测试与真实验证结果

- `git status --short`：阶段开始前无未提交变更；审计后仅新增本文件。
- 后端聚焦测试：使用 JDK 17 执行 `mvn '-Dtest=top.lanshan.manmu.rag.*Test,top.lanshan.manmu.node.UserFileRagNodeTest,top.lanshan.manmu.api.AppInfoControllerTest' test`，结果 `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`。
- 前端聚焦测试：在 `ui-vue3` 执行 `npm run test:unit -- skillPicker.spec.ts`，结果 `1 passed`、`3 tests passed`。
- Docker/PostgreSQL：`manmu-postgres` 为 `Up ... (healthy)`，`5432` 已映射到 localhost。
- 后端真实验证：使用 JDK 17 和 `real-model` profile 启动到 `18080`，`curl.exe http://localhost:18080/api/app/capabilities` 返回 `ragEnabled=true`。
- RAG 上传真实验证：使用 `target/http-check/rag-sample.txt` 调用 `curl.exe -F "file=@target/http-check/rag-sample.txt" -F "session_id=rag-audit-session" http://localhost:18080/api/rag/upload`，响应为 `status=success`、`session_id=rag-audit-session`、`chunks=1`、`file_name=rag-sample.txt`。
- 收尾：已停止本次验证启动的后端服务；`Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue` 无监听，端口已释放。
