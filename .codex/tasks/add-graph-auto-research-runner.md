# Task Handoff: add-graph-auto-research-runner
Updated: 2026-05-22 23:06:55 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: c7e8f0432e61e0cce0bb8cae6ce3947da8dd7999
Current Commit: c26c8a877f8b2fad1a0b07fdcc4110d9506a1c23

## Project Mainline

- This repository is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under package `top.lanshan.manmu`.
- The long-running project direction is to build a simplified but real production-path DeepResearch workflow: real LLM calls, real search provider calls, WebFlux SSE progress, PostgreSQL-backed persistence, pause/resume/stop lifecycle, and no runtime mock fallback.
- The project completed the pre-Graph serial foundations: explicit `coordinator`, `plan_validator`, `human_feedback`, and `research_team` node-shaped components.
- Stage 1 of the Graph migration completed at commit `1d8fd37`: controllers depend on `ResearchRunner`, `SimpleResearchRunner` remains the default, `mvp.research.runner=simple` exists, and `spring-ai-alibaba-graph-core` is on the classpath.
- Stage 2 completed at commit `27e75c3`: `ResearchGraphState` and `ResearchGraphStateKeys` can carry the existing `ResearchState`, accumulated SSE `ResearchEvent` values, terminal status, resume decision, and low-risk route reads.
- Stage 3 completed at commit `c7e8f04`: existing `ResearchNode` implementations can be wrapped as Spring AI Alibaba Graph `NodeAction` / `AsyncNodeAction` values through `ResearchNodeGraphAction`.
- Stage 4 completed at commit `c26c8a8`: the first minimal `GraphResearchRunner` auto-complete path can be enabled explicitly while the default `simple` runtime path remains unchanged.
- The agreed migration direction is to introduce Spring AI Alibaba Graph as a dynamic orchestration layer around existing nodes without expanding into RAG, MCP, Redis, front-end work, professional KB, coder agents, or full parallel execution.
- The Graph migration plan is saved at `.codex/graph-dynamic-orchestration-plan.md` and remains the source of truth for the stage sequence.

## Stage Role in Mainline

- This was stage 4 of the Graph migration: `add-graph-auto-research-runner`.
- The stage built the first runnable Graph-based research runner for auto-complete workflows, using `ResearchGraphState` and `ResearchNodeGraphAction`.
- It proved Graph can execute the existing node sequence and dynamic routes for direct answer and auto-accepted research completion, while report persistence and session history still behave like the simple runner.
- This stage did not switch the default runner; `mvp.research.runner=simple` remains the default after implementation.

## Mainline Progression

- Stage 1 made runner selection possible.
- Stage 2 created the state container that Graph execution can use while preserving `ResearchState` as the business state.
- Stage 3 added a reusable adapter from `ResearchNode` to Graph node actions.
- Stage 4 connected those pieces into a minimal `GraphResearchRunner` that can be enabled with explicit configuration and verified through real HTTP/SSE.
- Future stages should build on this by adding human pause/resume, feedback replan, running/paused stop support, graph default switching, and finally removal of `SimpleResearchRunner`.

## Related Stage Handoffs

- `.codex/tasks/add-graph-node-adapter.md`
- `.codex/tasks/add-graph-state-adapter.md`
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

- Implement the fourth Graph migration stage: a minimal `GraphResearchRunner` auto-complete path.
- Support `run(...)` and `runChat(...)` for direct answer and auto-accepted research completion.
- Keep `runUntilPlanGate(...)`, `resume(...)`, and `stopAndRecord(...)` conservative for now; do not implement Graph human pause/resume or stop semantics in this stage.
- Do not switch the default runner from `simple` to `graph`.

## Task Theme / User Intent

- The user will continue in a new session and wants a resume-ready handoff for `add-graph-auto-research-runner`.
- The intent is to advance the Spring AI Alibaba Graph migration one stage by making the Graph path runnable under explicit configuration.
- The user prefers small, independently verified stages with Chinese commits and real HTTP/SSE validation through real database/middleware/model-provider paths.

## Acceptance Criteria

