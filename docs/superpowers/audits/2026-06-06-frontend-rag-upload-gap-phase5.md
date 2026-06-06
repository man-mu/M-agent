# 前端 RAG 上传全链路 Phase 5 验证记录

## 验证时间

- 2026-06-06

## 阶段目标

- 完成前端 RAG 文档上传缺口的全链路验收。
- 验证自动化测试、前端构建、真实后端上传、真实深度研究 SSE、持久化接口和浏览器布局。
- 验证结束后关闭本次启动的后端和前端服务，并确认 `18080` / `5173` 无监听。

## 自动化测试

- 后端全量测试：
  - 命令：`mvn test`
  - JDK：`C:\WorkResources\JDKs\JDK17`
  - 结果：通过，`Tests run: 275, Failures: 0, Errors: 0, Skipped: 3`。
- 前端单元测试：
  - 命令：`npm run test:unit`
  - 结果：通过，`12` 个测试文件、`68` 个测试通过。
- 前端构建：
  - 命令：`npm run build`
  - 结果：通过，`vue-tsc` 与 `vite build` 均成功。

## 真实 HTTP / E2E 验证

- Docker PostgreSQL：
  - `manmu-postgres` 为 `healthy`。
- 后端：
  - 使用 JDK 17、`real-model` profile 启动到 `http://localhost:18080`。
  - `GET /api/app/capabilities` 返回 `ragEnabled=true`。
- 前端：
  - Vite 启动到 `http://127.0.0.1:5173`。

### 上传验证

- 验证 session：`rag-phase5-session-20260606`
- 上传文件：`target/http-check/rag-phase5-sample.txt`
- 上传资料关键词：`RAG-PHASE5-CHECK-20260606`
- 命令要点：
  - `curl.exe -F "file=@target/http-check/rag-phase5-sample.txt" -F "session_id=rag-phase5-session-20260606" http://localhost:18080/api/rag/upload`
- 结果：
  - 响应 `status=success`
  - `session_id=rag-phase5-session-20260606`
  - `file_name=rag-phase5-sample.txt`
  - `chunks=1`
- 后端日志确认：
  - `VectorStoreDataIngestionService` 读取 1 个文档，切出 1 个 chunk。
  - 已写入向量库：`session=rag-phase5-session-20260606`。

### 深度研究闭环验证

- 成功闭环 thread：`rag-phase5-thread-short-20260606`
- 请求要点：
  - `session_id=rag-phase5-session-20260606`
  - `thread_id=rag-phase5-thread-short-20260606`
  - `enable_deepresearch=true`
  - `auto_accepted_plan=true`
  - `max_step_num=1`
  - `optimize_query_num=0`
- SSE 文件：`target/http-check/rag-phase5-chat-short.sse`
- 结果：
  - `coordinator` 路由到 `DEEP_RESEARCH`。
  - SSE 出现 `node_name=user_file_rag` 的 started/completed 事件。
  - `displayTitle` 为“读取上传资料”。
  - `user_file_rag` payload 包含 `RAG-PHASE5-CHECK-20260606` 和来源文档 `rag-phase5-sample.txt`。
  - 后续 `coder_0`、`reporter` 完成。
  - SSE 出现 `event:done` 和 `event_type=graph.completed`。
  - 最终报告包含 `RAG-PHASE5-CHECK-20260606`，并说明它用于验证前端上传文件能否被后端 `user_file_rag` 在深度研究流程中读取与传递。

### 持久化验证

- 线程历史：
  - `GET /api/sessions/rag-phase5-session-20260606/threads/rag-phase5-thread-short-20260606`
  - 返回 `status=COMPLETED`
  - `report_thread_id=rag-phase5-thread-short-20260606`
  - `error_message=null`
- 报告接口：
  - `GET /api/reports/rag-phase5-thread-short-20260606`
  - 返回 `status=success`
  - 报告内容包含 `RAG-PHASE5-CHECK-20260606`
- 事件历史：
  - `GET /api/sessions/rag-phase5-session-20260606/threads/rag-phase5-thread-short-20260606/events`
  - 返回 `user_file_rag` 事件、`report.completed` 事件和 `graph.completed` 事件。

### 外部模型波动记录

- 首次使用 DeepSeek 普通深度研究请求时，外部请求返回 `Connection reset`，Graph 失败。
- 临时切换到已配置的 DashScope `qwen-flash` 后，直接回答路径可完成；强制深度研究路径能触发 `user_file_rag`，但后续研究节点遇到模型响应超时。
- 最终切回初始 DeepSeek `deepseek-chat`，使用更短深度研究请求完成完整闭环。
- 验证结束前当前模型选择已恢复为 DeepSeek `deepseek-chat`。
- 上述重试均通过后端公开模型接口完成，未读取、输出或提交任何 API Key。

## 浏览器布局验证

- 浏览器目标：`http://127.0.0.1:5173/chat`
- 桌面视口：`1280x720`
  - `/chat` 上传面板可见。
  - 上传按钮可见且可用。
  - `documentElement.scrollWidth=1280`，`clientWidth=1280`，无横向溢出。
  - 页面文本未出现 Windows 本地路径泄露。
- 移动视口：`390x844`
  - `/chat` 上传面板可见。
  - 上传按钮可见且可用。
  - `documentElement.scrollWidth=390`，`clientWidth=390`，无横向溢出。
  - 上传按钮与发送按钮不重叠。
  - 页面文本未出现 Windows 本地路径泄露。

## 服务关闭确认

- 已停止本次 Phase 5 启动的后端和前端服务。
- 关闭后确认：
  - `Get-NetTCPConnection -LocalPort 18080 -State Listen -ErrorAction SilentlyContinue` 无输出。
  - `Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue` 无输出。

## 敏感信息检查

- 未读取、输出或提交 `.local/model-providers.json`。
- 未读取、输出或提交 `.local/mcp-keys.json`。
- 未提交 `.local/`、`.claude/`、`target/` 或 `.idea/`。
- 验证记录中不包含 API Key 或本地敏感凭证。

## Phase 5 结论

- 自动化测试和前端构建通过。
- 前端 RAG 上传入口已可用，并根据 `ragEnabled=true` 展示可上传状态。
- 后端真实上传接口能将文档绑定到当前 `session_id`，并写入向量库。
- 同一 session 的真实深度研究流程能触发 `user_file_rag`，读取上传资料并进入最终报告。
- 会话历史、事件历史和报告接口均可持久化读取成功闭环证据。
- 桌面和移动端 `/chat` 布局无明显横向溢出，移动端上传按钮和发送按钮不重叠。
