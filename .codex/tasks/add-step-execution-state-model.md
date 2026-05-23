# Task Handoff: add-step-execution-state-model
Updated: 2026-05-23
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 3d4dc30caf18c082bf5fcfcc2cfb968ab8ba37d1
Current Commit: 3d4dc30caf18c082bf5fcfcc2cfb968ab8ba37d1

## Project Mainline

- M-agent is a Java 17 / Maven / Spring Boot 3.4.x simplified backend that recreates the core DeepResearch-style behavior of `C:/MainData/code/Codex_project/deepresearch-main` under package `top.lanshan.manmu`.
- The default runtime path is the Graph-based backend using Spring AI Alibaba Graph, WebFlux SSE, PostgreSQL persistence, Docker PostgreSQL, and real model providers.
- The project already supports direct answer, automatic research, manual plan gate, accepted resume, rejected replan, stop, report persistence, session history, and real HTTP/SSE validation.
- The active long-running direction is to build a Graph advanced execution skeleton before adding RAG, Redis, MCP, Docker coder execution, or frontend work.
- Stage 1, `add-stable-stream-event-contract`, established stable SSE event fields and event types. This stage adds stable step identity and execution status semantics that later `parallel_executor`, `researcher_n`, and `coder_n` stages should reuse.

## Stage Role in Mainline

- This is stage 2 of `.codex/graph-advanced-execution-plan.md`.
- It makes plan steps schedulable and traceable by stable id, owner node, attempt count, timestamps, result, error, and status.
- It prepares model and routing semantics only; it does not add the `parallel_executor` graph topology or dynamic executor nodes.

## Mainline Progression

- Stage 1 stabilized the stream envelope with `sequence`, `event_type`, `node_name`, `node_type`, `executor_id`, `step_id`, `phase`, `status`, `display_title`, `payload`, and compatibility fields.
- This stage introduced `StepExecutionStatus`, deterministic planner step ids, step execution metadata, and lightweight node execution state on `ResearchState`.
- Future stages should build assignment, retry, executor-specific status, and dynamic node events on `StepExecutionStatus` instead of reintroducing scattered prefix checks.

## Related Stage Handoffs

- Upstream completed stage: `.codex/tasks/add-stable-stream-event-contract.md`.
- Planning reference: `.codex/graph-advanced-execution-plan.md`.
- Historical reference only: `.codex/graph-dynamic-orchestration-plan.md` and older graph migration handoffs under `.codex/tasks/`.
- Likely next stage: `add-parallel-executor-assignment`.

## Goal

Upgrade the step execution state model so each plan step can be safely identified, assigned, processed, completed, retried later, and summarized by multi-executor graph nodes.

## Task Theme / User Intent

- Continue the staged advanced Graph execution work without widening into RAG, Redis, MCP, Docker execution, or frontend features.
- Align M-agent step status semantics with the important DeepResearch main project ideas while keeping the simplified backend runnable.
- Preserve existing behavior for the old linear `researcher` / `processor` path and old serialized plan statuses.

## Acceptance Criteria

- Extend `ResearchStep` with stable execution fields: `id`, `assignedNode`, `attempt`, `error`, `startedAt`, `completedAt`, `executionStatus`, and `executionRes`.
- Add a central helper, `StepExecutionStatus`, that supports `pending`, `assigned_<nodeName>`, `processing_<nodeName>`, `completed_<nodeName>`, and `error_<nodeName>`.
- Preserve compatibility with old status strings: `pending`, `processing`, `completed`, and `error: <message>`.
- Generate stable ids for planner-created steps after output mapping when ids are missing, for example `step-1`, `step-2`.
- Migrate terminal and prefix status checks in `ResearchTeamNode`, `ResearcherNode`, and `ProcessorNode` to the helper.
- Add lightweight execution state fields to `ResearchState`: `runningNodes`, `completedNodes`, `failedNodes`, and `lastAssignedNodes`.
- Focused tests cover planner step id auto-fill, dynamic assigned/processing non-terminal statuses, dynamic completed/error terminal statuses, old completed/error compatibility, and step execution JSON fields.
- `mvn test` passes.
- Real HTTP/SSE E2E confirms automatic research completes and saves a report, and manual pause plus accepted resume remains unaffected.
- Backend service is stopped after E2E and port `18080` is released.

