# Task Handoff: add-session-history-persistence
Updated: 2026-05-22 14:05:00 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 49bcae5a1294de3443133d1859c93bce10e0d73e
Current Commit: current HEAD handoff commit; verify with `git rev-parse HEAD`

## Project Mainline

- This project is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under `C:/MainData/code/Codex_project/M-agent`.
- The long-term direction is to align with `C:/MainData/code/Codex_project/deepresearch-main` in a deliberately reduced, runnable backend-first form.
- The project evolves DeepResearch workflow semantics in `SimpleResearchRunner` before adopting any full Spring AI Alibaba Graph stack.
- Completed stages now provide real model calls, Bocha-only real web search, planner/researcher/processor/reporter execution, a lightweight `research_team` loop, human feedback plan pause/resume/replan, running `/chat/stop` cancellation, PostgreSQL-backed completed report persistence, and PostgreSQL-backed session history persistence.
- Durable artifacts now include `research_reports` for final report bodies and `research_session_histories` for lifecycle history.
- The next mainline step can build UI/history retrieval, retry/export/download, or deeper Graph alignment on top of durable session status without introducing Redis, RAG, MCP, frontend, or full checkpointing prematurely.

## Stage Role in Mainline

- This stage added database-backed session history after completed report persistence.
- It closes the gap where final reports were durable but lifecycle states such as `RUNNING`, `PAUSED`, `STOPPED`, and `FAILED` were not queryable.
- It aligns with `deepresearch-main` `SessionHistory` / `SessionContextService` concepts by grouping thread histories under a session, preserving the user query, linking to report storage, and exposing recent histories.
- It remains a minimal backend-only step using the existing PostgreSQL, Flyway, R2DBC, WebFlux, and Docker Desktop setup.

## Mainline Progression

- `add-research-team` introduced the controlled research loop.
- `add-information-node-bocha-search` added real Bocha information gathering.
- `add-processor-node-search-context` added the processor path for PROCESSING steps.
- `add-human-feedback-plan-gate` added planner pause/resume/replan.
- `add-chat-stop-session-lifecycle` and `add-running-chat-stop-cancellation` made paused and running chat sessions stoppable.
- `add-postgres-report-persistence` added Docker PostgreSQL, Flyway schema migration, R2DBC report persistence, `/api/reports` get/exists/delete/session APIs, and verified the real curl chain.
- `add-session-history-persistence` added durable session/task history rows and lifecycle status transitions that future report history UI, export/download, retry, and eventual Graph migration can reuse.

## Related Stage Handoffs

- `add-postgres-report-persistence`: immediate upstream; completed and committed as `8e1e006` with a Chinese message meaning "Add Postgres report persistence".
- `add-running-chat-stop-cancellation`: upstream lifecycle behavior; stop remains cancellable and emits `event:stopped`.
- `add-chat-stop-session-lifecycle`: upstream paused stop cleanup.
- `add-human-feedback-plan-gate`: upstream paused `human_feedback` state.
- Earlier stage handoffs under `.codex/tasks/` provide the rest of the mainline.

## Goal

- Add PostgreSQL-backed session/task history persistence aligned with `deepresearch-main` session history concepts so a session can list its research runs and each thread has durable lifecycle state, query, timestamps, error information, and report linkage.

## Task Theme / User Intent

- Continue aligning M-agent with `C:/MainData/code/Codex_project/deepresearch-main` through a small runnable backend capability.
- Use the existing real PostgreSQL setup from the prior stage instead of an in-memory-only session store.
- Preserve real model/search execution paths and avoid mock production behavior.
- Keep the implementation backend-only and deliberately smaller than the reference project's full Graph/RAG/Redis stack.

## Acceptance Criteria

