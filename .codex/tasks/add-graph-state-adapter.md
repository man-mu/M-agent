# Task Handoff: add-graph-state-adapter
Updated: 2026-05-22 20:30:13 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 27e75c3c3ed0ea8205457642ab9ea0cec995a755
Current Commit: 27e75c3c3ed0ea8205457642ab9ea0cec995a755

## Project Mainline

- This repository is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under package `top.lanshan.manmu`.
- The long-running project direction is to build a simplified but real production-path DeepResearch workflow: real LLM calls, real search provider calls, WebFlux SSE progress, PostgreSQL-backed persistence, pause/resume/stop lifecycle, and no runtime mock fallback.
- The project completed the pre-Graph serial foundations: explicit `coordinator`, `plan_validator`, `human_feedback`, and `research_team` node-shaped components.
- Stage 1 of the Graph migration completed at commit `1d8fd37`: controllers depend on `ResearchRunner`, `SimpleResearchRunner` remains the default, `mvp.research.runner=simple` exists, and `spring-ai-alibaba-graph-core` is on the classpath.
- Stage 2 is now complete at commit `27e75c3`: a small `top.lanshan.manmu.graph` state adapter exists for carrying `ResearchState`, accumulated SSE events, terminal status, resume decisions, and low-risk route reads.
- The agreed migration direction is still to introduce Spring AI Alibaba Graph as a dynamic orchestration layer around existing nodes without expanding into RAG, MCP, Redis, front-end work, professional KB, coder agents, or full parallel execution.
- The Graph migration plan is saved at `.codex/graph-dynamic-orchestration-plan.md` and remains the source of truth for the stage sequence.

## Stage Role in Mainline

- This was stage 2 of the Graph migration: `add-graph-state-adapter`.
- The stage created the state container API needed before adapting nodes or implementing `GraphResearchRunner`.
- Future Graph code can now store the existing `ResearchState` as the business state while accumulating `ResearchEvent` values and lifecycle hints in a Map-style graph state.
- The default runtime path is unchanged and still uses `SimpleResearchRunner`.

## Mainline Progression

- Earlier stages moved from embedded serial control flow to explicit node semantics, then added the `ResearchRunner` abstraction.
- This stage bridged the runner abstraction to future Graph execution by defining how graph state maps store and retrieve existing runtime state.
- The next stage should be `add-graph-node-adapter`, wrapping existing `ResearchNode` implementations as Graph actions using `ResearchGraphState`.

## Related Stage Handoffs

- `.codex/tasks/add-research-runner-interface.md`
- `.codex/tasks/pre-graph-core-nodes.md`
- `.codex/tasks/add-background-investigator-pre-planner-search.md`
- `.codex/tasks/add-human-feedback-plan-gate.md`
- `.codex/tasks/add-information-node-bocha-search.md`
- `.codex/tasks/add-research-team.md`
- `.codex/tasks/add-query-rewrite-multi-query.md`
- `.codex/tasks/add-processor-node-search-context.md`
- `.codex/tasks/add-chat-stop-session-lifecycle.md`
- `.codex/tasks/add-running-chat-stop-cancellation.md`
- `.codex/tasks/add-postgres-report-persistence.md`
- `.codex/tasks/add-session-history-persistence.md`

## Goal

- Implement the second Graph migration stage: add a Graph state adapter layer around existing `ResearchState` and `ResearchEvent` values.
- Do not implement `GraphResearchRunner`.
- Do not implement a Graph node adapter.
- Do not switch the default runner from `simple` to `graph`.

## Task Theme / User Intent

- The user wanted to resume stage 2 from the handoff and execute only the Graph state adapter layer.
- The user prefers small, independently verified stages with Chinese commits and real HTTP/SSE validation after each implementation stage.
- The completed stage prepares the codebase for the next Graph migration step without changing production behavior.

## Acceptance Criteria

- Add package `top.lanshan.manmu.graph`.
- Add state key constants with:
  - `research_state`
  - `events`
  - `terminal_status`
  - `resume_decision`
- Add Graph state helper/adapter class that can:
  - create a state map from `ResearchRequest`;
  - create a state map from `ResearchRequest` plus session id;
  - create a state map from an existing `ResearchState`;
  - read and write `ResearchState` from a Map-style Graph state;
  - append one or more `ResearchEvent` values;
  - read accumulated events in insertion order with defensive copies;
  - save, read, and clear `ResumeDecision`;
  - save, read, and clear terminal status;
  - expose low-risk route-reading helpers from existing `ResearchState` decisions.
- Keep `ResearchState` as the primary business state; Graph state is only the orchestration container.
- Add focused unit tests for the adapter behavior.
- Run focused tests and full `mvn test` with Java 17.
- Start the backend locally and run a real curl SSE smoke test through the unchanged simple path, then stop the backend and confirm the port is closed.
- Commit completed stage with Chinese message `添加图状态适配层`.

## Scope

