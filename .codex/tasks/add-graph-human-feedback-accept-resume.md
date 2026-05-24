# Task Handoff: add-graph-human-feedback-accept-resume
Updated: 2026-05-23 09:21:07 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: c26c8a877f8b2fad1a0b07fdcc4110d9506a1c23
Current Commit: 51fc282c95f0061208a5e899c1d54aadf0ebff78

## Project Mainline

- This repository is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under package `top.lanshan.manmu`.
- The long-running project direction is a simplified but real production-path DeepResearch workflow: real LLM calls, real search provider calls, WebFlux SSE progress, PostgreSQL-backed persistence, pause/resume/stop lifecycle, and no runtime mock fallback.
- The project completed the pre-Graph serial foundations: explicit `coordinator`, `plan_validator`, `human_feedback`, and `research_team` node-shaped components.
- Stage 1 completed at commit `1d8fd37`: controllers depend on `ResearchRunner`, `SimpleResearchRunner` remains the default, `mvp.research.runner=simple` exists, and `spring-ai-alibaba-graph-core` is on the classpath.
- Stage 2 completed at commit `27e75c3`: `ResearchGraphState` and `ResearchGraphStateKeys` can carry the existing `ResearchState`, accumulated SSE `ResearchEvent` values, terminal status, resume decision, and low-risk route reads.
- Stage 3 completed at commit `c7e8f04`: existing `ResearchNode` implementations can be wrapped as Spring AI Alibaba Graph `NodeAction` / `AsyncNodeAction` values through `ResearchNodeGraphAction`.
- Stage 4 completed at commit `c26c8a8`: the first minimal `GraphResearchRunner` auto-complete path can be enabled explicitly while the default `simple` runtime path remains unchanged.
- Stage 5 completed at commit `51fc282`: explicit Graph runner manual plan-gate pause and accepted resume now work through real Graph topology, PostgreSQL-backed session history, report persistence, and real HTTP/SSE verification.
- A route check against `C:/MainData/code/Codex_project/deepresearch-main` found no direction-level drift: M-agent is following the main project's Graph concepts, but deliberately in a simplified, testable, reversible form.
- The agreed migration direction is still to introduce Spring AI Alibaba Graph as a dynamic orchestration layer around existing nodes without expanding into RAG, MCP, Redis, front-end work, professional KB, coder agents, or full parallel execution.
- The Graph migration plan is saved at `.codex/graph-dynamic-orchestration-plan.md` and remains the source of truth for the stage sequence.

## Stage Role in Mainline

- This is stage 5 of the Graph migration: `add-graph-human-feedback-accept-resume`.
- This stage made the explicit Graph runner support manual plan gate pause and accepted resume, closing the first lifecycle gap between `GraphResearchRunner` and `SimpleResearchRunner`.
- It should keep the Graph runner explicitly enabled by `mvp.research.runner=graph`; it must not switch the default runner away from `simple`.
- It should not implement rejected feedback replan, max-iteration fallback, running stop, paused stop, Graph default switching, or removal of `SimpleResearchRunner`; those remain later stages.

## Mainline Progression

- Stage 1 made runner selection possible.
- Stage 2 created the state container that Graph execution can use while preserving `ResearchState` as the business state.
- Stage 3 added a reusable adapter from `ResearchNode` to Graph node actions.
- Stage 4 connected those pieces into a minimal `GraphResearchRunner` auto-complete path with real HTTP/SSE verification.
- Stage 5 added the first human-in-the-loop lifecycle behavior for the Graph runner: `runUntilPlanGate(...)` pauses at `human_feedback`, and `resume(... accepted=true ...)` continues through research execution and completion.
- Future stage 6 should add rejected feedback, replan, max-iteration continuation, and stop semantics.

## Related Stage Handoffs

- `.codex/tasks/add-graph-auto-research-runner.md`
- `.codex/tasks/add-graph-node-adapter.md`
- `.codex/tasks/add-graph-state-adapter.md`
- `.codex/tasks/add-research-runner-interface.md`
- `.codex/tasks/pre-graph-core-nodes.md`
- `.codex/tasks/add-human-feedback-plan-gate.md`
- `.codex/tasks/add-chat-stop-session-lifecycle.md`
- `.codex/tasks/add-running-chat-stop-cancellation.md`