- Done: added Flyway migration `V2__create_research_session_histories.sql`.
- Done: tracks `thread_id`, `session_id`, `query`, `status`, `report_thread_id`, `error_message`, `created_at`, `updated_at`, `completed_at`, and `stopped_at`.
- Done: uses `RUNNING`, `PAUSED`, `COMPLETED`, `STOPPED`, and `FAILED`.
- Done: creates/updates history for `/chat/stream` start, planner human-feedback wait, `/chat/resume` continue/replan, completion, `/chat/stop`, and runner errors.
- Done: `/api/research/stream` also creates history because it uses `SimpleResearchRunner.run(...)`, with `session_id = threadId`.
- Done: completed report content remains in `research_reports`; session history links by `report_thread_id`.
- Done: added `GET /api/sessions/{sessionId}/history`, `GET /api/sessions/{sessionId}/threads/{threadId}`, and `GET /api/sessions/{sessionId}/recent?count=5`.
- Done: added focused service, controller, and runner tests for status transitions, listing, stopped behavior, and failed state.
- Done: Java 17 `mvn test` passed.
- Done: Docker/PostgreSQL-backed curl verification passed and the manually started backend was stopped; port `8080` was released.
- Done: implementation committed with Chinese message `添加会话历史持久化`.

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
- Current handoff commit: self-referential; verify with `git rev-parse HEAD`.
- Implementation commit: `49bcae5a1294de3443133d1859c93bce10e0d73e` (`添加会话历史持久化`).
- No upstream Git branch is configured for `main`.
- Working tree after implementation commit only had unrelated untracked `.claude/settings.local.json`; it must remain uncommitted.
- Docker PostgreSQL `manmu-postgres` was running and healthy during verification.
- The manually started backend service was stopped after curl verification and port `8080` was confirmed released.
- The `target/manual-session-history-app.log` and `.err.log` verification artifacts are under ignored `target/`.

## Completed

- Added `research_session_histories` schema with indexes in `src/main/resources/db/migration/V2__create_research_session_histories.sql`.
- Added `top.lanshan.manmu.sessionhistory`:
  - `ResearchSessionHistory`
  - `ResearchSessionHistoryEntity`
  - `ResearchSessionHistoryRepository`
  - `SessionHistoryStatus`
  - `SessionHistoryService`
  - `PostgresSessionHistoryService`
  - `SessionHistoryResponse`
- Added `SessionHistoryController` under `/api/sessions`.
- Wired `SimpleResearchRunner` to create `RUNNING` rows, mark planner gates as `PAUSED`, mark resume/replan as `RUNNING`, mark completed workflows as `COMPLETED`, mark stopped workflows as `STOPPED`, and persist `FAILED` for runner errors.
- Updated `/chat/stop` to return `Mono<ApiResponse<String>>` and wait for `stopAndRecord(...)`, so a follow-up history query sees `STOPPED` deterministically.
- Kept report bodies in `research_reports`; `research_session_histories.report_thread_id` links to the report thread.
- Added service, controller, and runner tests for the new persistence and lifecycle behavior.
- Committed implementation as `49bcae5` with Chinese message `添加会话历史持久化`.

## Decisions

- The table name is `research_session_histories`.
- Persistence uses the existing Spring Data R2DBC/PostgreSQL stack, not MyBatis/MyBatis-Plus.
- `STOPPED` rows do not link to a report unless completion already happened; normal stopped workflows do not save completed report rows.
- `FAILED` is persisted for runner errors after history creation starts.
- `/api/research/stream` creates history through `SimpleResearchRunner.run(...)` with session equal to thread ID.
- `/chat/stop` keeps the existing response shape but now waits for history persistence.
- Session history APIs use a separate `SessionHistoryResponse` envelope rather than reusing `ReportResponse`.

## Evidence / References

- New schema: `src/main/resources/db/migration/V2__create_research_session_histories.sql`
- New service/repository model: `src/main/java/top/lanshan/manmu/sessionhistory`
- New API: `src/main/java/top/lanshan/manmu/api/SessionHistoryController.java`
- Lifecycle integration: `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- Stop API integration: `src/main/java/top/lanshan/manmu/api/ChatController.java`
- Tests:
  - `src/test/java/top/lanshan/manmu/sessionhistory/PostgresSessionHistoryServiceTest.java`
  - `src/test/java/top/lanshan/manmu/api/SessionHistoryControllerTest.java`
  - `src/test/java/top/lanshan/manmu/runner/SimpleResearchRunnerTest.java`
  - `src/test/java/top/lanshan/manmu/api/ChatControllerTest.java`
- Reference session history inspected:
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/model/SessionHistory.java`
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/service/SessionContextService.java`
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/service/InMemorySessionContextService.java`

## Files Touched