- Implemented in `C:/MainData/code/Codex_project/M-agent`.
- Used no writes outside the repository's new graph package and graph tests.
- `C:/MainData/code/Codex_project/deepresearch-main` remained read-only and was not needed for this stage.
- Controller behavior, default runner configuration, and `SimpleResearchRunner` orchestration were not changed.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/graph`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/graph`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-graph-state-adapter.md`

### Read-only Reference Roots

- `C:/MainData/code/Codex_project/deepresearch-main`
- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/graph-dynamic-orchestration-plan.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model`
- Existing git history and prior commits.

### Forbidden Write Roots

- `C:/MainData/code/Codex_project/deepresearch-main`
- `C:/MainData/code/Codex_project/M-agent/.local`
- `C:/MainData/code/Codex_project/M-agent/target`
- `C:/MainData/code/Codex_project/M-agent/.idea`
- `C:/MainData/code/Codex_project/M-agent/.claude`
- Any file containing local API keys, provider credentials, or secrets.

## Current State

- Current branch is `main`.
- Current commit is `27e75c3c3ed0ea8205457642ab9ea0cec995a755` (`添加图状态适配层`).
- No upstream branch is configured for `main`; merge-base is unavailable.
- `git status --short` after commit shows only expected untracked `.claude/` and `.codex/` planning/handoff files.
- Backend service used for verification was stopped and port `18080` was confirmed closed.

## Completed

- Added `ResearchGraphStateKeys` with canonical state keys.
- Added `ResearchGraphState` utility:
  - creates graph state from `ResearchRequest`, `ResearchRequest` plus session id, or existing `ResearchState`;
  - stores `ResearchState` under `research_state`;
  - accumulates `ResearchEvent` values under `events`;
  - returns events as an immutable defensive copy;
  - stores and clears `ResumeDecision`;
  - stores and clears terminal status strings;
  - reads coordinator, plan validator, human feedback, and research team routes from the stored `ResearchState`.
- Added `ResearchGraphStateTest` with 9 focused tests.
- Ran focused tests, full tests, real backend SSE smoke validation, service shutdown, and commit.

## Decisions

- Use a plain `Map<String, Object>` as the Graph state boundary for now, avoiding early dependency on specific Graph execution API internals.
- Keep `ResearchState` as the business state instead of splitting its fields into graph keys at this stage.
- Use defensive event-list reads via `List.copyOf(...)`.
- Keep terminal status as a string until Graph runner lifecycle semantics are implemented.
- Add route helper methods now because they are thin reads from existing decisions and are likely useful for later Graph edges.
- Do not change runtime beans or runner selection in this stage.

## Evidence / References

- `.codex/graph-dynamic-orchestration-plan.md`: stage 2 goals and acceptance criteria.
- `.codex/tasks/add-research-runner-interface.md`: completed stage 1 handoff and verification details.
- `src/main/java/top/lanshan/manmu/model/ResearchState.java`: business state stored inside graph state.
- `src/main/java/top/lanshan/manmu/model/ResearchRequest.java`: request source for creating `ResearchState`.
- `src/main/java/top/lanshan/manmu/model/ResearchEvent.java`: SSE event type accumulated by graph state.
- `src/main/java/top/lanshan/manmu/runner/ResumeDecision.java`: resume decision stored by graph state.
- `src/main/java/top/lanshan/manmu/graph/ResearchGraphState.java`: new adapter.
- `src/main/java/top/lanshan/manmu/graph/ResearchGraphStateKeys.java`: new state key constants.
- `src/test/java/top/lanshan/manmu/graph/ResearchGraphStateTest.java`: focused unit tests.
- `AGENTS.md`: Java 17, Maven, real E2E, Docker/PostgreSQL, no-secret, and Chinese commit constraints.

## Files Touched

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/graph/ResearchGraphState.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/graph/ResearchGraphStateKeys.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/graph/ResearchGraphStateTest.java`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-graph-state-adapter.md`

## Commands Run