## Goal

- Implement Graph human pause and accepted resume for the explicit Graph runner.
- Support `GraphResearchRunner.runUntilPlanGate(...)` for `auto_accepted_plan=false`: run up to the plan gate, persist the paused state, mark session history `PAUSED`, and emit the existing `human_feedback` waiting event.
- Support `GraphResearchRunner.resume(threadId, decision)` when `decision.accepted()` is true: restore the paused state, run the human feedback route, continue to research execution, save the completed report, mark session history `COMPLETED`, and emit final `done`.
- Preserve current auto-complete Graph behavior from stage 4.
- Keep `stopAndRecord(...)` conservative for this stage unless a tiny compatibility adjustment is unavoidable.

## Task Theme / User Intent

- The user will open a new session to implement the next Graph migration stage, `add-graph-human-feedback-accept-resume`.
- The user wants continuity from the completed first four stages and the route check against `deepresearch-main`.
- The user prefers small, independently verified stages with Chinese commits and real HTTP/SSE validation through real PostgreSQL and real model-provider paths.

## Acceptance Criteria

- `mvp.research.runner=graph` supports `/chat/stream` with `auto_accepted_plan=false` for non-direct research requests:
  - emits normal progress events up to planning and validation;
  - pauses at `human_feedback`;
  - returns a `human_feedback` waiting event;
  - marks session history `PAUSED`;
  - does not save a report while paused.
- `GraphResearchRunner.resume(...)` supports accepted feedback:
  - finds the paused graph/research state by thread id;
  - applies `ResumeDecision.accepted=true`;
  - emits the human feedback accepted event;
  - continues through `information`, `research_team`, `researcher`/`processor` loop, `reporter`;
  - saves the completed report;
  - marks session history `COMPLETED`;
  - emits a final `done` event.
- If resume is called for an unknown thread, emit a `human_feedback` error event similar to `SimpleResearchRunner`.
- Focused tests should cover:
  - manual plan gate pauses at `human_feedback`;
  - paused state is retained and report is not saved while paused;
  - accepted resume completes and saves report;
  - missing paused state returns a human feedback error event;
  - existing auto-complete direct answer and auto research tests still pass.
- Run focused tests and full `mvn test` with Java 17.
- Start the backend locally with explicit graph runner configuration and run real curl SSE:
  - `/chat/stream` pauses to `human_feedback` with `PAUSED` history and no report;
  - `/chat/resume` with `feedback=true` completes with `COMPLETED` history and report exists.
- Stop the backend after verification and confirm port `18080` is closed.
- Commit the completed stage with a Chinese message meaning "support graph orchestration human accepted resume".

## Scope

- Implement in `C:/MainData/code/Codex_project/M-agent`.
- Expected write roots are the graph and runner packages plus focused graph/runner tests and this handoff file.
- Controllers should not need changes because they already depend on `ResearchRunner` and already expose `/chat/stream` and `/chat/resume`.
- Existing node implementations should be reused through `ResearchNodeGraphAction`; avoid duplicating business logic.
- `SimpleResearchRunner` should remain the behavior reference. Avoid changing it unless a tiny test compatibility adjustment is truly required.
- `application.yml` must keep `mvp.research.runner: simple`.
- Use `C:/MainData/code/Codex_project/deepresearch-main` only as read-only semantic/reference material.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/graph`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/graph`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/runner`
- `C:/MainData/code/Codex_project/M-agent/src/test/resources`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-graph-human-feedback-accept-resume.md`

### Read-only Reference Roots

- `C:/MainData/code/Codex_project/deepresearch-main`
- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/graph-dynamic-orchestration-plan.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/api`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/report`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/sessioncontext`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/sessionhistory`
- `C:/Users/20232/.m2/repository/com/alibaba/cloud/ai/spring-ai-alibaba-graph-core/1.0.0.4`
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
- Current commit is `51fc282c95f0061208a5e899c1d54aadf0ebff78`, whose message means "support graph orchestration human accepted resume".
- No upstream branch is configured for `main`; merge-base is unavailable.
- There are no tracked working-tree diffs after the stage commit.
- `git status --short` shows only expected untracked `.claude/`, `.codex/graph-dynamic-orchestration-plan.md`, previous stage handoff files, and this handoff file.
- Backend service was stopped after verification; port `18080` was confirmed closed.