## Scope

- Work only inside `C:/MainData/code/Codex_project/M-agent`.
- This stage touched the step model, status helper, planner output mapping, planner event snapshotting, graph node status checks, related state fields, and focused tests.
- `C:/MainData/code/Codex_project/deepresearch-main` remained read-only reference only.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/agent`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-step-execution-state-model.md`

### Read-only Reference Roots

- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/graph-advanced-execution-plan.md`
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

- Implementation, tests, real HTTP/SSE E2E, service shutdown, and handoff update are complete.
- Branch is `main`.
- No upstream branch is configured for `main`.
- Code baseline before this implementation was `3d4dc30caf18c082bf5fcfcc2cfb968ab8ba37d1`.
- Older unrelated untracked `.codex` planning/task files remain in the workspace and were not modified or staged.
- Final commit is pending at handoff update time.

## Completed

- Added `StepExecutionStatus` with dynamic node-scoped status builders and compatibility checks for legacy `completed` and `error: ...` values.
- Extended `ResearchStep` with `id`, `assignedNode`, `attempt`, `error`, `startedAt`, `completedAt`, explicit JSON properties for execution fields, and a `copy()` helper for safe event snapshots.
- Added `runningNodes`, `completedNodes`, `failedNodes`, and `lastAssignedNodes` to `ResearchState`, plus methods to record node started/completed/failed state.
- Updated `PlannerOutputMapper` to assign deterministic missing ids such as `step-1` and `step-2`.
- Updated `ResearchTeamNode`, `ResearcherNode`, and `ProcessorNode` to use `StepExecutionStatus` for terminal status checks.
- Updated `ResearcherNode` and `ProcessorNode` to record assigned node, attempt count, timestamps, error, and lightweight node state while preserving old linear `processing`, `completed`, and `error: ...` statuses.
- Updated `PlannerNode` to emit a plan snapshot so earlier `plan.generated` SSE payloads are not mutated by later step execution.
- Added focused tests for status helper behavior, planner id generation, planner snapshot isolation, dynamic terminal/non-terminal routing, node execution metadata, and execution field JSON output.

## Decisions

- Keep the old public status values for existing linear `researcher` / `processor` execution success and failure, while making the helper ready for future `completed_researcher_0` and `error_coder_0` values.
- Start `attempt` at `0` before execution and increment to `1` when the old linear node starts a step.
- Store `runningNodes`, `completedNodes`, and `failedNodes` as sets of node names; store `lastAssignedNodes` as a map from node name to step id. This is enough for the next assignment stage without forcing a full scheduler model yet.
- Serialize step execution metadata as fields inside the `step` payload (`id`, `assigned_node`, `attempt`, `error`, `started_at`, `completed_at`, `execution_res`, `execution_status`, `search_context`). Top-level `step_id` enrichment is left for a later event mapping stage.
- Fix the plan event mutation issue by snapshotting the emitted planner payload; this became visible after adding execution fields and is within this stage's event-state safety surface.
- Do not introduce `parallel_executor`, dynamic executor nodes, coder node, advanced routing, RAG, Redis, MCP, Docker executor, or frontend code in this stage.

## Evidence / References

- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/graph-advanced-execution-plan.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-stable-stream-event-contract.md`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchStep.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchState.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/StepExecutionStatus.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/agent/PlannerOutputMapper.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/PlannerNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/ResearchTeamNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/ResearcherNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/ProcessorNode.java`
- Real E2E artifacts under ignored `target/http-check/`, especially `step-state-auto-v2.sse`, `step-state-manual-dashscope-pause-v2.sse`, and `step-state-resume-accept-dashscope-v2.sse`.

## Files Touched

- `.codex/tasks/add-step-execution-state-model.md`
- `src/main/java/top/lanshan/manmu/agent/PlannerOutputMapper.java`
- `src/main/java/top/lanshan/manmu/model/ResearchState.java`
- `src/main/java/top/lanshan/manmu/model/ResearchStep.java`
- `src/main/java/top/lanshan/manmu/model/StepExecutionStatus.java`
- `src/main/java/top/lanshan/manmu/node/PlannerNode.java`
- `src/main/java/top/lanshan/manmu/node/ProcessorNode.java`
- `src/main/java/top/lanshan/manmu/node/ResearchTeamNode.java`
- `src/main/java/top/lanshan/manmu/node/ResearcherNode.java`
- `src/test/java/top/lanshan/manmu/agent/PlannerOutputMapperTest.java`
- `src/test/java/top/lanshan/manmu/model/StepExecutionStatusTest.java`
- `src/test/java/top/lanshan/manmu/node/PlannerNodeTest.java`
- `src/test/java/top/lanshan/manmu/node/ProcessorNodeTest.java`
- `src/test/java/top/lanshan/manmu/node/ResearchTeamNodeTest.java`
- `src/test/java/top/lanshan/manmu/node/ResearcherNodeTest.java`

## Commands Run

- `Get-Content -LiteralPath C:\Users\20232\.codex\skills\task-handoff\SKILL.md`
- `Get-Content -LiteralPath AGENTS.md`
- `git status --short --branch`
- `git log --oneline --decorate -8`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` returned no upstream configured.
- `Get-Content -LiteralPath .codex/graph-advanced-execution-plan.md -Encoding UTF8`
- `Get-Content` and `rg` inspections for `ResearchStep`, `ResearchState`, `PlannerOutputMapper`, `PlannerNode`, `ResearchTeamNode`, `ResearcherNode`, `ProcessorNode`, and related tests.
- `mvn '-Dtest=StepExecutionStatusTest,PlannerOutputMapperTest,ResearchTeamNodeTest,ResearcherNodeTest,ProcessorNodeTest' test`
- `mvn '-Dtest=StepExecutionStatusTest,PlannerOutputMapperTest,PlannerNodeTest,ResearchTeamNodeTest,ResearcherNodeTest,ProcessorNodeTest' test`
- `mvn test`
- `mvn -DskipTests package`
- Docker MCP `docker_container_list`, confirming `manmu-postgres` was `Up` and `healthy`.
- `java -jar target/deepresearch-mvp-0.1.0-SNAPSHOT.jar --server.port=18080`
- `curl.exe -sS http://localhost:18080/api/model/current`
- `curl.exe -sS -X POST http://localhost:18080/api/model/switch ...` using request body files in `target/http-check/`.
- `curl.exe -sS -X POST http://localhost:18080/api/model/test ...` for a short DashScope smoke call.
- `curl.exe -sS -N --max-time 240 -X POST http://localhost:18080/chat/stream ...` for automatic research.
- `curl.exe -sS -N --max-time 180 -X POST http://localhost:18080/chat/stream ...` for manual plan pause.
- `curl.exe -sS -N --max-time 240 -X POST http://localhost:18080/chat/resume ...` for accepted resume.
- `curl.exe -sS http://localhost:18080/api/reports/<thread>/exists`
- `curl.exe -sS http://localhost:18080/api/sessions/<session>/threads/<thread>`
- `Stop-Process` for the verification backend PID.
- `Get-NetTCPConnection -LocalPort 18080`
- `git diff --check`

