# Task Handoff: add-session-history-persistence
Updated: 2026-05-22 13:34:00 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 8e1e0064b8df155680310d8c7825813f4fe4fb82
Current Commit: 8e1e0064b8df155680310d8c7825813f4fe4fb82

## Project Mainline

- This project is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under `C:/MainData/code/Codex_project/M-agent`.
- The long-term direction is to align with `C:/MainData/code/Codex_project/deepresearch-main` in a deliberately reduced, runnable backend-first form.
- The project has been evolving the DeepResearch workflow semantics in `SimpleResearchRunner` before any full Spring AI Alibaba Graph migration.
- Completed stages now provide real model calls, Bocha-only real web search, planner/researcher/processor/reporter execution, a lightweight `research_team` loop, human feedback plan pause/resume/replan, running `/chat/stop` cancellation, and PostgreSQL-backed completed report persistence.
- The current durable artifact layer is `research_reports`, persisted through Spring Data R2DBC and Flyway-backed PostgreSQL.
- The next mainline step is to persist the session/task lifecycle itself so completed, paused, stopped, and failed research runs become queryable history, moving closer to `deepresearch-main` `SessionHistory` / `SessionContextService` behavior without adopting Redis, RAG, MCP, frontend, or the full Graph stack yet.

## Stage Role in Mainline

- This stage should add database-backed session history after completed report persistence.
- It exists because M-agent can now save final reports, but only completed reports are queryable; lifecycle states such as `RUNNING`, `PAUSED`, `STOPPED`, and `FAILED` are still not durable.
- It should align behaviorally with `deepresearch-main` session history concepts: sessions group thread IDs, histories preserve user query and report linkage, and recent session reports can be retrieved.
- It should remain a minimal backend-only stage, using the existing PostgreSQL/R2DBC/Flyway setup rather than adding Redis or a full Spring AI Alibaba Graph saver.

## Mainline Progression

- `add-research-team` introduced the controlled research loop.
- `add-information-node-bocha-search` added real Bocha information gathering.
- `add-processor-node-search-context` added the processor path for PROCESSING steps.
- `add-human-feedback-plan-gate` added planner pause/resume/replan.
- `add-chat-stop-session-lifecycle` and `add-running-chat-stop-cancellation` made paused and running chat sessions stoppable.
- `add-postgres-report-persistence` added Docker PostgreSQL, Flyway schema migration, R2DBC report persistence, `/api/reports` get/exists/delete/session APIs, and verified the real curl chain.
- `add-session-history-persistence` should build on `research_reports` by introducing durable session/task history rows and lifecycle status updates that future report history UI, export/download, retry, and eventual Graph migration can reuse.

## Related Stage Handoffs

- `add-postgres-report-persistence`: immediate upstream; completed and committed as `8e1e006` with a Chinese commit message meaning "Add Postgres report persistence".
- `add-running-chat-stop-cancellation`: upstream lifecycle behavior; stop must remain cancellable and emit `event:stopped`.
- `add-chat-stop-session-lifecycle`: upstream paused stop cleanup.
- `add-human-feedback-plan-gate`: upstream paused `human_feedback` state.
- Earlier stage handoffs under `.codex/tasks/` provide the rest of the mainline.

## Goal

- Add PostgreSQL-backed session/task history persistence aligned with `deepresearch-main` session history concepts so a session can list its research runs and each thread has durable lifecycle state, query, timestamps, error information, and report linkage.

## Task Theme / User Intent

- The user will start a new conversation to implement `add-session-history-persistence` and continue aligning M-agent with `C:/MainData/code/Codex_project/deepresearch-main`.
- The user wants this handoff to summarize the just-completed Postgres report persistence stage and set the next stage direction.
- The next stage should prioritize a small, runnable backend capability over broad migration.
- Database and middleware should run through Docker Desktop on Windows.
- Do not use an in-memory-only session store; use the existing real PostgreSQL setup from the prior stage.

## Acceptance Criteria

- Add a durable session/task history table, for example `research_session_histories` or `research_tasks`, through a new Flyway migration.
- Track at least `thread_id`, `session_id`, `query`, `status`, `created_at`, `updated_at`, and optionally `report_id`/`report_thread_id`, `error_message`, `completed_at`, `stopped_at`, or `metadata`.
- Use clear lifecycle statuses such as `RUNNING`, `PAUSED`, `COMPLETED`, `STOPPED`, and `FAILED`.
- Create/update history when `/chat/stream` starts, when planner waits for human feedback, when `/chat/resume` continues or replans, when the workflow completes, when `/chat/stop` stops paused or running work, and when the runner emits an error.
- Decide whether `/api/research/stream` should also create history; recommended default is yes with `session_id = threadId`, matching the report persistence decision.
- Keep completed report storage in `research_reports`; session history should link to report content instead of duplicating full report text unless a small snapshot is deliberately chosen.
- Add minimal APIs aligned with `deepresearch-main` concepts, for example:
  - `GET /api/sessions/{sessionId}/history`
  - `GET /api/sessions/{sessionId}/threads/{threadId}`
  - optional `GET /api/sessions/{sessionId}/recent?count=5`
  - optional delete only if it stays small and consistent with existing report delete behavior.