## Completed

- Stages 1 through 4 are completed and committed:
  - `1d8fd37`: runner abstraction and Graph dependency scaffolding.
  - `27e75c3`: graph state adapter.
  - `c7e8f04`: research node graph adapter.
  - `c26c8a8`: explicit Graph auto-complete runner path.
- Stage 4 validation passed focused tests, full `mvn test`, and real HTTP/SSE smoke paths under explicit graph runner configuration.
- A route comparison against `deepresearch-main` was performed on 2026-05-23:
  - no direction-level drift was found;
  - M-agent intentionally uses `ResearchRunner` abstraction and encapsulated `ResearchState` before moving to checkpointed HITL;
  - the next risk to address is human pause/resume state management and checkpoint/serialization semantics.
- Stage 5 implementation completed and committed:
  - `ResearchGraphBuilder` now builds a plan-gate graph that can route valid non-auto-accepted plans to `human_feedback`, plus an accepted-resume graph that starts at `human_feedback` and continues to research execution.
  - `GraphResearchRunner` now keeps in-memory paused graph state by thread id, marks `PAUSED`, restores accepted feedback, marks `RUNNING`, continues to report generation, saves the report, and marks `COMPLETED`.
  - Missing paused state returns a `human_feedback` error event; rejected feedback remains unsupported in this stage and preserves paused state for a later accepted resume attempt.
  - Event emission now deduplicates by emitted event object instead of assuming every Graph output carries a monotonically growing event list; this fixed a real Graph snapshot behavior seen during HTTP/SSE validation.

## Decisions

- Keep `ResearchState` as the business state inside graph state during stage 5; do not split all state fields into flat graph keys yet.
- Reuse `ResearchNodeGraphAction` for existing nodes.
- Stage 5 may introduce an in-memory paused state store inside `GraphResearchRunner` if that is the smallest safe step, matching `SimpleResearchRunner` behavior. Do not introduce Redis or durable checkpoint storage in this stage.
- For main-project alignment, prefer the semantic pattern from `deepresearch-main`: pause before or at `human_feedback`, resume with a feedback object, then continue from `human_feedback` toward `research_team`.
- Do not implement rejected feedback replan or stop in stage 5; keep those for stage 6.
- Do not change the default runner from `simple`.
- Stage 5 uses a simple in-memory paused `Map<String, Map<String,Object>>` inside `GraphResearchRunner` rather than Spring AI Alibaba checkpoint APIs. This is deliberate for the first accepted-resume milestone.
- Rejected feedback is deliberately not implemented in stage 5; it returns an unsupported `human_feedback` error while keeping the paused state available.

## Evidence / References

- `.codex/graph-dynamic-orchestration-plan.md`: stage 5 goals and acceptance criteria.
- `.codex/tasks/add-graph-auto-research-runner.md`: completed stage 4 handoff and verification details.
- `src/main/java/top/lanshan/manmu/runner/ResearchRunner.java`: runner contract, including `runUntilPlanGate`, `resume`, and `stopAndRecord`.
- `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`: behavior reference for pause/resume, `pausedStates`, `markPaused`, `markRunning`, save completed report, and missing paused-state errors.
- `src/main/java/top/lanshan/manmu/runner/GraphResearchRunner.java`: current explicit graph runner; `runUntilPlanGate` and `resume` currently return unsupported error events, and `stopAndRecord` returns false.
- `src/main/java/top/lanshan/manmu/graph/ResearchGraphBuilder.java`: current auto-complete graph topology; it currently routes `PlanValidatorRoute.HUMAN_FEEDBACK` to an unsupported error.
- `src/main/java/top/lanshan/manmu/graph/ResearchGraphState.java`: graph state helper with `resumeDecision(...)`, `humanFeedbackRoute(...)`, events, and research state access.
- `src/main/java/top/lanshan/manmu/graph/ResearchGraphStateSerializer.java`: current shallow-copy runtime serializer; adequate for stage 4 clone safety but not a full durable JSON checkpoint serializer.
- `src/main/java/top/lanshan/manmu/node/HumanFeedbackNode.java`: existing node semantics for waiting, accepted resume, rejected replan, and max-iteration fallback.
- `src/main/java/top/lanshan/manmu/node/PlanValidatorNode.java`: routes valid plans to `RESEARCH_TEAM` when auto-accepted, otherwise `HUMAN_FEEDBACK`.
- `src/test/java/top/lanshan/manmu/runner/GraphResearchRunnerTest.java`: stage 4 focused test style and stubs.
- `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/config/DeepResearchConfiguration.java`: main-project Graph topology and conditional edges.
- `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/controller/ChatController.java`: main-project `MemorySaver`, `interruptBefore("human_feedback")`, `getState`, and `fluxStreamFromInitialNode` reference.
- `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/controller/graph/GraphProcess.java`: main-project human feedback resume and stop task tracking reference.
- `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/HumanFeedbackNode.java`: main-project accepted/rejected feedback route semantics.
- `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/serializer/DeepResearchStateSerializer.java`: main-project durable JSON state serializer reference; do not copy wholesale unless necessary.

