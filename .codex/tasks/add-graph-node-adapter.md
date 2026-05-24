# Task Handoff: add-graph-node-adapter
Updated: 2026-05-22 21:45:58 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 27e75c3c3ed0ea8205457642ab9ea0cec995a755
Current Commit: c7e8f0432e61e0cce0bb8cae6ce3947da8dd7999

## Project Mainline

- This repository is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under package `top.lanshan.manmu`.
- The long-running project direction is to build a simplified but real production-path DeepResearch workflow: real LLM calls, real search provider calls, WebFlux SSE progress, PostgreSQL-backed persistence, pause/resume/stop lifecycle, and no runtime mock fallback.
- The project completed the pre-Graph serial foundations: explicit `coordinator`, `plan_validator`, `human_feedback`, and `research_team` node-shaped components.
- Stage 1 of the Graph migration completed at commit `1d8fd37`: controllers depend on `ResearchRunner`, `SimpleResearchRunner` remains the default, `mvp.research.runner=simple` exists, and `spring-ai-alibaba-graph-core` is on the classpath.
- Stage 2 completed at commit `27e75c3`: `ResearchGraphState` and `ResearchGraphStateKeys` can carry the existing `ResearchState`, accumulated SSE `ResearchEvent` values, terminal status, resume decision, and low-risk route reads.
- Stage 3 completed at commit `c7e8f04`: existing `ResearchNode` implementations can now be wrapped as Spring AI Alibaba Graph `NodeAction` / `AsyncNodeAction` values through `ResearchNodeGraphAction`.
- The agreed migration direction is to introduce Spring AI Alibaba Graph as a dynamic orchestration layer around existing nodes without expanding into RAG, MCP, Redis, front-end work, professional KB, coder agents, or full parallel execution.
- The Graph migration plan is saved at `.codex/graph-dynamic-orchestration-plan.md` and remains the source of truth for the stage sequence.

## Stage Role in Mainline

- This was stage 3 of the Graph migration: `add-graph-node-adapter`.
- The stage wrapped existing `ResearchNode` implementations as Graph node actions without rewriting node business logic.
- It connected the stage 2 state adapter to Spring AI Alibaba Graph's action API, so later `GraphResearchRunner` work can build a graph from the same nodes used by `SimpleResearchRunner`.
- It preserved the current runtime path completely; no controller behavior, default runner selection, or `SimpleResearchRunner` orchestration changed.

## Mainline Progression

- Stage 1 made runner selection possible.
- Stage 2 created the state container that Graph execution can use while preserving `ResearchState` as the business state.
- Stage 3 proved a reusable node adapter can read graph state, invoke a `ResearchNode`, collect emitted events, append them back into graph state, and return the updated state map.
- The next stage should be `add-graph-auto-research-runner`, which builds the first minimal `GraphResearchRunner` auto-complete path using this adapter.

## Related Stage Handoffs

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

- Implement the third Graph migration stage: add a reusable graph node/action adapter around existing `ResearchNode` implementations.
- Do not implement `GraphResearchRunner`.
- Do not build graph edges or conditional routing yet.
- Do not switch the default runner from `simple` to `graph`.

## Task Theme / User Intent

- The user wanted to continue the Graph migration in a new session and complete only the node adapter stage.
- The intent was to execute a small, independently verified migration step without re-planning the whole Graph migration.
- The user prefers small stages with Chinese commits, focused tests, full `mvn test`, and real HTTP/SSE validation after each implementation stage.

## Acceptance Criteria

- Add a graph node/action adapter class under `top.lanshan.manmu.graph`.
- The adapter must use the existing `ResearchNode` contract:
  - read `ResearchState` from graph state via `ResearchGraphState`;
  - invoke `ResearchNode.run(state)`;
  - collect emitted `ResearchEvent` values in order;
  - append those events into graph state via `ResearchGraphState.appendEvents(...)`;
  - return the updated graph state map expected by Spring AI Alibaba Graph.
- Implement compatibility with the dependency's Graph action API:
  - `NodeAction.apply(OverAllState) -> Map<String, Object>`;
  - `AsyncNodeAction.node_async(NodeAction)`;
  - `OverAllState.data()` returns an unmodifiable copy, so the adapter returns a mutable updated map for this path.
