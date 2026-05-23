# Task Handoff: add-parallel-executor-assignment
Updated: 2026-05-23
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 9530fa36c88006cdb4f93659305dbdb73e1a13b1
Current Commit: stage commit containing this handoff

## Project Mainline

- M-agent is a Java 17 / Maven / Spring Boot 3.4.x simplified DeepResearch-style backend under package `top.lanshan.manmu`.
- The default runtime path is the Graph-based backend using Spring AI Alibaba Graph, WebFlux SSE, PostgreSQL persistence, Docker PostgreSQL, session history, session context, and real model providers.
- The project already supports direct answer, automatic research, manual plan gate, accepted resume, rejected replan, stop, report persistence, session history, and real HTTP/SSE validation.
- The active long-running direction is to build a Graph advanced execution skeleton before adding RAG, Redis, MCP, Docker coder execution, or frontend work.
- Upstream stages established a stable SSE event contract and a step execution state model. This stage adds the first schedulable assignment node, `parallel_executor`, while keeping the default Graph route behavior unchanged.

## Stage Role in Mainline

- This is stage 3 of `.codex/graph-advanced-execution-plan.md`.
- It turns stable plan steps into assignable work items by introducing `ParallelExecutorNode`.
- It validates assignment semantics for future `researcher_n` and `coder_n` executor stages without wiring those dynamic executors into the main Graph path yet.

## Mainline Progression

- Stage 1, `add-stable-stream-event-contract`, stabilized stream fields such as `sequence`, `event_type`, `node_name`, `node_type`, `executor_id`, `step_id`, `phase`, `status`, `display_title`, and `payload`.
- Stage 2, `add-step-execution-state-model`, introduced `StepExecutionStatus`, deterministic step ids, execution metadata on `ResearchStep`, and lightweight execution node state on `ResearchState`.
- This stage adds deterministic assignment behavior on top of those primitives. Future stages should execute assigned steps through `researcher_0`, `researcher_1`, and `coder_0` nodes, then later wire the advanced execution graph behind an explicit feature flag.

## Related Stage Handoffs

- Immediate upstream completed stage: `.codex/tasks/add-step-execution-state-model.md`.
- Event contract upstream stage: `.codex/tasks/add-stable-stream-event-contract.md`.
- Planning reference: `.codex/graph-advanced-execution-plan.md`.
- Historical Graph migration references only: `.codex/graph-dynamic-orchestration-plan.md` and older graph task handoffs under `.codex/tasks/`.
- Likely next stage after this one: `add-multi-researcher-executors`.

## Goal

Add a `ParallelExecutorNode` assignment component that assigns pending plan steps to named executor nodes such as `researcher_0`, `researcher_1`, and `coder_0`, emits assignment events, and leaves the current default Graph execution route compatible and stable.

## Task Theme / User Intent

- Continue the staged advanced Graph execution work with a narrow, testable assignment-only milestone.
- Align M-agent with the DeepResearch main project's advanced execution skeleton while preserving the simplified backend's runnable shape.
- Do not add true parallel execution, dynamic executor nodes, coder execution, RAG, Redis, MCP, Docker execution, or frontend features in this stage.

## Acceptance Criteria

- Add configuration properties for advanced execution, with defaults equivalent to:
  - `mvp.research.advanced-execution.enabled=false`
  - `mvp.research.advanced-execution.parallel-node-count.researcher=2`
  - `mvp.research.advanced-execution.parallel-node-count.coder=1`
- Add `ParallelExecutorNode` under the existing node model.
- `ParallelExecutorNode` reads the current `ResearchPlan` from `ResearchState`.
- It skips terminal steps and skips steps that are already assigned or processing.
- It assigns `RESEARCH` steps to `researcher_n` executor names.
- It assigns `PROCESSING` steps to `coder_n` executor names.
- It assigns processing steps only after all research steps are terminal.
- It writes `ResearchStep.assignedNode`, `ResearchStep.executionStatus(StepExecutionStatus.assigned(nodeName))`, and assignment metadata.
- It records assignment through `ResearchState.recordNodeStarted(nodeName, step)`, preserving `lastAssignedNodes`.
- It emits `step.assigned` events with enough payload to identify step id, assigned node, step type, and current status.
- Focused tests cover round-robin assignment, research-before-processing behavior, skip rules for assigned/processing/completed/error steps, empty or invalid plan handling, and event payload shape.
- `mvn test` passes.
- Real HTTP/SSE E2E confirms the old/default Graph path still completes automatic research and does not regress manual pause/resume/stop behavior.
- Backend service is stopped after E2E and port `18080` is released.

## Scope

- Work only inside `C:/MainData/code/Codex_project/M-agent`.
- This stage may add a node, configuration properties, assignment helper code, tests, and this task handoff.
- This stage does not wire `parallel_executor` into the default Graph topology.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/graph`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/config`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-parallel-executor-assignment.md`

### Read-only Reference Roots

- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/graph-advanced-execution-plan.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-step-execution-state-model.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-stable-stream-event-contract.md`
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