## Files Touched

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/graph/ResearchGraphBuilder.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/GraphResearchRunner.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/runner/GraphResearchRunnerTest.java`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-graph-human-feedback-accept-resume.md`

## Commands Run

- `Get-Content -LiteralPath 'C:\Users\20232\.codex\skills\task-handoff\SKILL.md' -Raw`
- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'`
- `git merge-base HEAD '@{upstream}'`
- `Get-Content -Path .codex/tasks/add-graph-auto-research-runner.md -Raw`
- `Get-Content -Path .codex/tasks/add-graph-node-adapter.md -Raw`
- `Get-Content -Path .codex/tasks/add-graph-state-adapter.md -Raw`
- `Get-Content -Path .codex/tasks/add-research-runner-interface.md -Raw`
- `Get-Content -Path src/main/java/top/lanshan/manmu/runner/GraphResearchRunner.java -Raw`
- `Get-Content -Path src/main/java/top/lanshan/manmu/graph/ResearchGraphBuilder.java -Raw`
- `Get-Content -Path src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java -Raw`
- Route comparison commands against `C:/MainData/code/Codex_project/deepresearch-main`:
  - `rg --files`
  - `rg "StateGraph|CompiledGraph|Graph|HumanFeedback|Plan|ResearchTeam|Coordinator|..." -n`
  - `Get-Content` for `DeepResearchConfiguration.java`, `ChatController.java`, `GraphProcess.java`, dispatcher classes, key node classes, `DeepResearchStateSerializer.java`, and `StateUtil.java`
- `Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz'`
- `mvn -Dtest=GraphResearchRunnerTest test`
- `mvn "-Dtest=ResearchGraphStateTest,ResearchNodeGraphActionTest,GraphResearchRunnerTest" test`
- `mvn test`
- `mvn -DskipTests package`
- `java -jar target/deepresearch-mvp-0.1.0-SNAPSHOT.jar --server.port=18080 --mvp.research.runner=graph`
- `curl.exe -sS -N -X POST http://localhost:18080/chat/stream ...`
- `curl.exe -sS http://localhost:18080/api/sessions/<sessionId>/threads/<threadId>`
- `curl.exe -sS http://localhost:18080/api/reports/<threadId>/exists`
- `curl.exe -sS -X POST http://localhost:18080/api/model/switch ...` to switch runtime model from DashScope to DeepSeek after DashScope returned provider rate-limit errors during validation.
- `curl.exe -sS -N -X POST http://localhost:18080/chat/resume ...`
- `Stop-Process -Id <serverPid>`
- `Get-NetTCPConnection -LocalPort 18080`
- `git add -- src/main/java/top/lanshan/manmu/graph/ResearchGraphBuilder.java src/main/java/top/lanshan/manmu/runner/GraphResearchRunner.java src/test/java/top/lanshan/manmu/runner/GraphResearchRunnerTest.java`
- `git commit -m "支持图编排人工接受恢复"`

## Verification