- Keep the API surface small and do not add graph construction or runner lifecycle logic in this stage.
- Add focused unit tests using stub `ResearchNode` implementations.
- Run focused tests and full `mvn test` with Java 17.
- Start the backend locally and run a real curl SSE smoke test through the unchanged simple path, then stop the backend and confirm the port is closed.
- Commit the completed stage with a Chinese message meaning "add research node graph adapter".

## Scope

- Implemented in `C:/MainData/code/Codex_project/M-agent`.
- Changes stayed scoped to `top.lanshan.manmu.graph`, its focused tests, and this handoff file.
- Existing node implementations under `top.lanshan.manmu.node` were read but not changed.
- `SimpleResearchRunner`, controllers, runner selection configuration, report persistence, session history, and model provider code remained unchanged.
- `C:/MainData/code/Codex_project/deepresearch-main` was not modified.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/graph`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/graph`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-graph-node-adapter.md`

### Read-only Reference Roots

- `C:/MainData/code/Codex_project/deepresearch-main`
- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/graph-dynamic-orchestration-plan.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model`
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
- Current commit is `c7e8f0432e61e0cce0bb8cae6ce3947da8dd7999`, whose commit message means "add research node graph adapter".
- No upstream branch is configured for `main`; merge-base is unavailable.
- There are no tracked working-tree diffs after the stage commit.
- `git status --short` shows only expected untracked `.claude/`, `.codex/graph-dynamic-orchestration-plan.md`, `.codex/tasks/add-graph-state-adapter.md`, `.codex/tasks/add-research-runner-interface.md`, `.codex/tasks/pre-graph-core-nodes.md`, and this handoff file.
- Backend service was stopped after verification and port `18080` was confirmed closed.

## Completed

- Added `ResearchNodeGraphAction`.
- Implemented `NodeAction.apply(OverAllState)` compatibility.
- Added direct `apply(Map<String, Object>)` support for tests and later internal runner use.
- Added `from(...)`, static `async(...)`, and instance `async()` convenience methods for later `StateGraph.addNode(...)` usage.
- Preserved direct map semantics as in-place updates, while `OverAllState` invocation returns a mutable updated map because `OverAllState.data()` is unmodifiable.
- Added focused tests covering:
  - node receives the existing `ResearchState`;
  - node mutations to `ResearchState` remain visible;
  - emitted `ResearchEvent` values are appended in order;
  - `OverAllState` invocation returns updated graph state;
  - `AsyncNodeAction` wrapper works;
  - sync and async node failures propagate.
- Ran focused tests, full Maven tests, and a real HTTP/SSE smoke test.
- Committed the stage at `c7e8f04`.

## Decisions

- Stage 3 targets the existing `ResearchNode` API instead of duplicating node logic.
- The adapter directly implements Spring AI Alibaba Graph `NodeAction`.
- The adapter exposes `AsyncNodeAction` convenience factories because this dependency's `StateGraph.addNode(...)` accepts `AsyncNodeAction`.
- The adapter propagates node errors and does not append synthetic error events; current runner-level error handling remains the owner of error events.
- Blocking collection from `ResearchNode.run(state)` is acceptable for this adapter stage because existing simple orchestration already executes node work on bounded elastic; later Graph runner execution can choose scheduling.
- Unit tests use local stub `ResearchNode` implementations because this stage validates a pure adapter layer, not runtime mock fallback.

## Evidence / References