- Add focused service/controller/runner tests for status transitions, session listing, stopped non-completed behavior, and failed state if implemented.
- Run Java 17 `mvn test`.
- Run Docker/PostgreSQL backed verification when practical with `curl.exe`, then stop any manually started backend service and confirm port `8080` is released.
- Commit the completed stage with a Chinese commit message.

## Scope

- Work only in `C:/MainData/code/Codex_project/M-agent`.
- Inspect `C:/MainData/code/Codex_project/deepresearch-main` as read-only guidance.
- Backend-only scope: schema, repository/service/controller APIs, runner/controller lifecycle integration, tests, and Docker-backed verification.
- Do not edit the read-only reference project.
- Do not add frontend, RAG, MCP integration features, Redis, Elasticsearch, export/download/PDF, or a full Graph migration in this stage.
- Preserve real provider paths and do not introduce mock production behavior.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/pom.xml`
- `C:/MainData/code/Codex_project/M-agent/docker-compose.yml`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/test/resources`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-session-history-persistence.md`
- Optional update: `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-postgres-report-persistence.md` only if correcting cross-stage handoff details.

### Read-only Reference Roots

- `C:/MainData/code/Codex_project/deepresearch-main`
- Existing Git history in `C:/MainData/code/Codex_project/M-agent`
- Existing task handoffs under `C:/MainData/code/Codex_project/M-agent/.codex/tasks`
- Ignored manual verification artifacts under `C:/MainData/code/Codex_project/M-agent/target`

### Forbidden Write Roots

- `C:/MainData/code/Codex_project/deepresearch-main`
- `C:/MainData/code/Codex_project/M-agent/.local`
- `C:/MainData/code/Codex_project/M-agent/target`
- `C:/MainData/code/Codex_project/M-agent/.idea`
- `C:/MainData/code/Codex_project/M-agent/.claude`
- Any file containing API keys or local secrets

## Current State

- Current branch: `main`.
- Current commit: `8e1e0064b8df155680310d8c7825813f4fe4fb82` (`添加Postgres报告持久化`).
- Working tree has only unrelated untracked `.claude/settings.local.json`; do not edit, delete, stage, or commit it.
- `add-postgres-report-persistence` is complete and committed.
- Docker PostgreSQL `manmu-postgres` was running and healthy during verification; the next session should inspect live Docker state before relying on it.
- No backend service should be running on port `8080`; the prior manual curl verification stopped the app and confirmed port release.
- There is no session history persistence layer yet.

## Completed

- Completed upstream report persistence stage:
  - Added `docker-compose.yml` with `manmu-postgres` using `postgres:17-alpine`.
  - Added Spring Data R2DBC, PostgreSQL R2DBC/JDBC, Flyway, H2 test profile, and migrations.
  - Added `research_reports` schema with `thread_id`, `session_id`, `query`, `report`, `status`, `error_message`, `created_at`, and `updated_at`.
  - Added report service/repository/controller under `top.lanshan.manmu.report` and `/api/reports`.
  - Persisted completed reports before the final SSE `done` event.
  - Kept stopped workflows from creating completed report rows.
  - Added tests and passed full `mvn test`.
  - Ran curl-based full chain against real Docker PostgreSQL, DeepSeek, and Bocha paths; report get/exists/session/delete worked and port `8080` was released afterward.
- Inspected `deepresearch-main` session history references:
  - `SessionHistory`
  - `SessionContextService`
  - `InMemorySessionContextService`
  - `ReporterNode` usage of `SessionContextService.addSessionHistory(...)`
  - `BackgroundInvestigationNode` usage of `getRecentReports(sessionId)`

## Decisions

- The next stage should use the existing R2DBC/PostgreSQL stack rather than MyBatis/MyBatis-Plus, because the app is WebFlux/SSE based and the current persistence layer is reactive.
- Session history should be durable in PostgreSQL, not in memory.
- Start with lifecycle/history APIs and avoid pulling in reference-project RAG, short-term memory, Redis, or full Graph checkpointing.
- Prefer linking session history to `research_reports` by `thread_id` rather than duplicating report body in a second table.
- Keep `/api/research/stream` behavior consistent with report persistence unless a strong reason appears to scope history only to `/chat`.

## Evidence / References

- Upstream committed report persistence:
  - `src/main/java/top/lanshan/manmu/report/PostgresReportService.java`
  - `src/main/java/top/lanshan/manmu/api/ReportController.java`
  - `src/main/resources/db/migration/V1__create_research_reports.sql`
  - `docker-compose.yml`
- Runner lifecycle integration points:
  - `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
  - `src/main/java/top/lanshan/manmu/api/ChatController.java`
  - `src/main/java/top/lanshan/manmu/api/ResearchController.java`
  - `src/main/java/top/lanshan/manmu/model/ResearchState.java`
