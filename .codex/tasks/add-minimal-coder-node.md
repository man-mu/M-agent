# Task Handoff: add-minimal-coder-node
Updated: 2026-05-24T12:38:43+08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: fa52ebf4eeaf57f77683d4dc33295bf5eb25748e
Current Commit: 916d6cd73f6f191278c19939c294fd56f2cf399b

## Project Mainline

- M-agent is a Java 17 / Maven / Spring Boot 3.4.x simplified DeepResearch-style backend under package `top.lanshan.manmu`.
- The runtime direction is a Graph-based backend using Spring AI Alibaba Graph, WebFlux SSE, PostgreSQL persistence, Docker PostgreSQL, session history, session context, and real model providers.
- The project already supports direct answer, automatic research, manual plan gate, accepted resume, rejected replan, stop, report persistence, session history, session context, stable stream events, step execution metadata, assignment-only `parallel_executor`, and executor-aware dynamic researcher nodes.
- The active long-running direction is to build a minimal Graph advanced execution skeleton before adding RAG, Redis, MCP, Docker coder execution, or frontend work.
- The minimal dynamic `coder_n` execution half for `PROCESSING` steps is now implemented and verified while preserving the current default linear `processor` route.

## Stage Role in Mainline

- This is stage 5 of `.codex/graph-advanced-execution-plan.md`: `add-minimal-coder-node`.
- It exists because stage 3 can already assign `PROCESSING` steps to `coder_n`, and stage 4 proved assigned `RESEARCH` steps can be executed by named `researcher_n` nodes.
- This stage made assigned `PROCESSING` steps executable by named coder nodes without introducing real code execution, Docker sandboxes, MCP tools, RAG, Redis, or default advanced graph routing.

## Mainline Progression

- Stage 1, `add-stable-stream-event-contract`, stabilized SSE fields such as `sequence`, `event_type`, `node_name`, `node_type`, `executor_id`, `step_id`, `phase`, `status`, `display_title`, and `payload`.
- Stage 2, `add-step-execution-state-model`, introduced deterministic step ids, execution metadata on `ResearchStep`, `StepExecutionStatus`, and lightweight execution node state on `ResearchState`.
- Stage 3, `add-parallel-executor-assignment`, added disabled-by-default advanced execution config and assignment-only `ParallelExecutorNode`.
- Stage 4, `add-multi-researcher-executors`, added dynamic `researcher_n` execution behavior while preserving the legacy `researcher` compatibility path.
- This stage added dynamic `coder_n` behavior as a minimal named processor for `PROCESSING` steps. The next session will open stage 6, `wire-advanced-execution-graph`, to connect `parallel_executor -> researcher_n/coder_n -> research_team` behind an explicit advanced-execution switch.

## Related Stage Handoffs

- Immediate upstream completed stage: `.codex/tasks/add-multi-researcher-executors.md`.
- Earlier upstream stages: `.codex/tasks/add-parallel-executor-assignment.md`, `.codex/tasks/add-step-execution-state-model.md`, and `.codex/tasks/add-stable-stream-event-contract.md`.
- Planning reference: `.codex/graph-advanced-execution-plan.md`.
- Historical Graph migration references only: `.codex/graph-dynamic-orchestration-plan.md` and older Graph task handoffs under `.codex/tasks/`.
- Next stage after this one: `wire-advanced-execution-graph`.

## Goal

Add a minimal executor-aware coder node so `coder_0`, `coder_1`, and future coder instances can execute only the `PROCESSING` steps assigned to themselves by `parallel_executor`, while the existing `processor` node path remains compatible. This goal is implemented and verified.

## Task Theme / User Intent

- Continue the staged advanced execution work in the new session with a narrow, safe implementation task.
- Clarify that this stage's `coder` is not a real code-writing or Docker-executing agent. It is a minimal named processing executor aligned with `deepresearch-main` semantics, where `PROCESSING` steps are executed by `coder_n`.
- Keep M-agent minimal and runnable: use real model calls through the existing production path, but do not introduce Docker code execution, MCP tool callbacks, RAG, Redis, frontend behavior, or full graph fan-out.

## Acceptance Criteria

