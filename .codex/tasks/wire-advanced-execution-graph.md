# Task Handoff: wire-advanced-execution-graph
Updated: 2026-05-24T13:13:00+08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: e118e30dd0c087cd29d18ec7f0d2aa5b21969ae7
Current Commit: stage commit created; run `git rev-parse HEAD` for the exact hash

## Project Mainline

- M-agent is a Java 17 / Maven / Spring Boot 3.4.x simplified DeepResearch-style backend under package `top.lanshan.manmu`.
- The runtime direction is a Graph-based backend using Spring AI Alibaba Graph, WebFlux SSE, PostgreSQL persistence, Docker PostgreSQL, session history, session context, and real model providers.
- The project already supports direct answer, automatic research, manual plan gate, accepted resume, rejected replan, stop, report persistence, session history, session context, stable stream events, step execution metadata, assignment-only `parallel_executor`, dynamic `researcher_n`, and minimal dynamic `coder_n`.
- The active long-running direction is to build a minimal Graph advanced execution skeleton before adding RAG, Redis, MCP, Docker coder execution, or frontend work.

## Stage Role in Mainline

- This is stage 6 of `.codex/graph-advanced-execution-plan.md`: `wire-advanced-execution-graph`.
- It exists because stages 3, 4, and 5 added the scheduler, named researcher executors, and named coder executors without changing the default Graph route.
- This stage wires those pieces into the Graph topology behind `mvp.research.advanced-execution.enabled=true`, while preserving the legacy linear route when the switch is false.

## Mainline Progression

- Stage 1 stabilized SSE event fields and sequence metadata.
- Stage 2 introduced stable step ids and execution state metadata.
- Stage 3 added disabled-by-default assignment through `ParallelExecutorNode`.
- Stage 4 added dynamic `researcher_n` execution behavior.
- Stage 5 added dynamic `coder_n` execution behavior.
- This stage connected `research_team -> parallel_executor -> researcher_n/coder_n -> research_team -> reporter` under an explicit advanced-execution switch. It uses a sequential executor selection strategy for now while preserving the advanced node names, statuses, and SSE contract expected by a future fan-out implementation.
- The next stage can enable this path by default after full regression coverage and documentation updates.

## Related Stage Handoffs

- Immediate upstream completed stage: `.codex/tasks/add-minimal-coder-node.md`.
- Earlier upstream stages: `.codex/tasks/add-multi-researcher-executors.md`, `.codex/tasks/add-parallel-executor-assignment.md`, `.codex/tasks/add-step-execution-state-model.md`, and `.codex/tasks/add-stable-stream-event-contract.md`.
- Planning reference: `.codex/graph-advanced-execution-plan.md`.
- Historical Graph migration references only: `.codex/graph-dynamic-orchestration-plan.md` and older Graph task handoffs under `.codex/tasks/`.
- Likely next stage: `enable-advanced-execution-default`.

## Goal

Wire the advanced execution Graph route so `parallel_executor`, `researcher_n`, and `coder_n` execute plan steps only when `advanced-execution.enabled=true`, while `enabled=false` preserves the existing `research_team -> researcher/processor/reporter` behavior. This goal is implemented and verified.

## Task Theme / User Intent

- Resume from the stage 5 handoff and continue the staged advanced execution work.
- Keep the implementation minimal, production-shaped, and reversible through configuration.
- Do not introduce RAG, Redis, MCP, Docker coder execution, frontend behavior, or mock agent/search fallbacks.

## Acceptance Criteria

- Read `AGENTS.md`, `.codex/tasks/add-minimal-coder-node.md`, and `.codex/graph-advanced-execution-plan.md` before editing.
- Create/update this handoff and verify scope safety before code changes.
- `advanced-execution.enabled=false` keeps the old graph route unchanged.
- `advanced-execution.enabled=true` routes non-terminal plans from `research_team` to `parallel_executor`.
- `parallel_executor` assigns steps, then the Graph runs the corresponding named executor node.
- Research steps can reach `researcher_0`; processing steps can reach `coder_0`.
- Executor completion returns to `research_team`; all terminal steps route to `reporter`.
- Focused tests cover old-route preservation and enabled advanced routing through `parallel_executor`, `researcher_0`, and `coder_0`.
- `mvn test` passes.
- Real HTTP/SSE E2E runs with advanced execution explicitly enabled, uses Docker PostgreSQL and a real model provider, verifies advanced node events, report readability, and session history `COMPLETED`.
- Service started for E2E is stopped and port `18080` is released.

## Scope

- Work only inside `C:/MainData/code/Codex_project/M-agent`.
- This stage may modify Graph construction, advanced execution config wiring, `ResearchTeamNode` routing semantics, runner tests, node/model tests, and this handoff.
- This stage should not enable advanced execution by default.
- This stage should not add Docker execution, MCP tools, RAG, Redis, frontend behavior, or copy full `deepresearch-main` infrastructure.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/graph`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/config`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/test/resources`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/wire-advanced-execution-graph.md`

