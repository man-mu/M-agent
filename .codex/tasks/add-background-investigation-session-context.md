# Task Handoff: add-background-investigation-session-context
Updated: 2026-05-22 14:50:11 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 6e5d3142220dbf41fca3716d0724744fb5f79f7a
Current Commit: 6e5d3142220dbf41fca3716d0724744fb5f79f7a

## Project Mainline

- This project is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under `C:/MainData/code/Codex_project/M-agent`.
- The long-term direction is to align with `C:/MainData/code/Codex_project/deepresearch-main` in a deliberately reduced, runnable backend-first form.
- The project evolves DeepResearch workflow semantics in `SimpleResearchRunner` before adopting the full Spring AI Alibaba Graph stack.
- Completed stages now provide real model calls, Bocha-only real web search, planner/researcher/processor/reporter execution, a lightweight `research_team` loop, human feedback plan pause/resume/replan, running `/chat/stop` cancellation, PostgreSQL-backed completed report persistence, PostgreSQL-backed session history persistence, and lightweight background/session context injection before planning.
- Durable artifacts now include `research_reports` for final report bodies and `research_session_histories` for lifecycle/query/report-link history; this stage turns those artifacts into active planner context for later runs in the same session.
- The next mainline step can build on this context bridge toward richer background investigation or report continuity, while still avoiding full RAG, Redis, MCP, Elasticsearch, frontend, or Graph checkpoint migration unless explicitly requested.

## Stage Role in Mainline

- This stage turned persisted history from a queryable artifact into active research context.
- It exists because `add-session-history-persistence` could list completed reports and lifecycle states, but the next run did not yet use earlier reports from the same session.
- It aligns with `deepresearch-main` `BackgroundInvestigationNode` in reduced form: before planning, M-agent now reads recent completed reports for the same session and gives compact report excerpts to the planner.
- It remains backend-only and reuses existing PostgreSQL/R2DBC report and session-history services, without adding RAG, Redis, MCP, Elasticsearch, frontend, or full Graph checkpointing.

## Mainline Progression

- `add-research-team` introduced the controlled research loop.
- `add-information-node-bocha-search` added real Bocha information gathering.
- `add-processor-node-search-context` added the processor path for PROCESSING steps.
- `add-human-feedback-plan-gate` added planner pause/resume/replan.
- `add-chat-stop-session-lifecycle` and `add-running-chat-stop-cancellation` made paused and running chat sessions stoppable.
- `add-postgres-report-persistence` added durable completed reports.
- `add-session-history-persistence` added durable session/task lifecycle history and recent-history APIs.
- `add-background-investigation-session-context` now uses durable session history and report storage as input to future planning, creating a small bridge toward the reference project's `background_investigator -> planner` flow while staying in M-agent's simpler runner/node architecture.

## Related Stage Handoffs

- `add-session-history-persistence`: immediate upstream; completed and committed as `49bcae5` with a Chinese message meaning "Add session history persistence".
- `add-postgres-report-persistence`: upstream durable report storage used by this stage.
- `add-human-feedback-plan-gate`: planner pause/resume behavior remains compatible after adding background context.
- Earlier task handoffs under `.codex/tasks/` preserve the cross-stage project story.

## Goal

- Add a lightweight background-investigation/session-context capability that reads recent completed reports for the same `session_id`, formats them as useful context, and feeds that context into planning so a second research run can benefit from prior completed reports in the same session.

## Task Theme / User Intent

- Continue aligning M-agent with `C:/MainData/code/Codex_project/deepresearch-main` while keeping the implementation small and runnable.
- Follow from the completed session history persistence stage.
- Implement the smallest backend capability corresponding to the reference project's use of recent session reports in background investigation, not the whole Graph/RAG/MCP stack.
- Keep real model/search paths and avoid mock production behavior; tests may use isolated stubs.

## Acceptance Criteria