- `.codex/graph-dynamic-orchestration-plan.md`: stage 3 goals and acceptance criteria.
- `.codex/tasks/add-graph-state-adapter.md`: completed stage 2 handoff and verification details.
- `src/main/java/top/lanshan/manmu/graph/ResearchGraphState.java`: state helper reused by the adapter.
- `src/main/java/top/lanshan/manmu/graph/ResearchGraphStateKeys.java`: state keys reused by the adapter.
- `src/test/java/top/lanshan/manmu/graph/ResearchGraphStateTest.java`: graph package test style.
- `src/main/java/top/lanshan/manmu/node/ResearchNode.java`: node contract adapted.
- `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`: read-only reference for existing node invocation and error ownership.
- `pom.xml`: includes `spring-ai-alibaba-graph-core` via `spring-ai-alibaba.version=1.0.0.4`.
- Local Maven jar: `C:/Users/20232/.m2/repository/com/alibaba/cloud/ai/spring-ai-alibaba-graph-core/1.0.0.4/spring-ai-alibaba-graph-core-1.0.0.4.jar`.
- `javap` observations:
  - `com.alibaba.cloud.ai.graph.action.NodeAction`;
  - `com.alibaba.cloud.ai.graph.action.AsyncNodeAction`;
  - `com.alibaba.cloud.ai.graph.OverAllState`;
  - `OverAllState.data()` returns an unmodifiable map;
  - `StateGraph.addNode(String, AsyncNodeAction)`.
- `AGENTS.md`: Java 17, Maven, Docker/PostgreSQL, real E2E, no-secret, and Chinese commit constraints.

## Files Touched

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/graph/ResearchNodeGraphAction.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/graph/ResearchNodeGraphActionTest.java`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-graph-node-adapter.md`

## Commands Run

- `Get-Content -LiteralPath 'C:\Users\20232\.codex\skills\task-handoff\SKILL.md' -Raw`
- `git rev-parse --show-toplevel`
- `Get-Content -LiteralPath '.codex\tasks\add-graph-node-adapter.md' -Raw`
- `Get-Content -LiteralPath 'AGENTS.md' -Raw`
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'`
- `git merge-base HEAD '@{upstream}'`
- `Get-Content -LiteralPath 'src\main\java\top\lanshan\manmu\graph\ResearchGraphState.java' -Raw`
- `Get-Content -LiteralPath 'src\main\java\top\lanshan\manmu\graph\ResearchGraphStateKeys.java' -Raw`
- `Get-Content -LiteralPath 'src\main\java\top\lanshan\manmu\node\ResearchNode.java' -Raw`
- `Get-Content -LiteralPath 'src\test\java\top\lanshan\manmu\graph\ResearchGraphStateTest.java' -Raw`
- `Get-Content -LiteralPath '.codex\graph-dynamic-orchestration-plan.md' -Raw`
- `Get-Content -LiteralPath 'pom.xml' -Raw`
- `Get-Content -LiteralPath 'src\main\java\top\lanshan\manmu\model\ResearchEvent.java' -Raw`
- `Get-Content -LiteralPath 'src\main\java\top\lanshan\manmu\model\ResearchState.java' -Raw`
- `Get-Content -LiteralPath 'src\main\java\top\lanshan\manmu\runner\SimpleResearchRunner.java' -Raw`
- `javap -classpath <graph-core-jar> -p com.alibaba.cloud.ai.graph.OverAllState`
- `javap -classpath <graph-core-jar> -p com.alibaba.cloud.ai.graph.action.NodeAction`
- `javap -classpath <graph-core-jar> -p com.alibaba.cloud.ai.graph.action.AsyncNodeAction`
- `mvn "-Dtest=ResearchGraphStateTest,ResearchNodeGraphActionTest" test`
- `mvn test`
- `docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}"`
- `Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue`
- `mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18080`
- `curl.exe -s http://localhost:18080/api/model/current`
- `curl.exe -s http://localhost:18080/api/model/providers`
- `curl.exe --no-buffer -s -X POST http://localhost:18080/chat/stream -H "Content-Type: application/json" --data-binary '@target\codex-stage3-request.json'`
- `curl.exe -s http://localhost:18080/api/reports/codex-stage3-20260522214318/exists`
- `curl.exe -s http://localhost:18080/api/sessions/codex-stage3-session/threads/codex-stage3-20260522214318`
- `Stop-Process -Id 16544 -Force`
- `git add -- src/main/java/top/lanshan/manmu/graph/ResearchNodeGraphAction.java src/test/java/top/lanshan/manmu/graph/ResearchNodeGraphActionTest.java`
- `git commit -m <Chinese message meaning add research node graph adapter>`
- `git show --stat --oneline HEAD`

