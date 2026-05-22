# Task Handoff: add-postgres-report-persistence
Updated: 2026-05-22 12:48:54 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 8a90607dffe04e2613e3983bf560420bc43591cc
Current Commit: 8a90607dffe04e2613e3983bf560420bc43591cc

## Project Mainline

- This project is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under `C:/MainData/code/Codex_project/M-agent`.
- The long-term direction is to align with `C:/MainData/code/Codex_project/deepresearch-main` in a deliberately reduced, runnable backend-first form.
- The project is growing DeepResearch workflow semantics in `SimpleResearchRunner` before any full Spring AI Alibaba Graph migration.
- Completed stages removed mock runtime paths, added DeepResearch-compatible SSE envelopes, upgraded Plan/Step state, added a lightweight `research_team` control node, added Bocha-only real web search through an `information` node, added a real `processor` node for PROCESSING steps, added a human feedback plan gate for `/chat`, added paused-state `/chat/stop` cleanup, and added running chat stop/cancellation.
- The current stable auto-accepted backend loop is: planner -> information -> research_team -> researcher -> research_team -> processor -> research_team -> reporter -> `__END__`.
- The current interactive loop is: `/chat/stream` with `auto_accepted_plan=false` runs planner -> `human_feedback` waiting; `/chat/resume` with `feedback=true` continues execution; `/chat/resume` with `feedback=false` replans with `feedback_content` and waits again; `/chat/stop` can remove a paused `human_feedback` state or stop a currently subscribed chat stream.
- The next mainline step is to persist final research reports to a real database so completed workflows leave queryable artifacts, moving toward `deepresearch-main` report/history APIs without introducing the full Graph, RAG, MCP, Redis, frontend, or export stack yet.
- New feature work must not introduce mock agents, mock search, fabricated search results, local secret leaks, Redis, RAG, MCP, frontend code, or full Graph migration unless a later task explicitly asks for those layers.

## Stage Role in Mainline

- This stage should add database-backed report persistence after the workflow lifecycle boundary became controllable with pause, resume, and stop.
- It exists because M-agent can now complete, pause, resume, and stop research streams, but completed reports are only emitted over SSE and are not queryable afterward.
- It should align with `deepresearch-main` report concepts at the behavior/API level: report save/get/exists/delete and optional session-scoped report history.
- The user explicitly rejected an in-memory-only report store; use a real database from the start.
- Keep the MVP backend-only and minimal: PostgreSQL in Docker Desktop, a small schema, Spring persistence integration, and focused report APIs/tests.

## Mainline Progression

- `add-research-team` introduced a controlled loop around step execution.
- `add-information-node-bocha-search` added real Bocha `information` search before execution.
- `add-processor-node-search-context` split PROCESSING into a dedicated `processor` path that consumes prior observations and site information.
- `add-human-feedback-plan-gate` evolved the workflow from automatic run-to-completion to interactive controllable research with planner pause, accept resume, and rejected replan.
- `add-chat-stop-session-lifecycle` added `/chat/stop`, reference-style `ApiResponse`, paused-state cleanup, resume-after-stop error behavior, and real curl verification.
- `add-running-chat-stop-cancellation` completed the in-memory lifecycle boundary by making stop work during active `/chat` execution and verified it with unit tests plus real curl.
- `add-postgres-report-persistence` should turn completed reports into durable backend artifacts, creating the base for future report history, export/download, frontend report views, and eventual Graph migration.

## Related Stage Handoffs

- `add-research-team`: completed lightweight `ResearchTeamNode` and runner loop.
- `add-information-node-bocha-search`: completed Bocha-only `InformationNode` and real HTTP verification.
- `add-processor-node-search-context`: completed processor agent/node/routing and real HTTP verification.
- `add-human-feedback-plan-gate`: completed `/chat` human feedback plan gate, in-memory paused state, `/chat/resume`, and focused tests.
- `add-chat-stop-session-lifecycle`: completed `/chat/stop` for paused-state cleanup.
- `add-running-chat-stop-cancellation`: completed running `/chat/stop` cancellation and updated the stopped SSE contract.

## Goal

- Add PostgreSQL-backed persistence for generated research reports so completed workflows can be queried after SSE completion, while staying aligned with `deepresearch-main` report APIs and keeping the local MVP small.

## Task Theme / User Intent

- The user wants the next session to implement `add-postgres-report-persistence` and continue aligning M-agent with `C:/MainData/code/Codex_project/deepresearch-main`.
- The user does not want an in-memory-only report store; report data should be persisted to a database from the start.
- Database and middleware should run in Docker Desktop on the Windows host, not as direct host installs.
- Docker and database MCP tools are available and useful; prefer using them when inspecting or managing local containers.
- The user asked to keep `.claude/` ignored and not spend effort on it.
- The user asked that thinking/internal reasoning use English and final user-facing responses use Chinese; this preference was added to user-level and project-level `AGENTS.md`.

## Acceptance Criteria