## Verification

- Focused tests passed: 19 tests in the first focused run.
- Focused tests after planner snapshot fix passed: 21 tests with 0 failures.
- Full tests passed after implementation: `mvn test` ran 107 tests with 0 failures.
- Full tests passed after planner snapshot fix: `mvn test` ran 108 tests with 0 failures.
- Packaging passed: `mvn -DskipTests package`.
- Docker PostgreSQL was running as container `manmu-postgres`, healthy, with port `5432` mapped to localhost.
- Backend started on `http://localhost:18080` using Java 17 and connected to PostgreSQL 17.9; Flyway reported schema version 2 and no pending migrations.
- Automatic real HTTP/SSE E2E passed with `deepseek/deepseek-chat`:
  - `target/http-check/step-state-auto-v2.sse` contains `graph.completed`.
  - Planner payload contains `step-1` and `step-2` as pending snapshot fields.
  - Researcher and processor step payloads contain `assigned_node`, `attempt`, timestamps, and `execution_status=completed`.
  - `target/http-check/step-state-auto-report-exists-v2.json` reports `report_information=true`.
  - `target/http-check/step-state-auto-history-v2.json` reports `COMPLETED`.
- Manual pause plus accepted resume real HTTP/SSE E2E passed with `dashscope/qwen-turbo-2025-04-28`:
  - `target/http-check/step-state-manual-dashscope-pause-v2.sse` contains `human_feedback.waiting`, `step-1`, and `execution_status=pending`.
  - `target/http-check/step-state-resume-accept-dashscope-v2.sse` contains `human_feedback.accepted`, `graph.completed`, `step-1`, `assigned_node=processor`, and `execution_status=completed`.
  - `target/http-check/step-state-manual-dashscope-report-exists-v2.json` reports `report_information=true`.
  - `target/http-check/step-state-manual-dashscope-history-v2.json` reports `COMPLETED`.