- Stage implementation is complete and committed by the stage commit containing this handoff, with message `添加并行执行任务分配器`.
- `ParallelExecutorNode` exists as an assignment-only Spring node and is not connected to the default `ResearchGraphBuilder` topology.
- Advanced execution config defaults are present in `application.yml` and registered through `AdvancedExecutionConfiguration`.
- `main` still has no upstream branch configured.
- Older unrelated untracked `.codex` planning/task files remain in the workspace and were intentionally left untouched.
- Backend service used for E2E was stopped, and port `18080` was confirmed released.

## Completed

- Added `AdvancedExecutionProperties` and `AdvancedExecutionConfiguration`.
- Added default disabled YAML config for `mvp.research.advanced-execution`.
- Added `ParallelExecutorNode` with:
  - research-before-processing assignment gating,
  - deterministic least-loaded round-robin assignment,
  - skip rules for terminal, assigned, processing, and explicitly assigned steps,
  - assignment status writes using `StepExecutionStatus.assigned(nodeName)`,
  - state updates through `ResearchState.recordNodeStarted(nodeName, step)`,
  - `step.assigned` events carrying top-level `step_id` and payload details.
- Added focused tests for config defaults, node-count lower bounds, round-robin assignment, processing gating, skip rules, no-op assignment, invalid plan handling, and event payload metadata.
- Ran focused tests, full Maven tests, and real HTTP/SSE regression through Docker PostgreSQL and real model/search paths.
- Confirmed old/default Graph path did not emit `parallel_executor` and still used `researcher` / `processor` / `reporter`.

## Decisions

- Keep `advanced-execution.enabled=false` as the default for this stage.
- Treat this stage as assignment-only: do not add `researcher_0`, `coder_0`, or advanced graph routing yet.
- Assign all currently eligible steps in one `parallel_executor` run for the current step phase.
- Use least-loaded deterministic node selection, with insertion order as the tie breaker.
- Keep `step.assigned` as a node-delta style event phase rather than adding a new `ResearchStreamEventType` now.
- Set top-level `ResearchEvent.stepId` during assignment while also keeping step details in the payload.
- Reuse `ResearchState.recordNodeStarted` for assignment so `runningNodes` and `lastAssignedNodes` are populated for future executor routing.
- Do not support deprecated `SYNTHESIS` directly in `ParallelExecutorNode`; upstream planner mapping normalizes legacy synthesis to `PROCESSING`.

## Evidence / References

- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/graph-advanced-execution-plan.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-step-execution-state-model.md`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/config/AdvancedExecutionProperties.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/config/AdvancedExecutionConfiguration.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/ParallelExecutorNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/config/AdvancedExecutionPropertiesTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/node/ParallelExecutorNodeTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/StepExecutionStatus.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchStep.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchState.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchEvent.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchNodeMetadata.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/graph/ResearchGraphBuilder.java`

## Files Touched

- `.codex/tasks/add-parallel-executor-assignment.md`
- `src/main/java/top/lanshan/manmu/config/AdvancedExecutionConfiguration.java`
- `src/main/java/top/lanshan/manmu/config/AdvancedExecutionProperties.java`
- `src/main/java/top/lanshan/manmu/node/ParallelExecutorNode.java`
- `src/main/resources/application.yml`
- `src/test/java/top/lanshan/manmu/config/AdvancedExecutionPropertiesTest.java`
- `src/test/java/top/lanshan/manmu/node/ParallelExecutorNodeTest.java`

## Commands Run