### Read-only Reference Roots

- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/graph-advanced-execution-plan.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-minimal-coder-node.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-multi-researcher-executors.md`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/ResearcherNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/CoderNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/ParallelExecutorNode.java`
- `C:/MainData/code/Codex_project/deepresearch-main`
- `C:/Users/20232/.codex/skills/task-handoff/SKILL.md`

### Forbidden Write Roots

- `C:/MainData/code/Codex_project/deepresearch-main`
- `C:/MainData/code/Codex_project/M-agent/.local`
- `C:/MainData/code/Codex_project/M-agent/target`
- `C:/MainData/code/Codex_project/M-agent/.idea`
- `C:/MainData/code/Codex_project/M-agent/.claude`
- Existing unrelated untracked older `.codex` planning/task files unless the user explicitly asks to curate or commit them.

## Current State

- Stage 6 is implemented and verified.
- `ResearchGraphBuilder` now chooses the advanced topology only when `AdvancedExecutionProperties.isEnabled()` is true.
- The advanced topology explicitly adds `parallel_executor`, configured `researcher_n` nodes, configured `coder_n` nodes, and routes executor completion back to `research_team`.
- `ResearchTeamNode` now routes to `PARALLEL_EXECUTOR` only when constructed with advanced execution enabled; the default constructor and default properties keep legacy routing.
- `ResearchRunnerConfiguration` passes advanced execution properties and the real `ResearcherAgent` / `ProcessorAgent` into the graph builder so production dynamic executor nodes can be created from config.
- This stage is committed with message `接入高级图执行路由`; run `git rev-parse HEAD` for the exact hash.
- `main` has no upstream configured.
- Older unrelated untracked `.codex` files remain in the workspace and should be left alone.

## Completed

- Created and updated this resume-ready handoff.
- Implemented config-gated advanced Graph topology.
- Added `ResearchTeamRoute.PARALLEL_EXECUTOR`.
- Added focused advanced route tests in `GraphResearchRunnerTest`.
- Added `ResearchTeamNode` advanced routing test.
- Ran focused Maven tests and full Maven tests.
- Ran real HTTP/SSE E2E with Docker PostgreSQL and DeepSeek using explicit advanced execution config.
- Stopped the backend service and confirmed port `18080` was released.

## Decisions

- Treat `deepresearch-main` as read-only reference. Do not copy its full MCP, reflection, streaming, or tool-callback infrastructure.
- Use a sequential executor selection strategy for this stage because Spring AI Alibaba Graph fan-out semantics were not explored deeply here. The visible node names, statuses, and events still match the future parallel shape.
- Keep `advanced-execution.enabled=false` as the default. Stage 7 should decide when to enable the advanced path by default.
- Create dynamic executor nodes inside `ResearchGraphBuilder` from configured counts and the existing production agents. Tests may provide explicit `researcher_0` and `coder_0` nodes directly.
- Preserve the old `researcher` / `processor` linear graph path when the advanced switch is off.

## Evidence / References

- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-minimal-coder-node.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/graph-advanced-execution-plan.md`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/graph/ResearchGraphBuilder.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/GraphResearchRunner.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/config/AdvancedExecutionProperties.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/ResearchTeamNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/ParallelExecutorNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/ResearcherNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/CoderNode.java`
- Real E2E auto thread: `wire-advanced-20260524130742`.
- Manual accept resume session/thread: `wire-advanced-manual-20260524130907` / `wire-advanced-manual-20260524130907-thread`.
- Rejected replan session/thread: `wire-advanced-reject-20260524131011` / `wire-advanced-reject-20260524131011-thread`.
- Stop paused session/thread: `wire-advanced-stop-20260524131056` / `wire-advanced-stop-20260524131056-thread`.

## Files Touched

- `.codex/tasks/wire-advanced-execution-graph.md`
- `src/main/java/top/lanshan/manmu/graph/ResearchGraphBuilder.java`
- `src/main/java/top/lanshan/manmu/model/ResearchTeamRoute.java`
- `src/main/java/top/lanshan/manmu/node/ResearchTeamNode.java`
- `src/main/java/top/lanshan/manmu/runner/ResearchRunnerConfiguration.java`
- `src/test/java/top/lanshan/manmu/node/ResearchTeamNodeTest.java`
- `src/test/java/top/lanshan/manmu/runner/GraphResearchRunnerTest.java`

## Commands Run

