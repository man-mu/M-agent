# Task Handoff: add-postgres-report-persistence
Updated: 2026-05-22 13:18:49 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 3f86f80555c2d28726ccc10fd868283ff841b441
Current Commit: 3f86f80555c2d28726ccc10fd868283ff841b441

## Project Mainline

- This project is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under `C:/MainData/code/Codex_project/M-agent`.
- The long-term direction is to align with `C:/MainData/code/Codex_project/deepresearch-main` in a deliberately reduced, runnable backend-first form.
- The stable backend loop before this stage was planner -> information -> research_team -> researcher -> research_team -> processor -> research_team -> reporter -> `__END__`, with `/chat` supporting plan pause, resume, replan, and stop/cancel.
- This stage advanced the mainline by making final reports durable PostgreSQL-backed artifacts that can be queried after SSE completion.
- Future stages can build report history, export/download, frontend report views, and eventual Graph migration on top of this persistence base without adding Redis, RAG, MCP, frontend, or full Graph infrastructure yet.

## Stage Role in Mainline

- This stage added the first real database-backed persistence layer after the workflow lifecycle became controllable.
- It specifically avoided the reference project's in-memory or Redis report store and implemented a PostgreSQL-backed MVP.
- It aligned with `deepresearch-main` report API behavior for get, exists, and delete, while keeping export/download/interactive HTML out of scope.

## Mainline Progression

- Earlier stages added the research team loop, Bocha information search, processor node, human feedback gate, paused stop cleanup, and running cancellation.
- `add-postgres-report-persistence` now persists completed reports at the runner completion boundary before emitting the final `done` event.
- `/api/research/stream` persists with `session_id = threadId` because it has no session input; `/chat/*` persists with the real `session_id`.
- Stopped workflows do not create a completed report row. Failed workflow persistence remains a follow-up.

## Related Stage Handoffs

- `add-research-team`
- `add-information-node-bocha-search`
- `add-processor-node-search-context`
- `add-human-feedback-plan-gate`
- `add-chat-stop-session-lifecycle`
- `add-running-chat-stop-cancellation`

## Goal

- Add PostgreSQL-backed persistence for generated research reports so completed workflows can be queried after SSE completion, while keeping the backend MVP small and aligned with `deepresearch-main` report APIs.

## Task Theme / User Intent

- Use a real database from the start, not an in-memory-only report store.
- Prefer Docker Desktop for local PostgreSQL on Windows.
- Keep the work backend-only and avoid broad DeepResearch features such as RAG, MCP, Redis, frontend, export/download, and full Graph migration.
- Preserve real model and Bocha search paths; do not introduce mock production behavior.

## Acceptance Criteria

- Add local Docker Desktop PostgreSQL setup.
- Add stable Spring Boot 3.4.x compatible R2DBC/PostgreSQL/Flyway dependencies.
- Add schema for `research_reports` with `thread_id`, `session_id`, `query`, `report`, `status`, `error_message`, `created_at`, and `updated_at`.
- Persist `COMPLETED` reports for completed `/api/research/stream`, auto-accepted `/chat/stream`, and accepted `/chat/resume`.
- Do not persist completed reports for stopped workflows.
- Add report APIs: `GET /api/reports/{threadId}`, `GET /api/reports/{threadId}/exists`, `DELETE /api/reports/{threadId}`.
- Add a small session report list API if it stays simple.
- Add focused service/controller/runner tests and run `mvn test`.
- Run Docker/PostgreSQL backed verification when practical and stop any manually started backend service.
- Commit the completed stage with a Chinese commit message.

## Scope

- Work only in `C:/MainData/code/Codex_project/M-agent`.
- Inspect `C:/MainData/code/Codex_project/deepresearch-main` as read-only guidance.
- Backend-only scope: persistence, schema, report API, runner integration, tests, and local Docker database setup.
- Do not edit the read-only reference project.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/pom.xml`
- `C:/MainData/code/Codex_project/M-agent/docker-compose.yml`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/test/resources`
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

- Implementation is complete and verified.
- Working tree before commit contains intended changes plus unrelated untracked `.claude/settings.local.json`; do not stage `.claude`.
- Docker Desktop is running.
- `manmu-postgres` is running from `postgres:17-alpine`, healthy, and mapped on host port `5432`.
- A manually started backend service was stopped after verification; port `8080` was confirmed released.

## Completed

- Added `docker-compose.yml` with a `manmu-postgres` PostgreSQL service and named volume.
- Added Spring Data R2DBC, PostgreSQL R2DBC/JDBC, Flyway, JDBC starter, H2 test dependencies, and a test profile activated by Surefire.
- Added Flyway migration `V1__create_research_reports.sql`.
- Added report domain/service/repository/controller classes under `top.lanshan.manmu.report` and `/api/reports`.
- Extended `ResearchState` with `sessionId`.
- Integrated `SimpleResearchRunner` with `ReportService` so final report persistence happens before the `done` event.
- Updated `ChatController` to pass chat `session_id` into the runner.
- Added controller, service, and runner tests covering persistence behavior and stopped-flow non-persistence.
- Switched Docker Compose from `postgres:16-alpine` to locally cached `postgres:17-alpine` after Docker Hub token fetch failed for the first pull attempt.

## Decisions

- Use PostgreSQL 17 Alpine for local Docker because the image is already cached locally and avoids Docker Hub pull flakiness.
- Use R2DBC for app persistence and Flyway through JDBC for schema migration.
- Use H2 in PostgreSQL compatibility mode for automated tests so `mvn test` does not require Docker Desktop.
- Persist completed `/api/research/stream` reports too, with `session_id` equal to `threadId`.
- Do not create `STOPPED` rows in this MVP; stopped workflows leave no completed report row.
- Do not implement `FAILED` rows in this stage.
- Keep `GET /api/reports/session/{sessionId}` because it naturally follows the schema and stays small.