- `C:/MainData/code/Codex_project/M-agent/src/main/resources/db/migration/V2__create_research_session_histories.sql`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/sessionhistory/*`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/api/SessionHistoryController.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/api/ChatController.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/sessionhistory/PostgresSessionHistoryServiceTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/api/SessionHistoryControllerTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/api/ChatControllerTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/runner/SimpleResearchRunnerTest.java`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-session-history-persistence.md`

## Commands Run

- `git status --short --branch --untracked-files=all`
- `git log --oneline --decorate -10`
- `docker_container_list(all=true)`
- `docker_container_inspect(manmu-postgres)`
- `Get-Content` for project instructions, report layer, runner/controller files, tests, and reference `deepresearch-main` session history files
- `rg "new SimpleResearchRunner|SimpleResearchRunner\(" src/test src/main -n`
- `$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; mvn test`
- `Start-Process mvn.cmd spring-boot:run`
- `curl.exe` verification for model switch, `/chat/stream`, `/api/sessions/{sessionId}/threads/{threadId}`, `/api/reports/session/{sessionId}`, `/chat/stop`, and `/api/sessions/{sessionId}/recent?count=2`
- `Get-NetTCPConnection -LocalPort 8080`
- `Stop-Process` for the manually started Maven/Spring Boot process tree
- `git add ...`
- `git commit -m "添加会话历史持久化"`

## Verification

- `mvn test` passed twice with Java 17; final run reported 55 tests, 0 failures, 0 errors, 0 skipped.
- Docker/PostgreSQL verification used running healthy `manmu-postgres` on localhost port 5432.
- Manual backend startup applied Flyway version 2 against PostgreSQL and listened on port 8080.
- Curl verification passed:
  - DeepSeek key set and current model switched to `deepseek-chat`.
  - Auto-accepted `/chat/stream` emitted message events and `event:done`.
  - Completed thread history returned `status=COMPLETED` and `report_thread_id=<threadId>`.
  - `/api/reports/session/{sessionId}` returned the completed report row.
  - Plan-gated `/chat/stream` returned `PAUSED` history before stop.
  - `/chat/stop` returned success.
  - Stopped thread history returned `status=STOPPED` and `stopped_at`.
  - `/api/sessions/{sessionId}/recent?count=2` returned the stopped and completed histories in updated order.
- The manually started backend service was stopped and port `8080` was confirmed released.

## Known Failures / Blockers

- No upstream Git branch is configured.
- Unrelated untracked `.claude/settings.local.json` exists and must remain uncommitted.
- `.gitignore` currently ignores `.local/`, `target/`, and `.idea/`, but not `.claude/`; project instructions still forbid editing or committing `.claude/`.
- Real LLM/search verification can fail from external network/API issues; keep unit/service tests isolated from provider availability and use curl verification for end-to-end confidence.

## Next Actions

- Optional: add a frontend/history UI or richer history endpoint that joins report metadata without returning full report bodies unless requested.
- Optional: decide whether future delete behavior should delete only reports, only history, or both for a thread/session.
- Optional: use the durable history table as the base for retry/resume/export/download or later Graph checkpoint alignment.

## Open Questions

- Should `.claude/` be added to `.gitignore` in a future housekeeping commit, or should it remain manually untracked per current instructions?
- Should session history expose a lightweight joined report summary endpoint, separate from `/api/reports/session/{sessionId}`?
- Should failed histories capture a structured error code/source node beyond the current `error_message` string?

## Avoid / Do Not Redo

- Do not reimplement report persistence from scratch; build on `research_reports`.
- Do not use an in-memory-only session history store.
- Do not edit `C:/MainData/code/Codex_project/deepresearch-main`.
- Do not introduce MyBatis/MyBatis-Plus unless the user explicitly asks to change the persistence stack.
- Do not add Redis, Elasticsearch, RAG, MCP feature integration, frontend changes, export/download/PDF, or full Graph saver/checkpoint infrastructure unless it becomes a later explicit stage.
- Do not introduce mock agent output, mock search, fake reports, or fake search context in production code.
- Do not commit `.local`, `target`, `.idea`, `.claude`, API keys, generated SSE/curl output, or logs.
- Do not leave manually started backend services running after tests.
- Do not undo chat stop/cancellation behavior, report persistence behavior, or the `stopped` SSE contract.

## Resume Prompt
Resume task add-session-history-persistence. Read .codex/tasks/add-session-history-persistence.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