- A first accepted resume attempt with `deepseek/deepseek-chat` failed with `ReadTimeoutException`; retrying the same validation shape with DashScope passed. The failure is recorded as provider/network instability, not a code regression.
- Backend service was stopped after E2E and `Get-NetTCPConnection -LocalPort 18080` returned `PORT_18080_RELEASED`.
- `git diff --check` passed.

## Known Failures / Blockers

- No blocker remains for this stage.
- No upstream branch is configured for `main`.
- Older unrelated untracked `.codex` planning/task files remain in the workspace and should not be staged unless the user asks.
- A DeepSeek accepted-resume E2E attempt failed with `ReadTimeoutException`; DashScope accepted-resume E2E passed immediately afterward. Future E2E can switch providers through `/api/model/switch` if one provider is slow or rate-limited.
- Spring AI Alibaba Graph still logs shallow-copy fallback messages for `ResearchState` and `ResearchEvent` with `Instant`; this existed before the stage and tests/E2E still passed.

## Next Actions

1. Commit the completed stage with a Chinese message, staging only the files listed in `Files Touched` and leaving older unrelated untracked `.codex` files alone.
2. Resume the next stage `add-parallel-executor-assignment`; add `ParallelExecutorNode` that uses `StepExecutionStatus.assigned(nodeName)`, stable `ResearchStep.id`, and `ResearchState.lastAssignedNodes`.
3. In the next stage, decide whether event top-level `step_id` should be derived from step payloads when emitting `step.assigned` / executor timeline events.

## Open Questions

- Should future executor events also set top-level `ResearchEvent.stepId`, or is nested `payload.step.id` enough until the frontend timeline stage?
- Should the next assignment stage keep old linear `completed` statuses for compatibility or begin using dynamic statuses only inside `parallel_executor`-owned flows?
- Should graph state copying be revisited later to reduce shallow-copy log noise once advanced execution needs stronger state isolation?

## Avoid / Do Not Redo

- Do not rewrite the stable event contract from stage 1.
- Do not add `parallel_executor`, `researcher_n`, `coder_n`, RAG, Redis, MCP, Docker executor, or frontend code in this completed stage.
- Do not break old `pending`, `processing`, `completed`, or `error: <message>` status compatibility.
- Do not depend on exact LLM natural-language output in tests.
- Do not read, print, or commit API keys from `.local/model-providers.json`.
- Do not modify `deepresearch-main`; it is read-only reference material.
- Do not stage the older untracked `.codex` files unless the user explicitly asks.
- Do not leave a local backend service running after E2E.

## Resume Prompt
Resume task add-step-execution-state-model. Read .codex/tasks/add-step-execution-state-model.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