- Reference session history:
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/model/SessionHistory.java`
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/service/SessionContextService.java`
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/service/InMemorySessionContextService.java`
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/ReporterNode.java`
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/BackgroundInvestigationNode.java`
- User direction: continue aligning with `C:/MainData/code/Codex_project/deepresearch-main` in a new session under task `add-session-history-persistence`.

## Files Touched

- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-session-history-persistence.md`

## Commands Run

- `Get-Content -Raw C:\Users\20232\.codex\skills\task-handoff\SKILL.md`
- `git status --short --branch --untracked-files=all`
- `git log --oneline --decorate -8`
- `Get-Content -Raw .codex\tasks\add-postgres-report-persistence.md`
- `Get-ChildItem -File .codex\tasks`
- `git rev-parse HEAD`
- `Get-Content -Raw AGENTS.md`
- `Get-Content -Raw C:\MainData\code\Codex_project\deepresearch-main\src\main\java\com\alibaba\cloud\ai\example\deepresearch\model\SessionHistory.java`
- `Get-Content -Raw C:\MainData\code\Codex_project\deepresearch-main\src\main\java\com\alibaba\cloud\ai\example\deepresearch\service\SessionContextService.java`
- `Get-Content -Raw C:\MainData\code\Codex_project\deepresearch-main\src\main\java\com\alibaba\cloud\ai\example\deepresearch\service\InMemorySessionContextService.java`
- `rg "SessionHistory|SessionContext|history|/api/sessions|session" C:\MainData\code\Codex_project\deepresearch-main\src\main\java\com\alibaba\cloud\ai\example\deepresearch -n`

## Verification

- No code implementation has started for `add-session-history-persistence`.
- Upstream verification from `add-postgres-report-persistence`:
  - `mvn test` passed 46 tests.
  - Curl-based full chain passed against real Docker PostgreSQL and real model/search paths.
  - Backend service was stopped and port `8080` released after verification.

## Known Failures / Blockers

- No upstream Git branch is configured.
- Unrelated untracked `.claude/settings.local.json` exists and must remain uncommitted.
- The next stage must inspect current Docker state before assuming `manmu-postgres` is still running.
- Real LLM/search verification may fail from external network/API issues; keep unit/service tests isolated from provider availability and use curl verification for end-to-end confidence.

## Next Actions

- On resume, inspect git status, Docker/PostgreSQL state, current schema/migrations, and reference `SessionHistory` / `SessionContextService` usage before editing.
- Design and implement the minimal PostgreSQL-backed session history schema/service/controller and wire lifecycle updates into `SimpleResearchRunner`, `ChatController`, and possibly `ResearchController`.
- Run Java 17 `mvn test`, then perform a Docker/PostgreSQL `curl.exe` verification for create/list/get/status transitions, stop behavior, and completed report linkage; stop the backend service and commit with a Chinese message.

## Open Questions

- Should history rows be called `research_session_histories`, `research_tasks`, or another name that better fits future frontend/API use?
- Should `STOPPED` rows include a report link when a report was partially generated, or should stopped rows never link to reports?
- Should `FAILED` be persisted for all runner errors now, and should errors before planner creation still create history rows?
- Should `/chat/stop` return the updated history status in its `ApiResponse`, or remain a thread ID response for now?
- Should session history APIs reuse `ReportResponse` style envelopes or introduce a separate session response type?

## Avoid / Do Not Redo

- Do not reimplement report persistence from scratch; build on the committed `research_reports` layer.
- Do not use an in-memory-only session history store.
- Do not edit `C:/MainData/code/Codex_project/deepresearch-main`.
- Do not introduce MyBatis/MyBatis-Plus unless the user explicitly asks to change the persistence stack; current app uses WebFlux-friendly R2DBC.
- Do not add Redis, Elasticsearch, RAG, MCP feature integration, frontend changes, export/download/PDF, or full Graph saver/checkpoint infrastructure in this stage.
- Do not introduce mock agent output, mock search, fake reports, or fake search context in production code.
- Do not commit `.local`, `target`, `.idea`, `.claude`, API keys, generated SSE/curl output, or logs.
- Do not leave manually started backend services running after tests.
- Do not undo the completed chat stop/cancellation behavior, report persistence behavior, or `stopped` SSE contract.

## Resume Prompt
Resume task add-session-history-persistence. Read .codex/tasks/add-session-history-persistence.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
