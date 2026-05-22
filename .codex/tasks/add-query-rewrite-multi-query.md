# Task Handoff: add-query-rewrite-multi-query
Updated: 2026-05-22 15:27:25 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 081a2d491aefbe271b476605d9f5af2556fa1529
Current Commit: 081a2d491aefbe271b476605d9f5af2556fa1529

## Project Mainline

- This project is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under `C:/MainData/code/Codex_project/M-agent`.
- The long-term direction is to align with `C:/MainData/code/Codex_project/deepresearch-main` in a deliberately reduced, runnable backend-first form.
- The project evolves DeepResearch workflow semantics in `SimpleResearchRunner` before adopting the full Spring AI Alibaba Graph stack.
- Completed stages now provide real model calls, Bocha-only real web search, planner/researcher/processor/reporter execution, a lightweight `research_team` loop, human feedback plan pause/resume/replan, running `/chat/stop` cancellation, PostgreSQL-backed completed report persistence, PostgreSQL-backed session history persistence, and lightweight background/session context injection before planning.
- The project now requires every completed stage to be validated with both `mvn test` and real curl end-to-end testing through the local backend, real Docker-backed middleware, and real model-provider paths. A prior curl check found a runtime Reactor Netty blocking issue that unit tests did not catch, so this requirement is part of the mainline.
- The next mainline step should add a lightweight `rewrite_multi_query` capability before planning/background context, matching the reference project's pre-planner query optimization semantics without importing full RAG, MCP, Redis, Elasticsearch, frontend, or full Graph checkpoint migration.

## Stage Role in Mainline

- This stage should implement the next missing pre-planner module from `deepresearch-main`: query rewriting plus multi-query optimization.
- It exists now because M-agent already has durable session/report context feeding the planner, but it still sends only the raw user query into planning and downstream information gathering.
- In `deepresearch-main`, the relevant path is `coordinator -> rewrite_multi_query -> background_investigator -> planner`. M-agent does not yet need the full Graph structure, but it should introduce the same reduced semantic step in the current runner/node architecture.
- This stage should make downstream planning and search less brittle by turning one user query into a small, bounded set of optimized research queries produced through the real LLM path.

## Mainline Progression

- `add-research-team` introduced the controlled research loop.
- `add-information-node-bocha-search` added real Bocha information gathering.
- `add-processor-node-search-context` added the processor path for PROCESSING steps.
- `add-human-feedback-plan-gate` added planner pause/resume/replan.
- `add-chat-stop-session-lifecycle` and `add-running-chat-stop-cancellation` made paused and running chat sessions stoppable.
- `add-postgres-report-persistence` added durable completed reports.
- `add-session-history-persistence` added durable session/task lifecycle history and recent-history APIs.
- `add-background-investigation-session-context` uses durable session history and report storage as planner context.
- `add-query-rewrite-multi-query` should now add optimized-query generation before background context and planner execution, creating a clearer bridge toward the reference project's Graph flow while staying backend-only.

## Related Stage Handoffs

- `add-background-investigation-session-context`: immediate upstream; completed background/session context and proved the need for curl E2E testing after a runtime thread-switch issue.
- `add-session-history-persistence`: upstream durable session lifecycle source.
- `add-postgres-report-persistence`: upstream durable report storage source.
- `add-information-node-bocha-search`: downstream real web search path that may consume optimized queries later.
- Earlier task handoffs under `.codex/tasks/` preserve the cross-stage project story.

## Goal

- Add a lightweight `rewrite_multi_query` stage to M-agent that rewrites the user's research query and produces a bounded list of optimized queries through a real model-provider path, then feeds those optimized queries into planner/search context while preserving the existing runner, SSE, persistence, stop, and human-feedback behavior.

## Task Theme / User Intent

- Continue aligning `C:/MainData/code/Codex_project/M-agent` with `C:/MainData/code/Codex_project/deepresearch-main`.
- Implement only the small, useful module corresponding to `RewriteAndMultiQueryNode`, not the whole Graph/RAG/MCP stack.
- Understand and preserve the purpose of this module: improve planner and search recall by turning a raw user question into better search/planning formulations, usually including the original query and a few related optimized queries.
- Keep production behavior real: no mock rewrite agent, no fake optimized queries, no fake search, and no hidden fallback that masks provider failures.
- Finish with both unit/integration tests and curl-based production-shape verification.

## Acceptance Criteria

