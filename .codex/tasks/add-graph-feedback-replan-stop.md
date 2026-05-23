# Task Handoff: add-graph-feedback-replan-stop
Updated: 2026-05-23 12:47:30 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 51fc282c95f0061208a5e899c1d54aadf0ebff78
Current Commit: pending local stage 6 commit

## Project Mainline

- This repository is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under package `top.lanshan.manmu`.
- The long-running project direction is a simplified but real production-path DeepResearch workflow: real LLM calls, real search provider calls, WebFlux SSE progress, PostgreSQL-backed persistence, pause/resume/stop lifecycle, and no runtime mock fallback.
- The Graph migration is intentionally staged and reversible. `SimpleResearchRunner` remains the default through `mvp.research.runner=simple`; the Graph runner is still opt-in through `mvp.research.runner=graph`.
- Stage 1 completed runner selection and Graph dependency scaffolding.
- Stage 2 completed `ResearchGraphState` and `ResearchGraphStateKeys`.
- Stage 3 completed `ResearchNodeGraphAction`.
- Stage 4 completed the explicit Graph auto-complete runner path.
- Stage 5 completed Graph manual plan-gate pause and accepted resume.
- Stage 6 now implements rejected feedback replan, maximum-iteration continuation, paused stop, and running stop for the explicit Graph runner.
- Future stage 7 can consider switching the default runner to Graph after this stage is reviewed.

## Stage Role

- This is stage 6 of the Graph migration: `add-graph-feedback-replan-stop`.
- The stage closes the remaining chat lifecycle gap for the explicit Graph runner.
- The target behavior is parity with the relevant `SimpleResearchRunner` lifecycle behavior while preserving the current default runner.

## Completed Implementation

- `ResearchGraphBuilder` now exposes `buildResumeGraph(SessionContextService)`:
  - starts from `human_feedback`;
  - routes accepted feedback to `information`;
  - routes rejected feedback below the max iteration limit to `planner`;
  - routes waiting feedback to `END`;
  - runs `planner -> plan_validator` and then either retries planning, pauses again at `human_feedback`, or continues to research execution.
- `GraphResearchRunner` now uses the full resume graph instead of an accepted-only resume graph.
- Rejected `/chat/resume` now writes `ResumeDecision.accepted=false` and feedback content into `ResearchState`, reruns `human_feedback`, replans, validates, and pauses again when manual review is still required.
- Rejected feedback at the max plan-iteration limit now continues from `human_feedback` to research execution, saves the completed report, marks history `COMPLETED`, and emits final `done`.
- Pause detection now checks `HumanFeedbackRoute.WAITING` as well as the last emitted node. This fixes a real HTTP/SSE issue where Graph output could finish with `END` after `human_feedback`, causing the runner to attempt report persistence while still paused.
- `GraphResearchRunner.stopAndRecord(threadId)` now:
  - returns `false` for null, blank, or unknown thread ids;
  - stops active Graph execution and marks history `STOPPED`;
  - clears paused Graph state and marks history `STOPPED`.
- Active Graph runs now use the same sink-based stop pattern as `SimpleResearchRunner`, emitting the existing stopped event shape where the stream is still attached.
- Focused tests were added for rejected replan, max-iteration continuation, paused stop, running stop, missing stopped state, and existing accepted/auto paths.

## Files Touched

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/graph/ResearchGraphBuilder.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/GraphResearchRunner.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/runner/GraphResearchRunnerTest.java`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-graph-feedback-replan-stop.md`

## Verification

- Focused Java 17 tests passed:
  - `mvn -Dtest=GraphResearchRunnerTest test`
- Packaging passed:
  - `mvn -DskipTests package`
- Full Java 17 test suite was run:
  - `mvn test`
  - Result: one external-provider failure in `ResearchControllerLlmWorkflowTest.dashScopeAndDeepSeekCanDriveTheSameResearchStreamWorkflow`.
  - Failure reason: DashScope returned real provider throttling, `429 Throttling.RateQuota`; this was not caused by the Graph runner changes.