- `Get-Content -Raw -LiteralPath 'C:\Users\20232\.codex\skills\task-handoff\SKILL.md'`
- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'`
- `Get-Content -Raw -LiteralPath '.codex\tasks\add-graph-state-adapter.md'`
- `Get-Content -Raw -LiteralPath 'AGENTS.md'`
- `Get-Content -Raw -LiteralPath '.codex\graph-dynamic-orchestration-plan.md'`
- `Get-Content -Raw -LiteralPath 'src\main\java\top\lanshan\manmu\model\ResearchState.java'`
- `Get-Content -Raw -LiteralPath 'src\main\java\top\lanshan\manmu\model\ResearchRequest.java'`
- `Get-Content -Raw -LiteralPath 'src\main\java\top\lanshan\manmu\model\ResearchEvent.java'`
- `Get-Content -Raw -LiteralPath 'src\main\java\top\lanshan\manmu\runner\ResumeDecision.java'`
- `Get-Content -Raw -LiteralPath 'src\main\java\top\lanshan\manmu\runner\ResearchRunner.java'`
- `Get-Content -Raw -LiteralPath 'src\main\java\top\lanshan\manmu\runner\SimpleResearchRunner.java'`
- `rg --files 'src\test\java\top\lanshan\manmu' | rg '(runner|model|node|graph|ResearchState|ResearchEvent|Resume)'`
- `New-Item -ItemType Directory -Force -Path 'src\main\java\top\lanshan\manmu\graph','src\test\java\top\lanshan\manmu\graph'`
- `mvn -Dtest=ResearchGraphStateTest test` with `JAVA_HOME=C:\WorkResources\JDKs\JDK17`
- `mvn test` with `JAVA_HOME=C:\WorkResources\JDKs\JDK17`
- `Get-NetTCPConnection -LocalPort 18080`
- `docker_container_list(all=true)`
- `mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18080`
- `curl.exe -sS -X POST http://localhost:18080/api/model/switch`
- `curl.exe -N --max-time 180 -sS -X POST http://localhost:18080/chat/stream`
- `curl.exe -sS http://localhost:18080/api/reports/codex-graph-state-smoke-1/exists`
- `curl.exe -sS http://localhost:18080/api/sessions/codex-graph-state-smoke/threads/codex-graph-state-smoke-1`
- `curl.exe -sS http://localhost:18080/api/reports/session/codex-graph-state-smoke`
- `Stop-Process` for the service PID listening on `18080`
- `git add -- src/main/java/top/lanshan/manmu/graph src/test/java/top/lanshan/manmu/graph`
- `git commit -m "添加图状态适配层"`

## Verification

- Focused tests passed:
  - `mvn -Dtest=ResearchGraphStateTest test`
  - Result: 9 tests, 0 failures, 0 errors.
- Full tests passed:
  - `mvn test`
  - Result: 98 tests, 0 failures, 0 errors.
- Real backend smoke passed:
  - Docker container `manmu-postgres` was running and healthy on host port `5432`.
  - Backend started on port `18080` with Java 17 and connected to PostgreSQL 17.
  - Model switched to `deepseek/deepseek-chat`; no API key was printed.
  - `/chat/stream` request used `enable_deepresearch=false`, `auto_accepted_plan=true`, session `codex-graph-state-smoke`, and thread `codex-graph-state-smoke-1`.
  - SSE output included `coordinator` and `event:done`.
  - Report existence check returned true.
  - Session history for the smoke thread returned `COMPLETED`.
  - Report session lookup returned the completed report text.
  - Service was stopped and port `18080` was confirmed closed.

## Known Failures / Blockers

- No upstream branch is configured for `main`; merge-base is unavailable.
- `.claude/` is untracked and must be ignored unless the user explicitly asks otherwise.
- `.codex/graph-dynamic-orchestration-plan.md`, `.codex/tasks/pre-graph-core-nodes.md`, `.codex/tasks/add-research-runner-interface.md`, and this handoff file are untracked. Do not treat that as a scope violation.
- Some older files and handoffs contain mojibake display text from earlier encoding issues; this stage did not address that.
- Exact Spring AI Alibaba Graph execution API shape still needs to be checked from local dependency sources or official docs before later graph execution stages.

## Next Actions

1. Start stage 3 `add-graph-node-adapter`: inspect local `spring-ai-alibaba-graph-core` APIs and existing `ResearchNode` contract, then design the smallest action adapter around `ResearchGraphState`.
2. Implement a graph node/action adapter that reads `ResearchState`, invokes an existing `ResearchNode`, appends emitted `ResearchEvent` values, and returns the updated graph state without changing default runtime behavior.
3. Add focused tests, run Java 17 `mvn test`, perform real HTTP/SSE smoke validation, stop the backend, and commit the stage with a Chinese message.

## Open Questions

- Which exact Spring AI Alibaba Graph action interface should stage 3 target in version `1.0.0.4`?
- Should stage 3 depend directly on Graph classes, or keep a small adapter independent until `GraphResearchRunner` construction begins?
- Whether `.codex` planning and handoff files should ever be committed remains a user choice; current stage code commits did not include them.

## Avoid / Do Not Redo

- Do not redo completed stage 1 runner abstraction work.
- Do not redo completed stage 2 graph state adapter work.
- Do not modify `.local`, `.claude`, `target`, `.idea`, or secrets.
- Do not implement `GraphResearchRunner`, graph edges, or runner switching when only doing stage 3.
- Do not change `mvp.research.runner` default away from `simple` until the planned default-switch stage.
- Do not alter `SimpleResearchRunner` behavior unless a compile issue absolutely requires a tiny compatibility change.
- Do not introduce RAG, MCP, Redis, front-end work, professional KB, coder agents, full parallel executor, or runtime mocks.
- Do not skip the real backend curl E2E smoke test after implementation stages.

## Resume Prompt
Resume task add-graph-state-adapter. Read .codex/tasks/add-graph-state-adapter.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
