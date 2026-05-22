# Task Handoff: add-background-investigation-session-context
Updated: 2026-05-22 14:17:00 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 4fc8166db06f0635e5eb9df925f2083125444e0f
Current Commit: 4fc8166db06f0635e5eb9df925f2083125444e0f

## Project Mainline

- This project is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under `C:/MainData/code/Codex_project/M-agent`.
- The long-term direction is to align with `C:/MainData/code/Codex_project/deepresearch-main` in a deliberately reduced, runnable backend-first form.
- The project evolves DeepResearch workflow semantics in `SimpleResearchRunner` before adopting the full Spring AI Alibaba Graph stack.
- Completed stages now provide real model calls, Bocha-only real web search, planner/researcher/processor/reporter execution, a lightweight `research_team` loop, human feedback plan pause/resume/replan, running `/chat/stop` cancellation, PostgreSQL-backed completed report persistence, and PostgreSQL-backed session history persistence.
- Durable artifacts now include `research_reports` for final report bodies and `research_session_histories` for lifecycle/query/report-link history.
- The next mainline step is to make durable session history useful to the next research run by feeding recent completed reports into a lightweight background-investigation/session-context phase, matching the reference project's `BackgroundInvestigationNode` use of `SessionContextService.getRecentReports(sessionId)`.

## Stage Role in Mainline

- This stage should turn persisted history from a queryable artifact into active research context.
- It exists because `add-session-history-persistence` can list completed reports and lifecycle states, but the next run does not yet use earlier reports from the same session.
- It should align with `deepresearch-main` `BackgroundInvestigationNode`: before planning, gather current background information and include recent session reports as context for the background agent/planner.
- It should remain a small backend-only implementation that reuses existing PostgreSQL/R2DBC report and session-history services, without adding RAG, Redis, MCP, Elasticsearch, frontend, or full Graph checkpointing.

## Mainline Progression

- `add-research-team` introduced the controlled research loop.
- `add-information-node-bocha-search` added real Bocha information gathering.
- `add-processor-node-search-context` added the processor path for PROCESSING steps.
- `add-human-feedback-plan-gate` added planner pause/resume/replan.
- `add-chat-stop-session-lifecycle` and `add-running-chat-stop-cancellation` made paused and running chat sessions stoppable.
- `add-postgres-report-persistence` added durable completed reports.
- `add-session-history-persistence` added durable session/task lifecycle history and recent-history APIs.
- `add-background-investigation-session-context` should use that durable history as input to future planning/reporting, creating a bridge toward the reference project's `background_investigator -> planner/reporter` flow while staying in M-agent's simpler runner/node architecture.

## Related Stage Handoffs

- `add-session-history-persistence`: immediate upstream; completed and committed as `49bcae5` with a Chinese message meaning "Add session history persistence".
- `add-postgres-report-persistence`: upstream durable report storage used by this stage.
- `add-human-feedback-plan-gate`: planner pause/resume behavior must continue to work after adding background context.
- Earlier task handoffs under `.codex/tasks/` preserve the cross-stage project story.

## Goal

- Add a lightweight background-investigation/session-context capability that reads recent completed reports for the same `session_id`, formats them as useful context, and feeds that context into planning and/or reporting so a second research run can benefit from prior completed reports in the same session.

## Task Theme / User Intent

- The user will start a new conversation to implement `add-background-investigation-session-context` and continue aligning M-agent with `C:/MainData/code/Codex_project/deepresearch-main`.
- The user wants the next step to follow from the completed session history persistence stage.
- The recommended direction is to implement the smallest runnable backend capability corresponding to `deepresearch-main` `BackgroundInvestigationNode`, not to migrate the whole Graph/RAG/MCP stack.
- The implementation should keep real model/search paths, use Docker Desktop PostgreSQL on Windows, and avoid mock production behavior.

## Acceptance Criteria