- `Get-Content -LiteralPath 'C:\Users\20232\.codex\skills\task-handoff\SKILL.md'`
- `Get-Content -LiteralPath '.codex\tasks\add-parallel-executor-assignment.md'`
- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` returned no upstream configured.
- `Get-Content -LiteralPath 'AGENTS.md'`
- `rg --files src/main/java src/test/java src/main/resources .codex | rg "(ResearchState|ResearchStep|StepExecutionStatus|ResearchEvent|ResearchNodeMetadata|ResearchTeamNode|ResearcherNode|ProcessorNode|ResearchGraphBuilder|GraphResearchRunner|application|ConfigurationProperties|Properties|graph-advanced-execution-plan|add-step-execution-state-model)"`
- `Get-Content` on the relevant model, node, graph, config, and test files listed in Evidence / References.
- `mvn '-Dtest=AdvancedExecutionPropertiesTest,ParallelExecutorNodeTest' test` with `JAVA_HOME=C:\WorkResources\JDKs\JDK17` passed after one small test-generic fix.
- `mvn test` with `JAVA_HOME=C:\WorkResources\JDKs\JDK17` passed: 118 tests, 0 failures, 0 errors.
- `docker_container_list` showed `manmu-postgres` running and healthy with port `5432` mapped.
- Started backend on port `18080` with `mvn spring-boot:run` and wrote logs under `target/http-check/stage3-run.log`.
- `curl.exe http://localhost:18080/api/model/current`
- `curl.exe http://localhost:18080/api/model/providers`
- `curl.exe -N --max-time 300 ... http://localhost:18080/chat/stream` for auto accepted research, output `target/http-check/auto.sse`.
- `curl.exe http://localhost:18080/api/reports/<autoThread>` and `/api/sessions/<autoSession>/threads/<autoThread>`.
- `curl.exe -N --max-time 300 ... http://localhost:18080/chat/stream` for manual plan gate, output `target/http-check/manual-pause.sse`.
- `curl.exe http://localhost:18080/api/sessions/<manualSession>/threads/<manualThread>` confirmed `PAUSED`.
- `curl.exe -N --max-time 300 ... http://localhost:18080/chat/resume`, output `target/http-check/manual-resume.sse`.
- `curl.exe http://localhost:18080/api/reports/<manualThread>` and `/api/sessions/<manualSession>/threads/<manualThread>`.
- `curl.exe -N --max-time 300 ... http://localhost:18080/chat/stream` for a stopped paused thread, output `target/http-check/stop-pause.sse`.
- `curl.exe -sS ... http://localhost:18080/chat/stop`, output `target/http-check/stop-response.json`.
- `curl.exe http://localhost:18080/api/sessions/<stopSession>/threads/<stopThread>` confirmed `STOPPED`.
- `Select-String` on stage run logs for `ERROR`, `Exception`, `event:error`, and `NullPointerException` returned no matches.
- `Stop-Process` for the backend process and `Get-NetTCPConnection -LocalPort 18080` confirmed `port 18080 released`.

## Verification

- Focused tests passed: `AdvancedExecutionPropertiesTest`, `ParallelExecutorNodeTest`.
- Full test suite passed: `mvn test`, 118 tests, 0 failures, 0 errors.
- Real E2E auto accepted research:
  - Thread: `stage3-auto-20260523230220`
  - Session: `stage3-session-20260523230220`
  - SSE contained `event:done`, no `event:error`, and no `parallel_executor`.
  - Report read succeeded with status `success`.
  - Session history status was `COMPLETED`.
- Real E2E manual pause and accepted resume:
  - Thread: `stage3-manual-20260523230338`
  - Session: `stage3-session-20260523230338`
  - Pause SSE contained `human_feedback.waiting`, no `event:error`, and no `parallel_executor`.
  - Pause history status was `PAUSED`.
  - Resume SSE contained `event:done`, no `event:error`, and no `parallel_executor`.
  - Final report read succeeded with status `success`.
  - Final session history status was `COMPLETED`.
- Real E2E stop regression:
  - Thread: `stage3-stop-20260523230440`
  - Session: `stage3-session-20260523230440`
  - Pause SSE contained `human_feedback.waiting`.
  - `/chat/stop` returned success.
  - Session history status was `STOPPED`.
- Backend service was stopped and port `18080` was released.

## Known Failures / Blockers

- No implementation blocker remains for this assignment-only stage.
- No upstream branch is configured for `main`.
- `target/http-check` contains many historical ignored validation artifacts; they should remain untracked and ignored.
- Older unrelated untracked `.codex` files remain in the workspace and should not be staged for this stage unless explicitly requested.
- The advanced execution plan's future true graph fan-out behavior still needs later verification. This stage intentionally avoids depending on fan-out semantics.

## Next Actions

1. Start the next stage `add-multi-researcher-executors` by adding executor-aware `researcher_0` / `researcher_1` nodes that only process steps assigned to themselves.
2. Preserve the old `researcher` node path until advanced graph routing is explicitly enabled in a later stage.
3. Add focused tests for executor-specific processing, dynamic status transitions, and event metadata with `node_name=researcher_0`, `node_type=researcher`, and `executor_id=0`.

## Open Questions

- Should future advanced routing choose one assigned executor per graph loop, or rely on Spring AI Alibaba Graph multi-edge behavior for fan-out?
- Should a later stage add a dedicated `step.assigned` stream event type, or keep it classified as `node.delta`?
- Should a separate `ResearchState.recordNodeAssigned` method replace `recordNodeStarted` once executor nodes distinguish assigned versus processing more precisely?

## Avoid / Do Not Redo

- Do not rewrite the stable event contract from stage 1.
- Do not rework the step execution state model from stage 2 unless a small missing helper is directly required for multi-executor execution.
- Do not wire the advanced execution route into the default Graph path until the explicit routing stage.
- Do not add `coder_0`, coder prompts, Docker execution, MCP tools, RAG, Redis, frontend work, or full parallel execution in the next researcher-only stage.
- Do not break old `researcher` / `processor` linear Graph behavior.
- Do not read, print, or commit API keys from `.local/model-providers.json`.
- Do not modify `deepresearch-main`; it is read-only reference material.
- Do not stage the older unrelated untracked `.codex` files unless the user explicitly asks.
- Do not leave a local backend service running after E2E.

## Resume Prompt
Resume task add-parallel-executor-assignment. Read .codex/tasks/add-parallel-executor-assignment.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