- Add state/model support for optimized queries while preserving the original query. Suggested names are `optimizedQueries` in Java and an SSE payload field compatible with the reference concept `optimize_queries`.
- Add a real LLM-backed query rewrite/multi-query component, such as `QueryRewriteAgent` plus `LlmQueryRewriteAgent`, or an equivalently local naming pattern that fits the repo.
- Bound optimized query count to the reference range `0..5`, with a simple default of `3` unless existing project configuration suggests another value.
- Include the original query in the optimized query set unless an explicit count of `0` is configured or requested.
- Add a `rewrite_multi_query` node or runner stage that executes before background/session context loading and before `planner`.
- Emit a visible SSE event for observability, such as node `rewrite_multi_query`, status `completed`, and payload containing the optimized queries.
- Feed optimized queries into the planner prompt so planning can use the expanded query intent.
- Prefer a small downstream search use if straightforward and testable, for example allowing `InformationNode` or the query-building prompt to see optimized queries. Do not overreach if planner prompt integration is the safer first step.
- Keep `/api/research/stream`, `/chat/stream`, human feedback accepted resume, rejected replan, running stop, report persistence, and session history behavior compatible.
- If the rewrite model response is empty, malformed, or fails, do not invent fake production data. Either surface a structured workflow error or deliberately fall back to the original query with an explicit event and tests that document the behavior.
- Add focused tests for count clamping, output parsing/deduplication, planner propagation, runner ordering, SSE event presence, and failure/degraded behavior.
- Run Java 17 `mvn test` successfully.
- Start the local backend and use `curl` against real HTTP APIs, real Docker-backed PostgreSQL/middleware, and a real configured model provider to verify the full chain. Confirm the stream exposes `rewrite_multi_query`, planner/search/report still complete, persistence still works, and the backend is stopped afterward.
- Commit the stage with a Chinese commit message.

## Scope

- Work only in `C:/MainData/code/Codex_project/M-agent`.
- Inspect `C:/MainData/code/Codex_project/deepresearch-main` as read-only guidance.
- Backend-only scope: model/state additions, query rewrite agent/service, runner/node integration, prompt input changes, SSE event payloads, tests, and curl verification.
- Do not edit the read-only reference project.
- Do not add frontend, RAG, MCP integration features, Redis, Elasticsearch, export/download/PDF, short-term memory, user-file RAG, or full Spring AI Alibaba Graph migration in this stage.
- Do not add a new database table unless a clear persistence requirement appears; optimized queries can live in in-memory state and stream/report context first.
- Preserve real provider paths and do not introduce mock production behavior.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/pom.xml`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/test/resources`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-query-rewrite-multi-query.md`

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
- Current commit before this handoff: `081a2d491aefbe271b476605d9f5af2556fa1529`, with a Chinese message meaning "Update stage verification requirements".
- No upstream Git branch is configured for `main`.
- Working tree before this handoff had only unrelated untracked `.claude/`; do not edit, delete, stage, or commit it.
- No implementation for `rewrite_multi_query` has started in M-agent.
- Existing `ResearchState` has `query`, `backgroundContext`, plan, observations, search contexts, site information, and report, but no optimized-query field.
- Existing `SimpleResearchRunner` currently calls `runPlanner(state)`, and `runPlanner` loads background context then executes `plannerNode` on `boundedElastic`.
- The next implementation should insert query rewriting before background context and planner execution.

## Completed

- Clarified that M-agent does not yet implement `rewrite_multi_query`.
- Identified `rewrite_multi_query` as the most suitable next small stage before full Graph migration.
- Confirmed that complete Graph migration should wait until query rewrite, background context, planner/execution semantics, status/resume/stop behavior, and validation discipline are stable.
- Updated project-level `AGENTS.md` in a prior commit to require real curl end-to-end validation for every stage, not only unit tests.
- Created this handoff to let a fresh session start the `rewrite_multi_query` stage safely.

## Decisions

- Use a lightweight M-agent-native implementation instead of importing Spring AI Alibaba Graph, RAG pre-retrieval classes, or reference project infrastructure wholesale.
- Keep `deepresearch-main` read-only and use it only as semantic guidance.
- The first useful integration point is planner prompt propagation and SSE observability; deeper downstream search-query selection can be added only if it stays small and testable.
- Use real model-provider calls in production code. Unit tests may use test doubles around local interfaces, but production wiring must not fall back to mock output.
- Treat curl E2E as mandatory because earlier curl testing found a runtime thread-blocking failure that `mvn test` alone missed.

## Evidence / References

- Project instructions: `C:/MainData/code/Codex_project/M-agent/AGENTS.md`.
- Upstream handoff: `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-background-investigation-session-context.md`.
- Current M-agent state and runner:
  - `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchState.java`
  - `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- Reference project files inspected:
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/RewriteAndMultiQueryNode.java`
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/dispatcher/RewriteAndMultiQueryDispatcher.java`
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/config/DeepResearchConfiguration.java`
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/util/StateUtil.java`
- Reference behavior:
  - `RewriteAndMultiQueryNode` rewrites the raw query, optionally compresses with short-term memory, expands with `MultiQueryExpander`, clamps `optimize_query_num` to `0..5`, stores `optimize_queries`, and routes to `background_investigator` unless user-file RAG is enabled.
  - M-agent should skip short-term memory and user-file RAG for this stage unless the user expands scope.