- Docker PostgreSQL was checked with Docker tooling and `manmu-postgres` was running and healthy on port `5432`.
- The backend was started locally from the packaged jar with explicit Graph runner configuration on port `18080`.
- Real HTTP/SSE rejected replan was verified with DeepSeek as the active model:
  - `/chat/stream` with `auto_accepted_plan=false` reached `human_feedback`;
  - `/chat/resume` with `feedback=false` emitted `human_feedback -> planner -> plan_validator -> human_feedback`;
  - session history returned `PAUSED`;
  - report existence remained false while paused.
- Real HTTP/SSE paused stop was verified:
  - `/chat/stop` returned success for a paused Graph thread;
  - history moved to `STOPPED`;
  - a later `/chat/resume` returned the expected `human_feedback` error for missing paused state.
- Real HTTP/SSE max-iteration continuation was verified:
  - after repeated rejected feedback through the default max plan-iteration limit, `human_feedback` routed to `RESEARCH_TEAM`;
  - the stream continued through research execution and emitted final `done`;
  - history moved to `COMPLETED`;
  - report existence returned true.
- Real HTTP/SSE running stop was verified:
  - an active Graph stream emitted `coordinator`;
  - `/chat/stop` returned success;
  - the stream emitted `event:stopped`;
  - history moved to `STOPPED`;
  - no report was saved for the stopped thread.
- The backend service was stopped after verification and port `18080` was confirmed closed.

## Known Failures / Blockers

- Full `mvn test` is currently blocked by real DashScope quota throttling in an LLM smoke test:
  - `ResearchControllerLlmWorkflowTest.dashScopeAndDeepSeekCanDriveTheSameResearchStreamWorkflow`
  - provider response: `429 Throttling.RateQuota`
- A previous running-stop HTTP attempt also hit a real provider timeout before stop arrived; the later immediate-stop verification succeeded.

## Decisions

- Keep `SimpleResearchRunner` unchanged and use it as the lifecycle reference.
- Keep `application.yml` default runner as `simple`.
- Keep Graph paused and running state in memory for this stage; do not introduce durable checkpoint storage yet.
- Do not change controllers; existing `/chat/stream`, `/chat/resume`, and `/chat/stop` already route through `ResearchRunner`.
- Do not add Redis, RAG, MCP, front-end work, professional KB, coder agents, or full parallel execution.

## Useful Next Checks

- After DashScope quota recovers, rerun full `mvn test` with Java 17.
- In stage 7, before switching the default runner, rerun a full real HTTP/SSE smoke pass against `mvp.research.runner=graph`.
- Consider exposing `max_plan_iterations` through `ChatRequest` only if future product work needs faster manual testing of max-iteration behavior through `/chat/stream`; this stage verified it by repeated rejected resumes without changing the API.

## Commands Run

- `git status --short`
- `git diff --stat`
- `git diff -- src/main/java/top/lanshan/manmu/runner/GraphResearchRunner.java`
- `git diff -- src/main/java/top/lanshan/manmu/graph/ResearchGraphBuilder.java`
- `git diff -- src/test/java/top/lanshan/manmu/runner/GraphResearchRunnerTest.java`
- `mvn -Dtest=GraphResearchRunnerTest test`
- `mvn test`
- `mvn -DskipTests package`
- `docker_container_list`
- `curl.exe -sS http://localhost:18080/api/model/current`
- `java -jar target/deepresearch-mvp-0.1.0-SNAPSHOT.jar --server.port=18080 --mvp.research.runner=graph`
- `curl.exe -N -sS -X POST http://localhost:18080/chat/stream ...`
- `curl.exe -N -sS -X POST http://localhost:18080/chat/resume ...`
- `curl.exe -sS -X POST http://localhost:18080/chat/stop ...`
- `curl.exe -sS http://localhost:18080/api/sessions/{sessionId}/threads/{threadId}`
- `curl.exe -sS http://localhost:18080/api/reports/{threadId}/exists`
- `Stop-Process -Id <java-service-pid> -Force`
- `Get-NetTCPConnection -LocalPort 18080 -State Listen`