- Preserve the existing `processor` compatibility node for the current default Graph path.
- Add a `CoderNode` or equivalent node variant that supports an `executorId` and returns names such as `coder_0`.
- Prefer reusing the existing `ProcessorAgent` for the minimal implementation unless the codebase shape clearly benefits from a tiny `CoderAgent` wrapper.
- A dynamic coder node processes only `PROCESSING` steps whose status means the step belongs to that node, initially `assigned_coder_<id>`.
- `coder_1` must not process a step assigned to `coder_0`.
- `coder_0` must not process a `RESEARCH` step, even if it is assigned with `assigned_coder_0`.
- When a dynamic coder starts work, it writes `processing_coder_<id>`, sets timing/error metadata safely, and records node state.
- On success, it writes `completed_coder_<id>`, stores the processing result in the step, adds the result to `ResearchState.observations`, and records node completion.
- On failure, it writes `error_coder_<id>`, stores a non-empty step error, records node failure, and emits a failure event with a non-empty error.
- Dynamic coder step events include stable metadata resolving through `withSequence` to `node_name=coder_0`, `node_type=coder`, `executor_id=0`, and the relevant `step_id`.
- Dynamic coder should emit no events when no step is assigned to it, matching the dynamic researcher no-op shape and keeping future fan-out safe.
- Avoid double-counting attempts when dynamic coder execution follows `ParallelExecutorNode` assignment; old `processor` compatibility execution should keep its existing attempt behavior unless directly justified.
- Focused tests cover executor-specific selection, wrong-type skip, status transitions, event metadata, failure fallback, no-assigned-step no-op, and old `processor` compatibility behavior.
- `mvn test` passes.
- Real HTTP/SSE E2E verifies the old/default route still works and does not accidentally emit `coder_0`, `researcher_0`, or `parallel_executor` unless advanced routing is explicitly enabled in a later stage.
- Backend service is stopped after E2E and port `18080` is released.

## Scope