## Evidence / References

- Local completion boundary: `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`.
- Local report API: `src/main/java/top/lanshan/manmu/api/ReportController.java`.
- Local report service: `src/main/java/top/lanshan/manmu/report/PostgresReportService.java`.
- Local schema: `src/main/resources/db/migration/V1__create_research_reports.sql`.
- Reference report controller/service: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/controller/ReportController.java` and `service/ReportService.java`.
- Reference HTTP examples: `C:/MainData/code/Codex_project/deepresearch-main/DeepResearch.http`.

## Files Touched

- `pom.xml`
- `docker-compose.yml`
- `src/main/java/top/lanshan/manmu/api/ChatController.java`
- `src/main/java/top/lanshan/manmu/api/ReportController.java`
- `src/main/java/top/lanshan/manmu/model/ResearchState.java`
- `src/main/java/top/lanshan/manmu/report/PostgresReportService.java`
- `src/main/java/top/lanshan/manmu/report/ReportResponse.java`
- `src/main/java/top/lanshan/manmu/report/ReportService.java`
- `src/main/java/top/lanshan/manmu/report/ReportStatus.java`
- `src/main/java/top/lanshan/manmu/report/ResearchReport.java`
- `src/main/java/top/lanshan/manmu/report/ResearchReportEntity.java`
- `src/main/java/top/lanshan/manmu/report/ResearchReportRepository.java`
- `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- `src/main/resources/application.yml`
- `src/main/resources/db/migration/V1__create_research_reports.sql`
- `src/test/java/top/lanshan/manmu/api/ChatControllerTest.java`
- `src/test/java/top/lanshan/manmu/api/ReportControllerTest.java`
- `src/test/java/top/lanshan/manmu/report/PostgresReportServiceTest.java`
- `src/test/java/top/lanshan/manmu/runner/SimpleResearchRunnerTest.java`
- `src/test/resources/application-test.yml`
- `.codex/tasks/add-postgres-report-persistence.md`

## Commands Run

- `git status --short --branch`
- `git rev-parse --show-toplevel`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` failed because no upstream is configured.
- `git merge-base HEAD '@{upstream}'` failed because no upstream is configured.
- `docker ps`
- `docker version`
- `Start-Service -Name com.docker.service`
- `Start-Process "C:/Program Files/Docker/Docker/Docker Desktop.exe"`
- `docker compose up -d postgres`
- `mvn test`
- `mvn -Dtest=PostgresReportServiceTest test`
- Manual HTTP verification against local app and Docker PostgreSQL:
  - `GET /api/model/current`
  - `POST /chat/stream`
  - `GET /api/reports/{threadId}`
  - `GET /api/reports/{threadId}/exists`
  - `GET /api/reports/session/{sessionId}`
  - `DELETE /api/reports/{threadId}`
  - `GET /api/reports/{threadId}/exists` after delete
- Backend shutdown by stopping the listening process on port `8080`.

## Verification

- `mvn test` passed: 46 tests, 0 failures, 0 errors.
- `mvn -Dtest=PostgresReportServiceTest test` passed: 3 tests, 0 failures, 0 errors.
- Docker/PostgreSQL verification passed with `manmu-postgres` healthy.
- Manual HTTP verification passed:
  - `/chat/stream` emitted `event:done`.
  - `GET /api/reports/verify-report-postgres` returned `status=success` and a non-empty report.
  - `exists` returned `true` before delete.
  - session report list returned one report.
  - `DELETE` returned `status=success`.
  - `exists` returned `false` after delete.
- Port `8080` was released after manual verification.

## Known Failures / Blockers

- No upstream Git branch is configured.
- Initial `docker compose up -d postgres` failed when using `postgres:16-alpine` because Docker Hub token fetch returned EOF. The compose image was changed to locally cached `postgres:17-alpine`, and verification then passed.
- Unrelated untracked `.claude/settings.local.json` exists and must remain uncommitted.
- Failed workflow report rows are not implemented yet.

## Next Actions

- Commit the completed stage with a Chinese commit message.
- In a future stage, decide whether to persist `FAILED` and `STOPPED` lifecycle rows for history views.
- In a future stage, build on this persistence layer for report export/download or frontend report history.

## Open Questions

- Should failed workflows create `FAILED` rows with `error_message` in the next persistence stage?
- Should stopped workflows eventually create `STOPPED` rows for user-visible history, or remain absent?
- Should report history include pagination/search once frontend history exists?

## Avoid / Do Not Redo

- Do not implement an in-memory-only report store.
- Do not edit `C:/MainData/code/Codex_project/deepresearch-main`.
- Do not migrate to Spring AI Alibaba Graph yet.
- Do not add Redis, Elasticsearch, RAG, MCP feature integration, frontend changes, export/download/PDF, or Graph saver/checkpoint infrastructure in this stage.
- Do not introduce mock agent output, mock search, fake reports, or fake search context in production code.
- Do not commit `.local`, `target`, `.idea`, `.claude`, API keys, generated SSE/curl output, or logs.
- Do not leave manually started backend services running after tests.
- Do not undo the completed chat stop/cancellation behavior or its `stopped` SSE contract.

## Resume Prompt
Resume task add-postgres-report-persistence. Read .codex/tasks/add-postgres-report-persistence.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