- Add a minimal background/session-context model, for example a `SessionContextReport` record or a formatted context string derived from recent `COMPLETED` histories.
- Add service behavior that reads recent completed session histories from `SessionHistoryService`, follows `report_thread_id` into `ReportService`, and returns bounded report context for the same session.
- Exclude the current thread from its own recent-report context when possible.
- Limit context size and count to avoid injecting entire large reports blindly; recommended defaults: 3 to 5 reports and a clear per-report character cap.
- Add a lightweight `BackgroundInvestigationNode` or equivalent service that can run before `planner`.
- Extend `ResearchState` to hold background/session context, for example `backgroundContext` or `backgroundInvestigationResults`.
- Feed background/session context into `LlmPlannerAgent` prompt input so planning can consider previous completed reports.
- Keep `/api/research/stream` and `/chat/stream` behavior compatible; chat should use the request `session_id`, while `/api/research/stream` can continue using `session_id = threadId`.
- Preserve human feedback plan gate, resume/replan, stop, completion, and failed history behavior.
- Add focused tests showing:
  - recent completed reports are loaded and formatted for a new run in the same session,
  - the current thread is excluded,
  - no context is produced for a new session,
  - planner receives context when it exists,
  - existing lifecycle/history tests remain valid.
- Run Java 17 `mvn test`.
- When practical, run Docker/PostgreSQL-backed curl verification: create one completed report in a session, start a second run in that session, confirm background/session context is observable through an event or testable output, then stop any manually started backend and confirm port `8080` is released.
- Commit the completed stage with a Chinese commit message.

## Scope

- Work only in `C:/MainData/code/Codex_project/M-agent`.
- Inspect `C:/MainData/code/Codex_project/deepresearch-main` as read-only guidance.
- Backend-only scope: services, model/state additions, node/runner integration, prompt input changes, tests, and optional Docker-backed verification.
- Do not edit the read-only reference project.
- Do not add frontend, RAG, MCP integration features, Redis, Elasticsearch, export/download/PDF, or full Spring AI Alibaba Graph migration in this stage.
- Do not add a new database table unless a clear need appears; prefer reusing `research_session_histories` and `research_reports`.
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
- Current commit: `4fc8166db06f0635e5eb9df925f2083125444e0f` (`update session history persistence task handoff`).
- Immediate implementation commit for upstream stage: `49bcae5a1294de3443133d1859c93bce10e0d73e` (`add session history persistence`).
- No upstream Git branch is configured for `main`.
- Working tree has only unrelated untracked `.claude/settings.local.json`; do not edit, delete, stage, or commit it.
- No code implementation has started for `add-background-investigation-session-context`.
- No backend service should be running on port `8080`; the prior stage stopped its manually started backend and confirmed port release.

## Completed

- Completed upstream session history persistence:
  - Added `research_session_histories` Flyway migration.
  - Added `top.lanshan.manmu.sessionhistory` service/repository/entity/response classes.
  - Added `/api/sessions/{sessionId}/history`, `/api/sessions/{sessionId}/threads/{threadId}`, and `/api/sessions/{sessionId}/recent`.
  - Wired `SimpleResearchRunner` lifecycle transitions into durable session history.
  - Kept report bodies in `research_reports` and linked histories by `report_thread_id`.
  - Passed `mvn test` with 55 tests.
  - Verified completed, paused, stopped, and recent history via Docker/PostgreSQL-backed curl.
- Inspected reference `deepresearch-main` background context flow:
  - `BackgroundInvestigationNode` gets `session_id`, calls `sessionContextService.getRecentReports(sessionId)`, builds an assistant message containing prior DeepResearch reports, and asks `backgroundAgent` for background results.
  - `PlannerNode` consumes `background_investigation_results` when `enable_deepresearch` is true.
  - `DeepResearchConfiguration` wires `background_investigator` before planner/reporter in the full Graph.

## Decisions

- Recommended next stage is a lightweight session-context/background-investigation step, not RAG, MCP, Redis, Elasticsearch, frontend, or full Graph migration.
- Reuse `SessionHistoryService.findRecentBySessionId(...)` and `ReportService.getReport(...)` or `ReportService.findBySessionId(...)` rather than adding a new persistence table.
- Prefer passing compact prior-report context into planner prompt input first; a dedicated background LLM agent can be added only if the codebase shape makes it worthwhile.
- Keep context bounded by count and size to protect prompt budget.
- Make the behavior testable without requiring real external provider calls in unit/service tests.

## Evidence / References