- Work only inside `C:/MainData/code/Codex_project/M-agent`.
- This stage may modify processing/coder node code, shared execution status helpers, model classes if a tiny helper is needed, graph node metadata if missing, focused tests, and this handoff.
- This stage should not wire the advanced execution route into the default Graph topology.
- This stage should not add real code-writing behavior, Docker execution, MCP tools, RAG, Redis, frontend behavior, or a full prompt/tooling copy from `deepresearch-main`.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/agent`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources/prompts`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-minimal-coder-node.md`

### Read-only Reference Roots

- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/graph-advanced-execution-plan.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-multi-researcher-executors.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-parallel-executor-assignment.md`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/ResearcherNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/ProcessorNode.java`
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

- The upstream stage `add-multi-researcher-executors` is committed at `fa52ebf4eeaf57f77683d4dc33295bf5eb25748e`.
- This completed stage is committed at `916d6cd73f6f191278c19939c294fd56f2cf399b` with message `添加最小动态 coder 节点`.
- This stage now has an additive `CoderNode` implementation plus focused tests.
- `CoderNode` intentionally is not a Spring `@Component`, so the existing `ResearchGraphBuilder(List<ResearchNode>)` default node collection does not add it to the current linear graph route before a later wiring stage.
- `main` has no upstream branch configured.
- Older unrelated untracked `.codex` files remain in the workspace and should be left alone.
- `ParallelExecutorNode` already assigns `PROCESSING` steps to `coder_n` after all `RESEARCH` steps are terminal, and dynamic coder execution now exists for direct/future graph invocation.
- `ProcessorNode` is the current compatibility node for `PROCESSING` steps in the default linear Graph route. It executes all non-terminal processing steps selected by `ResearchTeamDecision`, writes legacy statuses, and calls `ProcessorAgent`.
- `ResearcherNode` is the best local implementation pattern for this stage: additive executor constructor, no-op when no assigned step exists, one assigned step per invocation, dynamic statuses, step-level events with `step_id`, and compatibility behavior retained.
- `ResearchNodeMetadata` already recognizes `coder_n` dynamic names and should produce `node_type=coder` and `executor_id=<n>`.

## Completed

- Created this resume-ready handoff and updated it after implementation.
- Added `src/main/java/top/lanshan/manmu/node/CoderNode.java`.
- Added `src/test/java/top/lanshan/manmu/node/CoderNodeTest.java`.
- Verified focused node behavior, full Maven tests, and real HTTP/SSE default-route regression against Docker PostgreSQL and DeepSeek.
- Committed this stage as `916d6cd 添加最小动态 coder 节点`.

## Decisions

- Treat `deepresearch-main` as read-only reference. Do not copy its full MCP, reflection, streaming, or tool-callback infrastructure.
- Implemented the minimal coder by mirroring the just-completed dynamic researcher shape.
- Kept `ProcessorNode` unchanged for default compatibility and added `CoderNode` as an additive dynamic executor.
- Kept `CoderNode` out of Spring component scanning for this stage, because the current graph builder auto-collects `ResearchNode` beans by name and this stage must not wire advanced routing into the default graph.
- A dynamic coder should process one assigned `PROCESSING` step per invocation and emit no events if no step belongs to it.
- Do not add Docker executor, MCP callbacks, RAG, Redis, frontend work, or default graph wiring in this stage.
- Real model usage should stay on the existing `ProcessorAgent` production path if possible, avoiding mock/fallback behavior.

## Evidence / References

- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/graph-advanced-execution-plan.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-multi-researcher-executors.md`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/ResearcherNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/ProcessorNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/ParallelExecutorNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/agent/ProcessorAgent.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/StepExecutionStatus.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchNodeMetadata.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/node/ResearcherNodeTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/node/ProcessorNodeTest.java`
- `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/CoderNode.java`

## Files Touched

- `.codex/tasks/add-minimal-coder-node.md`
- `src/main/java/top/lanshan/manmu/node/CoderNode.java`
- `src/test/java/top/lanshan/manmu/node/CoderNodeTest.java`

## Commands Run

- `Get-Content -Raw C:\Users\20232\.codex\skills\task-handoff\SKILL.md`
- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git rev-parse HEAD`
- `Get-Content -Raw AGENTS.md`
- `Get-Content -Raw .codex\tasks\add-multi-researcher-executors.md`
- `Get-Content -Raw src\main\java\top\lanshan\manmu\node\ProcessorNode.java`
- `Get-Content -Raw src\test\java\top\lanshan\manmu\node\ProcessorNodeTest.java`
- `Get-Content -Raw src\main\java\top\lanshan\manmu\agent\ProcessorAgent.java`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` returned no upstream configured.
- `git diff --name-only`
- `git merge-base HEAD '@{upstream}'` returned no upstream configured.
- `Get-Content -Raw .codex\graph-advanced-execution-plan.md`
- `Get-Content -Raw C:\MainData\code\Codex_project\deepresearch-main\src\main\java\com\alibaba\cloud\ai\example\deepresearch\node\CoderNode.java`
- `Get-Content -Raw src\main\java\top\lanshan\manmu\node\ResearcherNode.java`
- `Get-Content -Raw src\main\java\top\lanshan\manmu\node\ParallelExecutorNode.java`

## Verification

- Focused tests passed: `mvn '-Dtest=CoderNodeTest,ProcessorNodeTest,ResearcherNodeTest,ParallelExecutorNodeTest,ResearchTeamNodeTest' test`.
- Full test suite passed: `mvn test` with 127 tests, 0 failures, 0 errors.
- Real HTTP/SSE regression passed on port `18080` using Docker PostgreSQL `manmu-postgres` and DeepSeek: `/api/research/stream` completed with `event:done`, report persistence was readable, and session history status was `COMPLETED`.
- The real SSE response did not contain `coder_0`, `researcher_0`, or `parallel_executor`; the default route still used the legacy `processor` path.
- The backend service started for E2E was stopped, and port `18080` was confirmed released.

## Known Failures / Blockers

- No implementation blocker is known.
- `main` has no upstream branch configured.
- The advanced graph route is still intentionally not wired. The new dynamic coder node is unit-tested but not registered in the default Spring graph path.
- The current `ProcessorNode` uses legacy status strings for the compatibility path. The next session must avoid breaking this old path while adding dynamic coder status behavior.
- The advanced execution plan file appears garbled when read in the current console encoding, but its task structure and stage ids are identifiable.
- DashScope may return 429 during real HTTP/SSE validation; project instructions allow temporarily switching to DeepSeek through `/api/model/switch` when existing keys are configured.

## Next Actions

1. In a new session, create or resume `.codex/tasks/wire-advanced-execution-graph.md` and restore the mainline from this handoff plus `.codex/graph-advanced-execution-plan.md`.
2. Implement stage 6 by deciding how to instantiate/register `researcher_n` and `coder_n` and wire `parallel_executor -> executor -> research_team` only when `advanced-execution.enabled=true`.
3. Preserve the verified default-route regression: no `coder_0`, `researcher_0`, or `parallel_executor` events unless the advanced graph is explicitly selected.

## Open Questions

- Resolved: use a new `CoderNode` class backed by `ProcessorAgent`.
- Resolved: do not introduce a `CoderAgent` interface yet.
- Resolved: do not add `src/main/resources/prompts/coder.md` yet; use the existing processor prompt through `ProcessorAgent`.
- Open for next stage: how to instantiate and register dynamic executor nodes for advanced graph routing without disturbing the current default route.
- The user explicitly plans to start stage 6 in a new conversation.

## Avoid / Do Not Redo

- Do not rewrite the stable event contract from stage 1.
- Do not rework the step execution model from stage 2 except for tiny helpers required by dynamic coder behavior.
- Do not redo `ParallelExecutorNode` from stage 3 except for a directly justified tiny alignment fix.
- Do not redo dynamic `ResearcherNode` from stage 4.
- Do not wire `parallel_executor` into the default Graph path in this stage.
- Do not make `coder_n` perform real code generation, code execution, Docker execution, MCP tool calls, file writes, RAG, Redis, or frontend behavior in this stage.
- Do not break old `researcher` / `processor` linear Graph behavior.
- Do not read, print, or commit API keys from `.local/model-providers.json`.
- Do not modify `deepresearch-main`; it is read-only reference material.
- Do not stage older unrelated untracked `.codex` files unless the user explicitly asks.
- Do not leave a local backend service running after E2E.

## Resume Prompt
Resume task add-minimal-coder-node. Read .codex/tasks/add-minimal-coder-node.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