- Add `GraphResearchRunner`, likely under `top.lanshan.manmu.runner`, and make it conditional on `mvp.research.runner=graph`.
- Add a graph construction helper/configuration, likely under `top.lanshan.manmu.graph`, if that keeps runner code smaller.
- Use existing `ResearchNode` implementations and `ResearchNodeGraphAction`; do not duplicate business node logic.
- Preserve default configuration as `mvp.research.runner=simple`.
- Support at least these auto-complete routes:
  - `coordinator -> __END__` for direct answer;
  - `coordinator -> rewrite_multi_query` for deep research;
  - `rewrite_multi_query -> background_investigator -> planner -> plan_validator`;
  - `plan_validator -> planner` for invalid plan retries while retries remain;
  - `plan_validator -> information` for valid auto-accepted plans;
  - `information -> research_team`;
  - `research_team -> researcher | processor | reporter`;
  - `researcher -> research_team`;
  - `processor -> research_team`;
  - `reporter -> __END__`.
- Preserve current persistence behavior:
  - start session history before running;
  - save completed report at the end;
  - mark session history `COMPLETED`;
  - on failure, mark session history `FAILED` and emit a runner error event.
- Focused tests should cover:
  - direct answer path;
  - auto-accepted research completion path;
  - invalid plan retry path;
  - research team loop through researcher/processor/reporter;
  - explicit graph runner configuration creates the graph runner while default simple configuration remains unchanged.
- Run focused tests and full `mvn test` with Java 17.
- Start the backend locally with explicit graph runner configuration and run a real curl SSE smoke test through `/chat/stream` or `/api/research/stream`; verify SSE node sequence, report existence, and session history `COMPLETED`.
- Stop the backend after verification and confirm port closure.
- Commit the completed stage with the Chinese message meaning "add graph orchestration auto research path".

## Scope

- Implement in `C:/MainData/code/Codex_project/M-agent`.
- Expected write roots are the graph and runner packages plus focused tests and this handoff file.
- `SimpleResearchRunner` may be read heavily as the reference behavior; avoid changing it unless a tiny compile/test compatibility adjustment is truly necessary.
- Controllers should not need changes because they already depend on `ResearchRunner`.
- `application.yml` should keep `mvp.research.runner: simple`; if tests need graph configuration, use test properties or explicit command-line configuration.
- Use `C:/MainData/code/Codex_project/deepresearch-main` only as read-only semantic/reference material if needed.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/graph`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/graph`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/runner`
- `C:/MainData/code/Codex_project/M-agent/src/test/resources`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-graph-auto-research-runner.md`

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
- Current commit is `c7e8f0432e61e0cce0bb8cae6ce3947da8dd7999`, whose message means "add research node graph adapter".
- No upstream branch is configured for `main`; merge-base is unavailable.
- There are no tracked working-tree diffs at the time this handoff is created.
- `git status --short` shows only expected untracked `.claude/`, `.codex/graph-dynamic-orchestration-plan.md`, `.codex/tasks/add-graph-node-adapter.md`, `.codex/tasks/add-graph-state-adapter.md`, `.codex/tasks/add-research-runner-interface.md`, `.codex/tasks/pre-graph-core-nodes.md`, and this new handoff file.
- Backend service is not running on port `18080` at the time of the prior stage completion.

## Completed

- Stage 1 completed and committed:
  - added `ResearchRunner`;
  - made controllers depend on the abstraction;
  - made `SimpleResearchRunner` the conditional default;
  - added `mvp.research.runner=simple`;
  - added `spring-ai-alibaba-graph-core`.
- Stage 2 completed and committed:
  - added `ResearchGraphStateKeys`;
  - added `ResearchGraphState`;
  - added focused graph state tests;
  - verified unit/full tests and real HTTP/SSE smoke path.
- Stage 3 completed and committed:
  - added `ResearchNodeGraphAction`;
  - added focused adapter tests;
  - verified unit/full tests and real HTTP/SSE smoke path;
  - committed as `c7e8f04`.
- Stage 4 completed and committed as `c26c8a8`:
  - added `ResearchGraphBuilder` with the auto-complete Graph topology and dynamic route edges;
  - added `ResearchGraphStateSerializer` to shallow-copy graph runtime state safely while preserving mutable `ResearchState` and `ResearchEvent` values;
  - added `GraphResearchRunner`, conditional on `mvp.research.runner=graph`;
  - added a `ResearchGraphBuilder` bean in runner configuration;
  - made `ResearchGraphState.events(...)` read-only so graph output maps are not mutated while events are emitted;
  - added focused `GraphResearchRunnerTest` coverage for direct answer, auto-accepted completion, invalid plan retry, research team loop, and default/simple versus explicit/graph runner selection;
  - verified focused tests, full Maven tests, and real HTTP/SSE smoke paths with Postgres and the configured real model provider.

## Decisions