- Add a Docker Desktop based PostgreSQL setup for local development, preferably via a minimal `docker-compose.yml` in M-agent.
- Use stable, compatible Maven dependencies for Spring Boot 3.4.x and Java 17; avoid unnecessarily new or risky dependency versions.
- Prefer WebFlux-compatible persistence. Recommended default: Spring Data R2DBC with PostgreSQL, plus Flyway using JDBC for schema migrations if needed.
- Add a durable `research_reports` table or equivalent schema with at least `thread_id`, `session_id`, `query`, `report`, `status`, `created_at`, and `updated_at`; optionally include `error_message` or metadata if useful.
- Save a `COMPLETED` report when `ReporterNode`/`SimpleResearchRunner` reaches the final report for `/chat/stream` and accepted `/chat/resume`; decide whether `/api/research/stream` should also persist reports and document the decision.
- Do not save fake or mock report content in production code.
- Stopped workflows should not persist a completed report. Either persist `STOPPED` with empty report or do not create a row; choose the smallest behavior and test it.
- Failed workflows may persist `FAILED` with an error reason if this can be done cleanly, otherwise leave it for a follow-up and document the choice.
- Add report APIs aligned with `deepresearch-main` behavior at minimum: `GET /api/reports/{threadId}`, `GET /api/reports/{threadId}/exists`, and `DELETE /api/reports/{threadId}`.
- Consider adding `GET /api/reports/session/{sessionId}` or a recent report list only if it stays small and naturally follows the schema.
- Add focused tests for repository/service/controller behavior and report persistence after workflow completion.
- Run Java 17 `mvn test`.
- Run Docker/PostgreSQL backed verification when practical: start the DB container, run migrations, start or test the app, verify report save/get/delete, then stop any manually started backend service. Keep containers/volumes only as intended for local development.
- Commit the completed stage with a Chinese commit message.

## Scope

- Work only in `C:/MainData/code/Codex_project/M-agent`.
- Inspect `C:/MainData/code/Codex_project/deepresearch-main` as read-only guidance.
- Backend-only scope: persistence, schema, report API, runner integration, tests, local Docker database setup.
- Do not edit the read-only reference project.
- Do not add frontend, RAG, MCP integration features, Redis, Elasticsearch, export/download/PDF, or a full Graph migration in this stage unless absolutely required for the minimal report persistence path.
- Preserve real provider paths and do not introduce mock production behavior.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/pom.xml`
- `C:/MainData/code/Codex_project/M-agent/docker-compose.yml`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/test/resources`
- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-postgres-report-persistence.md`

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

- Git branch: `main`.
- Current commit before creating this handoff: `8a90607dffe04e2613e3983bf560420bc43591cc`.
- No upstream is configured for `main`; upstream and merge-base commands fail with "no upstream configured".
- Working tree had only unrelated untracked `.claude/`; do not edit, delete, inspect deeply, or commit it unless the user explicitly asks.
- M-agent currently has no database dependencies, no Docker Compose file, and no report persistence layer.
- Existing resource files are `application.yml`, `application-llm.yml`, `application-real-model.yml`, and prompt markdown files.
- Current `SimpleResearchRunner` emits `ResearchEvent.done(threadId, ..., state.report())` after reporter completion, but the report is not persisted.
- `/api/research/stream` still uses `runner.run(...)`; `/chat/stream` uses `runner.runChat(...)`; accepted `/chat/resume` is cancellable.
- Manual real curl verification after running cancellation succeeded: DeepSeek key save, model switch, model test, running `/chat/stop` returning success with `event:stopped`, paused stop success, missing stop failure, and port 8080 released.

## Completed

- Created this resume-ready handoff for the next stage.
- Updated user-level `C:/Users/20232/.codex/AGENTS.md` with language preference: internal thinking in English, final replies in Chinese.
- Updated project-level `AGENTS.md` with the same language preference and a note to ignore `.claude/` unless explicitly requested.
- Reviewed the current M-agent module layout and confirmed no existing DB persistence stack.
- Reviewed `deepresearch-main` report-related references:
  - `ReportController`
  - `ReportService`
  - `ReportMemoryService`
  - `ReportRedisService`
  - `SessionHistory`
  - `SessionContextService`
  - `InMemorySessionContextService`
  - report examples in `DeepResearch.http`

## Decisions

- Next implementation should use a real database, not an in-memory report store.
- Recommended default is PostgreSQL in Docker Desktop, R2DBC for application persistence, and Flyway/JDBC migrations for schema setup.
- Keep report persistence minimal and durable before adding export/download, frontend, RAG, Redis, Elasticsearch, or Graph migration.
- Prefer database state values that make lifecycle behavior explicit: `COMPLETED`, optionally `STOPPED`, optionally `FAILED`.
- Do not treat `.claude/` as part of this task.

## Evidence / References

- Local runner completion points: `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`, lines around `ResearchEvent.done(...)`.
- Local report state source: `src/main/java/top/lanshan/manmu/model/ResearchState.java`, `report()` and `report(String)`.
- Local reporter: `src/main/java/top/lanshan/manmu/node/ReporterNode.java`, where `state.report(report)` is set.
- Local chat controller: `src/main/java/top/lanshan/manmu/api/ChatController.java`.
- Local research controller: `src/main/java/top/lanshan/manmu/api/ResearchController.java`.
- Reference report controller: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/controller/ReportController.java`.
- Reference report service interface and implementations: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/service/ReportService.java`, `ReportMemoryService.java`, `ReportRedisService.java`.
- Reference session history/context: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/model/SessionHistory.java`, `service/SessionContextService.java`, `service/InMemorySessionContextService.java`.
- Reference HTTP examples: `C:/MainData/code/Codex_project/deepresearch-main/DeepResearch.http`, lines for `/api/reports/{threadId}`, `/exists`, `DELETE`, `/export`, `/download`, and `/build-html`.
- User constraint: database and middleware run in Docker Desktop on Windows; Docker/database MCP tools are available and should remain usable.