- `Get-Content -Raw C:\Users\20232\.codex\skills\task-handoff\SKILL.md`
- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'`
- `git merge-base HEAD '@{upstream}'`
- `Get-Content -Raw AGENTS.md`
- `Get-Content -Raw .codex\tasks\add-minimal-coder-node.md`
- `Get-Content -Raw .codex\graph-advanced-execution-plan.md`
- `rg --files`
- `rg -n "class ResearchGraphBuilder|advanced-execution|ParallelExecutorNode|CoderNode|ResearcherNode|conditional|route|edge|StateGraph|GraphResearchRunner|ResearchTeamNode|ResearchNodeGraphAction" src pom.xml .codex -g "*.java" -g "*.yml" -g "*.yaml" -g "*.properties" -g "*.md"`
- `mvn '-Dtest=GraphResearchRunnerTest,ResearchTeamNodeTest,ParallelExecutorNodeTest,ResearcherNodeTest,CoderNodeTest,AdvancedExecutionPropertiesTest' test`
- `mvn test`
- Docker MCP `docker_container_list` confirmed `manmu-postgres` was running and healthy.
- Started backend with `mvn spring-boot:run` on port `18080`, profile `real-model`, and environment variables enabling advanced execution.
- `curl.exe -X POST http://localhost:18080/api/model/switch ...` switched to DeepSeek.
- `curl.exe -N -X POST http://localhost:18080/api/research/stream ...` verified the advanced automatic path.
- `curl.exe http://localhost:18080/api/reports/<threadId>` verified persisted report readability.
- `curl.exe http://localhost:18080/api/sessions/<sessionId>/threads/<threadId>` verified session history.
- `curl.exe -N -X POST http://localhost:18080/chat/stream ...` verified manual pause.
- `curl.exe -N -X POST http://localhost:18080/chat/resume ...` verified accepted resume and rejected replan.
- `curl.exe -X POST http://localhost:18080/chat/stop ...` verified stopping a paused thread.
- `Stop-Process` stopped the local backend Java process and `Get-NetTCPConnection -LocalPort 18080` confirmed release.

## Verification

- Focused tests passed: `mvn '-Dtest=GraphResearchRunnerTest,ResearchTeamNodeTest,ParallelExecutorNodeTest,ResearcherNodeTest,CoderNodeTest,AdvancedExecutionPropertiesTest' test` with 38 tests, 0 failures, 0 errors.
- Full test suite passed: `mvn test` with 129 tests, 0 failures, 0 errors.
- Real HTTP/SSE automatic advanced path passed on port `18080` using Docker PostgreSQL `manmu-postgres` and DeepSeek. SSE for thread `wire-advanced-20260524130742` contained `parallel_executor=6`, `researcher_0=4`, `coder_0=4`, `event:done=1`, and `event:error=0`.
- Report API returned success for `wire-advanced-20260524130742`.
- Session history API returned `COMPLETED` for `wire-advanced-20260524130742`.
- Manual plan gate paused at `human_feedback.waiting` for `wire-advanced-manual-20260524130907-thread`.
- Accepted resume completed and emitted advanced executor events, including `coder_0`, for `wire-advanced-manual-20260524130907-thread`.
- Rejected resume replanned and paused again at `human_feedback.waiting` for `wire-advanced-reject-20260524131011-thread`.
- Stop paused thread returned success and history `STOPPED` for `wire-advanced-stop-20260524131056-thread`.
- Backend service was stopped after E2E and port `18080` was confirmed released.

## Known Failures / Blockers

- No implementation blocker is known.
- `main` has no upstream branch configured.
- The advanced execution plan renders garbled in the current PowerShell console encoding, but stage ids and acceptance details were identifiable.
- Older unrelated untracked `.codex` files remain in the workspace and should be left alone.
- The advanced route is not truly parallel yet; this stage intentionally uses sequential executor selection while preserving the advanced Graph contract.

## Next Actions

1. Start stage 7, `enable-advanced-execution-default`, only after reviewing whether the sequential strategy is acceptable as the default path.
2. In stage 7, keep the legacy route available or explicitly document its fallback/removal decision after another real E2E regression pass.
3. Continue leaving unrelated untracked older `.codex` files alone unless the user explicitly asks to curate or commit them.

## Open Questions

- Should stage 7 keep a short-term explicit fallback to the linear route after making advanced execution default?
- Should the next review explore Spring AI Alibaba Graph fan-out semantics, or continue with the simpler sequential advanced route until Docker/MCP/RAG work arrives?

## Avoid / Do Not Redo

- Do not rewrite the stable event contract from stage 1.
- Do not rework the step execution model from stage 2 except for tiny routing helpers if required.
- Do not redo `ParallelExecutorNode` assignment semantics from stage 3 unless tests reveal a routing gap.
- Do not redo dynamic `ResearcherNode` or `CoderNode` behavior from stages 4 and 5.
- Do not enable advanced execution by default in this stage.
- Do not add Docker execution, MCP tool calls, RAG, Redis, frontend behavior, or large copies from `deepresearch-main`.
- Do not read, print, or commit API keys from `.local/model-providers.json`.
- Do not modify `deepresearch-main`; it is read-only reference material.
- Do not leave a local backend service running after E2E.

## Resume Prompt
Resume task wire-advanced-execution-graph. Read .codex/tasks/wire-advanced-execution-graph.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