## Verification

- Focused tests passed with Java 17:
  - `mvn "-Dtest=ResearchGraphStateTest,ResearchNodeGraphActionTest" test`
  - 15 tests, 0 failures, 0 errors.
- Full tests passed with Java 17:
  - `mvn test`
  - 104 tests, 0 failures, 0 errors.
- Docker/PostgreSQL verification:
  - `manmu-postgres` was running and healthy.
  - Application startup connected to `jdbc:postgresql://localhost:5432/manmu`.
  - Flyway validated 2 migrations and reported schema version 2.
- Real HTTP/SSE smoke verification:
  - Started backend on port `18080`.
  - Current configured model was `dashscope/qwen-turbo-2025-04-28`, with API key configured.
  - Sent `/chat/stream` request with `session_id=codex-stage3-session`, `thread_id=codex-stage3-20260522214318`, `enable_deepresearch=false`, `auto_accepted_plan=true`, and query `What is 2 plus 2? Reply briefly.`
  - SSE output included `event:message` from `coordinator`.
  - SSE output included `event:done` from `__END__`.
  - Report existence check returned true.
  - Session history for the thread returned `COMPLETED`.
  - Backend process was stopped and port `18080` was confirmed closed.

## Known Failures / Blockers

- No upstream branch is configured for `main`; merge-base is unavailable.
- `.claude/` is untracked and must be ignored unless the user explicitly asks otherwise.
- `.codex/graph-dynamic-orchestration-plan.md`, `.codex/tasks/pre-graph-core-nodes.md`, `.codex/tasks/add-research-runner-interface.md`, `.codex/tasks/add-graph-state-adapter.md`, and this handoff file are untracked. Do not treat that as a scope violation.
- The local `spring-ai-alibaba-graph-core` sources jar is not available; only the binary jar is present. `javap` was sufficient for this stage.
- Attempts to call `/api/model/switch` during verification returned HTTP 400 due to request binding/format mismatch; this was not part of the stage scope, and the smoke test succeeded with the already configured current model.
- Some older source comments display mojibake due to encoding display; this stage did not modify those files.

## Next Actions

1. Start or resume the next stage `add-graph-auto-research-runner`; read `.codex/graph-dynamic-orchestration-plan.md`, `ResearchGraphState`, `ResearchNodeGraphAction`, and `SimpleResearchRunner` before editing.
2. Implement the first minimal `GraphResearchRunner` auto-complete path using the adapter, without adding human pause/resume, stop, RAG, MCP, Redis, front-end, or full parallel execution.
3. Preserve `mvp.research.runner=simple` as the default until the graph auto path passes focused tests, full `mvn test`, and real HTTP/SSE verification under explicit graph configuration.

## Open Questions

- The exact `StateGraph` construction API should be re-checked during stage 4 with `javap` or official documentation before implementing `GraphResearchRunner`.
- The `/api/model/switch` HTTP 400 observed during smoke setup may deserve a separate small fix later, but it is unrelated to this Graph adapter stage.
- Whether `.codex` planning and handoff files should ever be committed remains a user choice; current stage code commits did not include them.

## Avoid / Do Not Redo

- Do not redo completed stage 1 runner abstraction work.
- Do not redo completed stage 2 graph state adapter work.
- Do not redo completed stage 3 node adapter work.
- Do not modify `.local`, `.claude`, `target`, `.idea`, or secrets.
- Do not implement Graph human pause/resume, feedback replan, stop, graph-default switching, or simple runner removal in the next auto-runner stage.
- Do not change `mvp.research.runner` default away from `simple` until the planned default-switch stage.
- Do not alter existing `ResearchNode` implementations or `SimpleResearchRunner` behavior unless a compile issue absolutely requires a tiny compatibility change.
- Do not introduce RAG, MCP, Redis, front-end work, professional KB, coder agents, full parallel executor, or runtime mocks.
- Do not skip the real backend curl E2E smoke test after future implementation stages.

## Resume Prompt
Resume task add-graph-node-adapter. Read .codex/tasks/add-graph-node-adapter.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