## Files Touched

- `C:/Users/20232/.codex/AGENTS.md` (outside repo, user-level instruction update)
- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-postgres-report-persistence.md`

## Commands Run

- `Get-Content -LiteralPath C:\Users\20232\.codex\skills\task-handoff\SKILL.md`
- `git rev-parse --show-toplevel`
- `git status --short`
- `git log --oneline --decorate -6`
- `Get-Content -Encoding UTF8 -LiteralPath C:\Users\20232\.codex\AGENTS.md`
- `Get-Content -Encoding UTF8 -LiteralPath AGENTS.md`
- `git branch --show-current`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` failed because no upstream is configured.
- `git merge-base HEAD '@{upstream}'` failed because no upstream is configured.
- `Get-Content -Encoding UTF8 -LiteralPath .codex\tasks\add-running-chat-stop-cancellation.md`
- `Get-ChildItem -LiteralPath .codex\tasks -File | Select-Object Name,LastWriteTime,Length`
- `Select-String -LiteralPath C:\MainData\code\Codex_project\deepresearch-main\DeepResearch.http -Pattern '/api/reports|/chat/stream|/chat/resume|/chat/stop' -Context 2,2`
- `Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz"`

## Verification

- No implementation verification has run for `add-postgres-report-persistence` because implementation has not started.
- Previous stage verification after running cancellation:
  - `mvn test` passed 38 tests.
  - Manual real curl verification passed DeepSeek model setup/test, running chat stop with `event:stopped`, paused stop, missing stop, and backend shutdown with port 8080 released.

## Known Failures / Blockers

- No upstream Git branch is configured.
- Unrelated untracked `.claude/` exists in the working tree; ignore it.
- M-agent currently has no database container, schema migration, R2DBC/JDBC config, repository, or report API.
- Introducing database-backed tests may require a Dockerized PostgreSQL strategy or a local dev DB; prefer Docker Desktop and available Docker/database MCP tooling.
- Real LLM/provider tests can fail from external API rate limits or network timeouts; report persistence tests should isolate DB behavior when possible and only use real providers for manual end-to-end checks.

## Next Actions

- Inspect current Spring Boot config and choose the minimal PostgreSQL + R2DBC + migration setup, using Docker Desktop for the local database and keeping secrets out of source.
- Implement report persistence schema, repository/service, runner save integration, and `/api/reports` get/exists/delete APIs aligned with `deepresearch-main`.
- Run Java 17 `mvn test` and Docker-backed/manual verification; stop any manually started backend service afterward and commit with a Chinese message.

## Open Questions

- Should stopped workflows create a `STOPPED` row, or should they leave no report row? The user has not decided; choose the smaller behavior and document it.
- Should failed workflows create a `FAILED` row with `error_message` in this stage, or should that wait for a follow-up status/history task?
- Should `/api/research/stream` persist reports too, or should persistence initially apply only to `/chat/*` to mirror the user-facing DeepResearch flow?
- Should report history by session be included now, or should this stage stop at get/exists/delete by `threadId`?

## Avoid / Do Not Redo

- Do not implement an in-memory-only report store; the user explicitly wants database persistence.
- Do not edit `C:/MainData/code/Codex_project/deepresearch-main`.
- Do not migrate to Spring AI Alibaba Graph yet.
- Do not add Redis, Elasticsearch, RAG, MCP feature integration, frontend changes, export/download/PDF, or Graph saver/checkpoint infrastructure in this stage.
- Do not introduce mock agent output, mock search, fake reports, or fake search context in production code.
- Do not commit `.local`, `target`, `.idea`, `.claude`, API keys, generated SSE/curl output, or logs.
- Do not leave manually started backend services running after tests.
- Do not undo the completed chat stop/cancellation behavior or its `stopped` SSE contract.

## Resume Prompt
Resume task add-postgres-report-persistence. Read .codex/tasks/add-postgres-report-persistence.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