- Stage 4 should build a minimal Graph runner for auto-complete only.
- Default runtime must remain simple; graph runner must require explicit `mvp.research.runner=graph`.
- Prefer using real Spring AI Alibaba Graph APIs if the implementation is clean.
- Use `ResearchNodeGraphAction` to wrap existing nodes, and use `ResearchGraphState` as the only state convention.
- Keep persistence and error handling aligned with `SimpleResearchRunner`.
- Do not introduce pause/resume/stop lifecycle in Graph runner during this stage; leave those for stages 5 and 6.
- Do not use runtime mocks. Unit tests may use local stub nodes/services to validate orchestration behavior.

## Evidence / References

- `.codex/graph-dynamic-orchestration-plan.md`: stage 4 goals and acceptance criteria.
- `.codex/tasks/add-graph-node-adapter.md`: completed stage 3 details and verification.
- `src/main/java/top/lanshan/manmu/runner/ResearchRunner.java`: runner contract.
- `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`: behavior reference for node ordering, route decisions, report persistence, session history, and runner error events.
- `src/main/java/top/lanshan/manmu/graph/ResearchGraphState.java`: graph state helper.
- `src/main/java/top/lanshan/manmu/graph/ResearchNodeGraphAction.java`: node adapter to reuse.
- `src/main/java/top/lanshan/manmu/model/ResearchState.java`: business state to preserve.
- `src/main/java/top/lanshan/manmu/model/ResearchEvent.java`: SSE event type.
- `src/main/java/top/lanshan/manmu/node/ResearchNode.java`: existing node contract.
- `src/test/java/top/lanshan/manmu/runner/SimpleResearchRunnerTest.java`: useful reference for stubs and behavior assertions.
- Local Maven jar: `C:/Users/20232/.m2/repository/com/alibaba/cloud/ai/spring-ai-alibaba-graph-core/1.0.0.4/spring-ai-alibaba-graph-core-1.0.0.4.jar`.
- `javap` observations from the local jar:
  - `StateGraph.addNode(String, AsyncNodeAction)`;
  - `StateGraph.addEdge(String, String)`;
  - `StateGraph.addConditionalEdges(String, AsyncEdgeAction, Map<String, String>)`;
  - `StateGraph.compile()`;
  - `CompiledGraph.call(Map<String, Object>)`;
  - `CompiledGraph.fluxStream(Map<String, Object>)`;
  - `EdgeAction.apply(OverAllState) -> String`;
  - `AsyncEdgeAction.edge_async(EdgeAction)`;
  - `CompiledGraph` also exposes `invoke(...)`, `call(...)`, and reactive `fluxStream(...)`.
- `AGENTS.md`: Java 17, Maven, Docker/PostgreSQL, real E2E, no-secret, and Chinese commit constraints.

## Files Touched

- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-graph-auto-research-runner.md`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/graph/ResearchGraphBuilder.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/graph/ResearchGraphState.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/graph/ResearchGraphStateSerializer.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/GraphResearchRunner.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/ResearchRunnerConfiguration.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/runner/GraphResearchRunnerTest.java`

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
- `Get-Content -LiteralPath 'AGENTS.md' -Raw`
- `Get-Content -LiteralPath '.codex\graph-dynamic-orchestration-plan.md' -Raw`
- `Get-Content -LiteralPath '.codex\tasks\add-graph-node-adapter.md' -Raw`
- `Get-Content -LiteralPath 'src\main\java\top\lanshan\manmu\runner\ResearchRunner.java' -Raw`
- `Get-Content -LiteralPath 'src\main\java\top\lanshan\manmu\runner\SimpleResearchRunner.java' -Raw`
- `Get-Content -LiteralPath 'src\main\java\top\lanshan\manmu\graph\ResearchGraphState.java' -Raw`
- `Get-Content -LiteralPath 'src\main\java\top\lanshan\manmu\graph\ResearchNodeGraphAction.java' -Raw`
- `javap -classpath <graph-core-jar> -p com.alibaba.cloud.ai.graph.StateGraph`
- `jar tf <graph-core-jar>` filtered for graph/action/state classes.
- `javap -classpath <graph-core-jar> -p com.alibaba.cloud.ai.graph.CompiledGraph`
- `javap -classpath <graph-core-jar> -p com.alibaba.cloud.ai.graph.GraphResponse`
- `javap -classpath <graph-core-jar> -p com.alibaba.cloud.ai.graph.action.EdgeAction com.alibaba.cloud.ai.graph.action.AsyncEdgeAction`
- `javap -classpath <graph-core-jar> -p com.alibaba.cloud.ai.graph.KeyStrategyFactory com.alibaba.cloud.ai.graph.action.Command com.alibaba.cloud.ai.graph.action.AsyncCommandAction`
- `Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz'`

