# 记忆画像真实链路 E2E 验证记录

验证日期：2026-05-26

## 验证范围

- 后端以 `real-model` profile 启动在 `18080`。
- Flyway 连接真实 PostgreSQL，启动日志显示 schema 当前版本为 `6`。
- 通过 `/api/model/switch` 切换到 `deepseek-chat`，仅确认 `apiKeyConfigured=true`，未读取、打印或提交本地密钥文件。
- 使用 `curl.exe` 调用两轮同 session 的 `/chat/stream`，覆盖真实 HTTP/SSE、真实模型供应商、PostgreSQL 持久化、conversation memory、user profile、report、session history。

## 验证数据

- `session_id`: `phase6-memory-quality`
- `thread_id`:
  - `phase6-memory-quality-1`
  - `phase6-memory-quality-2`

## 验证结果

- 两轮 SSE 均返回 `message` 事件，并以 `done` 事件结束。
- `conversation_messages` 中同一 session 下存在 `USER=2`、`ASSISTANT=2`。
- `user_profiles` 中生成结构化画像字段：
  - `expertise_level=intermediate`
  - `detail_preference=concise`
  - `style_preference=practical`
- `research_session_histories` 中两个线程均为 `COMPLETED`，且 `report_thread_id` 可回指报告线程。
- `research_reports` 中两个线程均为 `COMPLETED`，报告内容可通过 `/api/reports/session/{sessionId}` 读取。
- `/api/sessions/{sessionId}/history` 可读取两个已完成线程的会话历史。
- 验证结束后已停止本地后端服务，并确认 `18080` 端口释放。

## 本地输出

原始请求、SSE、HTTP 响应和数据库只读查询输出保存在 `target/http-check/phase6-*`，该目录为本地验证产物，不提交。