- M-agent upstream files:
  - `src/main/java/top/lanshan/manmu/sessionhistory/SessionHistoryService.java`
  - `src/main/java/top/lanshan/manmu/sessionhistory/PostgresSessionHistoryService.java`
  - `src/main/java/top/lanshan/manmu/report/ReportService.java`
  - `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
  - `src/main/java/top/lanshan/manmu/model/ResearchState.java`
  - `src/main/java/top/lanshan/manmu/agent/PlannerAgent.java`
  - `src/main/java/top/lanshan/manmu/agent/LlmPlannerAgent.java`
  - `src/main/resources/prompts/planner.md`
- Reference project files:
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/BackgroundInvestigationNode.java`
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/dispatcher/BackgroundInvestigationDispatcher.java`
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/PlannerNode.java`
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/config/DeepResearchConfiguration.java`
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/service/SessionContextService.java`
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/model/SessionHistory.java`

## Files Touched

- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-background-investigation-session-context.md`

## Commands Run

- `Get-Content -Raw C:\Users\20232\.codex\skills\task-handoff\SKILL.md`
- `git status --short --branch --untracked-files=all`
- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name @{upstream}`
- `git log --oneline --decorate -8`
- `Get-Content -Raw AGENTS.md`
- `Get-Content -Raw .codex\tasks\add-session-history-persistence.md`
- `Get-Content -Raw C:\MainData\code\Codex_project\deepresearch-main\src\main\java\com\alibaba\cloud\ai\example\deepresearch\node\BackgroundInvestigationNode.java`
- `Get-Content -Raw C:\MainData\code\Codex_project\deepresearch-main\src\main\java\com\alibaba\cloud\ai\example\deepresearch\dispatcher\BackgroundInvestigationDispatcher.java`
- `Get-Content -Raw C:\MainData\code\Codex_project\deepresearch-main\src\main\java\com\alibaba\cloud\ai\example\deepresearch\node\PlannerNode.java`
- `rg "background|Background|recentReports|getRecentReports|SessionHistory|session history|history" ...`
- `Get-Content -Raw` for M-agent `PlannerAgent`, `LlmPlannerAgent`, `ResearchState`, `SessionHistoryService`, `ReportService`, and `prompts/planner.md`

## Verification

- No code implementation has started for this stage.
- Handoff creation inspected the current git state and relevant reference files.
- Current workspace has no tracked diff before writing this handoff.
- Upstream stage verification from `add-session-history-persistence`:
  - Java 17 `mvn test` passed with 55 tests.
  - Docker/PostgreSQL-backed curl verification passed for completed, paused, stopped, and recent session history.
  - Manually started backend service was stopped and port `8080` released.

## Known Failures / Blockers

- No upstream Git branch is configured.
- Unrelated untracked `.claude/settings.local.json` exists and must remain uncommitted.
- `.gitignore` ignores `.local/`, `target/`, and `.idea/`, but not `.claude/`; project instructions still forbid editing or committing `.claude/`.
- Real LLM/search verification may fail from external network/API issues; keep unit/service tests isolated from provider availability.
- Some existing Chinese strings in source appear mojibake in terminal output because of encoding display issues; avoid editing unrelated display-title/comment text unless required.

## Next Actions

- Design the minimal session-context service/model that loads recent completed histories for a session, fetches linked report text from `ReportService`, excludes the current thread, and returns bounded formatted context.
- Add the background/session context into `ResearchState`, runner flow before planner execution, and `LlmPlannerAgent` prompt input; add focused tests for context loading and planner prompt propagation.
- Run Java 17 `mvn test`, optionally run Docker/PostgreSQL curl verification for two runs in the same session, stop any manually started backend service, then commit with a Chinese message.

## Open Questions

- Should the first implementation expose background/session context as a visible SSE `background_investigator` event, or keep it internal and verify through tests?
- Should `PlannerAgent.plan(...)` gain a fourth `backgroundContext` argument, or should context be folded into `ResearchState` and read by `PlannerNode` before calling the agent?
- Should prior report context include full report snippets, only summaries/headings, or a fixed character window per report?
- Should the context source include only `COMPLETED` histories with `report_thread_id`, or also failed/stopped queries as negative context later?

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