## Verification

- Focused tests passed with Java 17:
  - `mvn -Dtest=GraphResearchRunnerTest test`
  - `mvn "-Dtest=ResearchGraphStateTest,ResearchNodeGraphActionTest,GraphResearchRunnerTest" test`
- Full Java 17 verification passed:
  - `mvn test`
  - Result: 108 tests, 0 failures, 0 errors, 0 skipped.
  - During full tests, one DeepSeek TLS handshake timeout was retried by Spring AI and did not fail the build.
- Package command passed:
  - `mvn -DskipTests package`
- Real backend E2E passed:
  - Started `target/deepresearch-mvp-0.1.0-SNAPSHOT.jar` on port `18080` with `--mvp.research.runner=graph`.
  - Startup used PostgreSQL `manmu-postgres` on localhost port `5432`; Flyway validated the schema at version 2.
  - `/api/model/current` reported current provider `dashscope`, model `qwen-turbo-2025-04-28`, and `apiKeyConfigured=true`.
  - `/chat/stream` with `session_id=codex-graph-e2e-20260522-2303`, `thread_id=codex-graph-e2e-20260522-2303-1`, `auto_accepted_plan=true`, and `enable_deepresearch=false` emitted `coordinator` then `event:done`.
  - `/api/reports/codex-graph-e2e-20260522-2303-1/exists` returned true.
  - `/api/reports/codex-graph-e2e-20260522-2303-1` returned the saved direct answer report.
  - `/api/sessions/codex-graph-e2e-20260522-2303/threads/codex-graph-e2e-20260522-2303-1` returned `status=COMPLETED` and matching `report_thread_id`.
  - `/api/research/stream` with `threadId=codex-graph-e2e-20260522-2304-1`, `auto_accepted_plan=true`, and `enable_deepresearch=false` emitted `coordinator` then `event:done`.
  - Verification service was stopped and port `18080` was confirmed closed.

## Known Failures / Blockers

- No upstream branch is configured for `main`; merge-base is unavailable.
- `.claude/` is untracked and must be ignored unless the user explicitly asks otherwise.
- `.codex/graph-dynamic-orchestration-plan.md`, `.codex/tasks/pre-graph-core-nodes.md`, `.codex/tasks/add-research-runner-interface.md`, `.codex/tasks/add-graph-state-adapter.md`, `.codex/tasks/add-graph-node-adapter.md`, and this handoff file are untracked. Do not treat that as a scope violation.
- The local `spring-ai-alibaba-graph-core` sources jar is not available; only the binary jar is present. Use `javap`, official docs, or dependency source download if needed.
- Prior stage verification saw HTTP 400 when calling `/api/model/switch`; this is out of scope for Graph runner unless it blocks explicit graph E2E setup.
- Some older source comments display mojibake due to encoding display; do not clean unrelated comments in this stage.

## Next Actions

1. Start the next Graph migration stage from commit `c26c8a8`.
2. Implement Graph human pause/resume or stop semantics in a later stage, not as a continuation of this completed stage.
3. Keep `mvp.research.runner=simple` as the default until the dedicated default-switch stage.

## Open Questions

- Future stage question: whether Graph pause/resume should keep using `ResearchGraphState` plus `ResumeDecision`, or introduce a narrower checkpoint state once human feedback is wired.
- Future stage question: how to implement Graph running/paused stop semantics without diverging from the existing `SimpleResearchRunner` persistence contract.
- Whether `.codex` planning and handoff files should ever be committed remains a user choice; current stage code commits did not include them.

## Avoid / Do Not Redo

- Do not redo completed stage 1 runner abstraction work.
- Do not redo completed stage 2 graph state adapter work.
- Do not redo completed stage 3 node adapter work.
- Do not modify `.local`, `.claude`, `target`, `.idea`, or secrets.
- Do not change `mvp.research.runner` default away from `simple`.
- Do not implement Graph human pause/resume, feedback replan, running/paused stop, graph default switching, or simple runner removal in this stage.
- Do not alter existing `ResearchNode` implementations or `SimpleResearchRunner` behavior unless a compile issue absolutely requires a tiny compatibility change.
- Do not introduce RAG, MCP, Redis, front-end work, professional KB, coder agents, full parallel executor, or runtime mocks.
- Do not skip the real backend curl E2E smoke test after implementation.

## Resume Prompt
Resume task add-graph-auto-research-runner. Read .codex/tasks/add-graph-auto-research-runner.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