## Files Touched

- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-query-rewrite-multi-query.md`

## Commands Run

- `Get-Content -Raw C:\Users\20232\.codex\skills\task-handoff\SKILL.md`
- `git status --short --branch`
- `git rev-parse --show-toplevel`
- `git rev-parse HEAD`
- `git log --oneline -8`
- `Get-ChildItem -Force .codex\tasks | Select-Object Name,Length,LastWriteTime`
- `git branch --show-current`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` (failed: no upstream configured)
- `git merge-base HEAD '@{upstream}'` (failed: no upstream configured)
- `Get-Content -Raw AGENTS.md`
- `Get-Content -Raw .codex\tasks\add-background-investigation-session-context.md`
- `Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz"`
- `Get-Content -Raw C:\MainData\code\Codex_project\deepresearch-main\src\main\java\com\alibaba\cloud\ai\example\deepresearch\node\RewriteAndMultiQueryNode.java`
- `Get-Content -Raw C:\MainData\code\Codex_project\deepresearch-main\src\main\java\com\alibaba\cloud\ai\example\deepresearch\dispatcher\RewriteAndMultiQueryDispatcher.java`
- `Get-Content -Raw src\main\java\top\lanshan\manmu\model\ResearchState.java`
- `Get-Content -Raw src\main\java\top\lanshan\manmu\runner\SimpleResearchRunner.java`

## Verification

- Handoff creation only; no implementation tests were run for `rewrite_multi_query`.
- No backend curl verification was run for this new stage because implementation has not started.
- Prior project-level verification requirement now applies to the next implementation stage: `mvn test` plus real curl E2E before considering the stage complete.

## Known Failures / Blockers

- No upstream Git branch is configured.
- Unrelated untracked `.claude/` exists and must remain uncommitted.
- `rewrite_multi_query` does not exist in M-agent yet.
- The exact M-agent API shape for configuring optimized query count is not decided. Defaulting to `3` in code/config is acceptable if no request field exists.
- The fallback policy for model rewrite failure must be chosen deliberately and tested; do not silently invent fake optimized queries.

## Next Actions

- Inspect M-agent agent/model/node/test patterns, then add a small query rewrite agent/service and `ResearchState` optimized-query support that fits existing code style.
- Insert the rewrite stage before background context and planner execution, emit a `rewrite_multi_query` SSE event, and propagate optimized queries into planner/search prompt context.
- Add focused tests, run Java 17 `mvn test`, then start the backend and perform real curl E2E validation through `/chat/stream` or `/api/research/stream`; stop the backend and commit with a Chinese message.

## Open Questions

- Should optimized-query count be a fixed config property, a request field, or both?
- Should rewrite failure stop the workflow, or should the workflow continue with only the original query and an explicit degraded-status event?
- Should optimized queries be persisted in report/session metadata later, or remain stream/state-only for now?

## Avoid / Do Not Redo

- Do not edit `C:/MainData/code/Codex_project/deepresearch-main`.
- Do not introduce full Graph migration in this stage.
- Do not add RAG, MCP, Redis, Elasticsearch, frontend, user-file RAG, short-term memory, export/download/PDF, or Graph checkpointing unless the user explicitly changes scope.
- Do not reimplement report persistence, session history, background/session context, stop/cancellation, or human feedback from scratch.
- Do not introduce mock agent output, mock search, fake optimized queries, fake reports, or fake search context in production code.
- Do not commit `.local`, `target`, `.idea`, `.claude`, API keys, generated SSE/curl output, or logs.
- Do not leave manually started backend services running after tests.
- Do not treat `mvn test` alone as sufficient validation for the stage.

## Resume Prompt
Resume task add-query-rewrite-multi-query. Read .codex/tasks/add-query-rewrite-multi-query.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