- Done: Added `SessionContextReport` and `SessionContextService` under `top.lanshan.manmu.sessioncontext`.
- Done: Added `PostgresSessionContextService`, which reads recent histories from `SessionHistoryService`, follows `report_thread_id` through `ReportService.getReport(...)`, excludes the current thread, and returns bounded formatted report context.
- Done: Context is bounded by configurable defaults: `manmu.session-context.max-reports` default `5`, and `manmu.session-context.max-report-characters` default `1200`.
- Done: Added `ResearchState.backgroundContext`.
- Done: `SimpleResearchRunner` loads background context before planning for normal run, chat run, plan-gated run, and rejected-feedback replan.
- Done: `PlannerNode` passes background context to `PlannerAgent`; `LlmPlannerAgent` injects it into the user prompt when present.
- Done: `/api/research/stream` and `/chat/stream` behavior remains compatible. `/api/research/stream` keeps `session_id = threadId`; chat continues to use request `session_id`.
- Done: Human feedback plan gate, accepted resume, rejected replan, stop, completion, and failed history tests remain valid.
- Done: Focused tests cover context formatting/loading, current-thread exclusion, empty new-session context, count/size limits, planner context propagation, and runner loading before planning.
- Done: Java 17 `mvn test` passed with 61 tests.
- Not run: Optional Docker/PostgreSQL curl verification, because full Maven tests including real model workflow already passed and no manually started backend was needed.
- Done: Implementation committed with Chinese commit message `添加背景调查会话上下文`.

## Scope

- Work only in `C:/MainData/code/Codex_project/M-agent`.
- Inspect `C:/MainData/code/Codex_project/deepresearch-main` as read-only guidance.
- Backend-only scope: services, model/state additions, node/runner integration, prompt input changes, tests, and optional Docker-backed verification.
- Do not edit the read-only reference project.
- Do not add frontend, RAG, MCP integration features, Redis, Elasticsearch, export/download/PDF, or full Spring AI Alibaba Graph migration in this stage.
- Do not add a new database table; this stage reuses `research_session_histories` and `research_reports`.
- Preserve real provider paths and do not introduce mock production behavior.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/pom.xml`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/test/resources`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-background-investigation-session-context.md`
- Optional update: `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-session-history-persistence.md` only if correcting cross-stage details.

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
- Current commit before handoff update: `6e5d3142220dbf41fca3716d0724744fb5f79f7a` (`添加背景调查会话上下文`).
- No upstream Git branch is configured for `main`.
- Implementation commit is complete and tested.
- Working tree after implementation commit had only unrelated untracked `.claude/`; do not edit, delete, stage, or commit it.
- No backend service was manually started, so there is no manually started process to stop.

## Completed

- Added `top.lanshan.manmu.sessioncontext.SessionContextReport`.
- Added `top.lanshan.manmu.sessioncontext.SessionContextService`.
- Added `top.lanshan.manmu.sessioncontext.PostgresSessionContextService`:
  - reads recent histories by session,
  - filters to `COMPLETED`,
  - requires `report_thread_id`,
  - excludes current thread/report thread,
  - fetches linked report text,
  - strips and truncates report excerpts,
  - formats compact per-report context blocks.
- Added `ResearchState.backgroundContext`.
- Extended `PlannerAgent` with a default four-argument `plan(...)` overload.
- Updated `LlmPlannerAgent` to include a `Recent completed reports from the same session` prompt section when context exists.
- Updated `PlannerNode` to pass state background context to the planner agent.
- Updated `SimpleResearchRunner` to load background context before planner execution and defer planner node execution until after context is loaded.
- Added tests:
  - `PostgresSessionContextServiceTest`
  - `PlannerNodeTest`
  - `LlmPlannerAgentTest`
  - new runner coverage in `SimpleResearchRunnerTest`.
- Committed implementation as `6e5d314` with message `添加背景调查会话上下文`.

## Decisions

- Use a lightweight session-context service rather than adding a visible SSE `background_investigator` node/event in this first implementation.
- Pass compact prior-report context into planner prompt input first; a dedicated background LLM agent can be added later if needed.
- Keep context bounded by count and per-report character cap to protect prompt budget.
- Reuse `SessionHistoryService.findRecentBySessionId(...)` and `ReportService.getReport(...)` instead of adding a new table.
- Keep tests isolated from external provider availability by testing prompt propagation and service formatting with stubs.

## Evidence / References

- M-agent implementation files:
  - `src/main/java/top/lanshan/manmu/sessioncontext/SessionContextReport.java`
  - `src/main/java/top/lanshan/manmu/sessioncontext/SessionContextService.java`
  - `src/main/java/top/lanshan/manmu/sessioncontext/PostgresSessionContextService.java`
  - `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
  - `src/main/java/top/lanshan/manmu/model/ResearchState.java`
  - `src/main/java/top/lanshan/manmu/node/PlannerNode.java`
  - `src/main/java/top/lanshan/manmu/agent/PlannerAgent.java`
  - `src/main/java/top/lanshan/manmu/agent/LlmPlannerAgent.java`
- M-agent tests:
  - `src/test/java/top/lanshan/manmu/sessioncontext/PostgresSessionContextServiceTest.java`
  - `src/test/java/top/lanshan/manmu/node/PlannerNodeTest.java`
  - `src/test/java/top/lanshan/manmu/agent/LlmPlannerAgentTest.java`
  - `src/test/java/top/lanshan/manmu/runner/SimpleResearchRunnerTest.java`