- `mvn -Dtest=GraphResearchRunnerTest test` passed with 8 tests.
- `mvn "-Dtest=ResearchGraphStateTest,ResearchNodeGraphActionTest,GraphResearchRunnerTest" test` passed with 23 tests.
- `mvn test` passed with 112 tests.
- Real backend was packaged and started with Java 17:
  - `java -jar target/deepresearch-mvp-0.1.0-SNAPSHOT.jar --server.port=18080 --mvp.research.runner=graph`
  - Docker PostgreSQL `manmu-postgres` was running and healthy.
- Real HTTP/SSE validation passed against `http://localhost:18080`:
  - `/chat/stream` with `auto_accepted_plan=false` emitted `coordinator`, `rewrite_multi_query`, `background_investigator`, `planner`, `plan_validator`, and `human_feedback`.
  - Session `codex-stage5-ds-20260523091604` / thread `codex-stage5-ds-20260523091604-thread` was marked `PAUSED` after the plan gate.
  - `/api/reports/<threadId>/exists` returned `false` while paused.
  - `/chat/resume` with `feedback=true` emitted `human_feedback`, `information`, `research_team`, `processor`, `reporter`, and final `event:done`.
  - The same thread was marked `COMPLETED`, `report_thread_id` was set, and report existence returned `true`.
- During an earlier real run, DashScope returned `429 Throttling.RateQuota`; runtime model was switched to configured DeepSeek through `/api/model/switch` without reading or modifying `.local/model-providers.json`.
- Backend verification service was stopped and port `18080` was confirmed closed.

## Known Failures / Blockers

- No upstream branch is configured for `main`; merge-base is unavailable.
- `.claude/` is untracked and must be ignored unless the user explicitly asks otherwise.
- `.codex/graph-dynamic-orchestration-plan.md`, prior `.codex/tasks/*.md`, and this new handoff file are untracked. Do not treat that as a scope violation.
- `deepresearch-main` is not a git repository copy in this workspace; use it as read-only source/reference files only.
- `ResearchGraphStateSerializer` is currently a shallow-copy runtime serializer, not a full durable checkpoint serializer. If stage 5 uses Spring AI Alibaba `MemorySaver` checkpoint APIs, verify serializer behavior carefully before relying on it.
- The local `spring-ai-alibaba-graph-core` sources jar is not available; use `javap`, official docs, or dependency source download if needed.
- Some older source comments display mojibake due to encoding display; do not clean unrelated comments in this stage.
- Real HTTP/SSE validation depends on live model-provider quotas. DashScope hit a rate limit during this stage, but DeepSeek completed the required E2E path.

## Next Actions

1. Start the next stage from `.codex/graph-dynamic-orchestration-plan.md` stage 6: implement Graph rejected feedback replan, max-iteration continuation, running stop, and paused stop.
2. Before editing stage 6, inspect `GraphResearchRunner`, `ResearchGraphBuilder`, `SimpleResearchRunner`, `HumanFeedbackNode`, and the new Graph tests from this stage to preserve accepted-resume behavior.
3. Keep using real HTTP/SSE E2E with explicit `mvp.research.runner=graph`, but account for model-provider quota by checking configured providers and switching runtime model through `/api/model/switch` if needed.

## Open Questions

- Stage 6 decision needed: keep extending the current explicit in-memory paused-state approach, or move to Spring AI Alibaba checkpoint APIs before adding rejected feedback and stop semantics?
- Stage 6 should decide whether to upgrade `ResearchGraphStateSerializer` toward durable JSON serialization or keep the current shallow runtime serializer until durable checkpointing is required.

## Avoid / Do Not Redo

- Do not redo completed stages 1 through 5.
- Do not modify `.local`, `.claude`, `target`, `.idea`, or secrets.
- Do not change `mvp.research.runner` default away from `simple`.
- Do not implement rejected feedback replan, max-iteration fallback, running stop, paused stop, graph default switching, or simple runner removal in this stage.
- Do not introduce RAG, MCP, Redis, front-end work, professional KB, coder agents, full parallel executor, or runtime mocks.
- Do not alter existing `ResearchNode` implementations or `SimpleResearchRunner` behavior unless a compile issue absolutely requires a tiny compatibility change.
- Do not skip the real backend curl E2E pause/resume smoke test after implementation.

## Resume Prompt
Resume task add-graph-human-feedback-accept-resume. Read .codex/tasks/add-graph-human-feedback-accept-resume.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