- Reference project files inspected:
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/BackgroundInvestigationNode.java`
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/service/SessionContextService.java`
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/PlannerNode.java`

## Files Touched

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/agent/LlmPlannerAgent.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/agent/PlannerAgent.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchState.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/PlannerNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/sessioncontext/PostgresSessionContextService.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/sessioncontext/SessionContextReport.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/sessioncontext/SessionContextService.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/agent/LlmPlannerAgentTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/node/PlannerNodeTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/runner/SimpleResearchRunnerTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/sessioncontext/PostgresSessionContextServiceTest.java`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-background-investigation-session-context.md`

## Commands Run

- `Get-Content -Path C:\Users\20232\.codex\skills\task-handoff\SKILL.md`
- `git status --short --branch`
- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name @{upstream}` (failed: no upstream configured)
- `git merge-base HEAD @{upstream}` (failed: no upstream configured)
- `Get-Content -Path AGENTS.md`
- `git log --oneline --decorate -8`
- `rg --files src/main/java/top/lanshan/manmu src/main/resources src/test/java/top/lanshan/manmu src/test/resources`
- `Get-Content -Path` for M-agent runner, planner, agent, state, report, session history, prompt, and tests.
- `Get-Content -Path` for reference `BackgroundInvestigationNode.java`, `SessionContextService.java`, and `PlannerNode.java`.
- `mvn '-Dtest=PostgresSessionContextServiceTest,PlannerNodeTest,LlmPlannerAgentTest,SimpleResearchRunnerTest' test` with Java 17: first run after code changes found a runner deferral test issue, second run passed 17 tests.
- `mvn test` with Java 17: passed 61 tests.
- `git diff --check`: no whitespace errors; only CRLF conversion warnings.
- `git add -- ...`
- `git commit -m "添加背景调查会话上下文"`: created `6e5d314`.

## Verification

- Targeted tests passed after fixing planner execution deferral:
  - `PostgresSessionContextServiceTest`
  - `PlannerNodeTest`
  - `LlmPlannerAgentTest`
  - `SimpleResearchRunnerTest`
  - 17 tests total.
- Full Java 17 `mvn test` passed:
  - 61 tests, 0 failures, 0 errors, 0 skipped.
- Real model workflow tests included in full suite passed in this environment.
- `git diff --check` reported no whitespace errors, only Windows CRLF conversion warnings.
- No manual backend service was started; no port `8080` cleanup was required.

## Known Failures / Blockers

- No upstream Git branch is configured.
- Unrelated untracked `.claude/` exists and must remain uncommitted.
- `.gitignore` ignores `.local/`, `target/`, and `.idea/`, but not `.claude/`; project instructions still forbid editing or committing `.claude/`.
- Some existing Chinese strings in source appear mojibake in terminal output; this stage did not edit unrelated display-title/comment text.

## Next Actions

- Optional next stage: expose a lightweight `background_investigator` SSE event or debug payload if the UI/API needs visibility into which prior reports influenced planning.
- Optional next stage: add a dedicated background LLM agent that summarizes recent reports before planner input if raw excerpts become too noisy.
- Optional next stage: run Docker/PostgreSQL-backed curl verification with two chat runs in the same session if manual end-to-end evidence is desired beyond the passing test suite.

## Open Questions

- Should background/session context be visible to clients as a first-class stream event, or remain internal planner context?
- Should later context include only report excerpts, model-generated summaries, or structured report metadata and citations?
- Should failed/stopped queries ever be included as negative or avoidance context, or should the source stay limited to completed reports?

## Avoid / Do Not Redo

- Do not reimplement session history or report persistence from scratch.
- Do not edit `C:/MainData/code/Codex_project/deepresearch-main`.
- Do not introduce a full Graph migration in this stage.
- Do not add RAG, MCP, Redis, Elasticsearch, frontend, export/download/PDF, or short-term memory unless the user explicitly changes scope.
- Do not introduce mock agent output, mock search, fake reports, or fake search context in production code.
- Do not commit `.local`, `target`, `.idea`, `.claude`, API keys, generated SSE/curl output, or logs.
- Do not leave manually started backend services running after tests.
- Do not break existing human feedback plan gate, running stop/cancellation, report persistence, or session history lifecycle behavior.

## Resume Prompt
Resume task add-background-investigation-session-context. Read .codex/tasks/add-background-investigation-session-context.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
