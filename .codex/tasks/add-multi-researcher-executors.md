# Task Handoff: add-multi-researcher-executors
Updated: 2026-05-24T12:05:00+08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: b3502d3ba7168d131f65d2cf770df83415f701d3
Current Commit: b3502d3ba7168d131f65d2cf770df83415f701d3

## Project Mainline

- M-agent is a Java 17 / Maven / Spring Boot 3.4.x simplified DeepResearch-style backend under package `top.lanshan.manmu`.
- The runtime direction is a Graph-based backend using Spring AI Alibaba Graph, WebFlux SSE, PostgreSQL persistence, Docker PostgreSQL, session history, session context, and real model providers.
- The project already supports direct answer, automatic research, manual plan gate, accepted resume, rejected replan, stop, report persistence, session history, and real HTTP/SSE validation.
- The active long-running direction is to build a Graph advanced execution skeleton before adding RAG, Redis, MCP, Docker coder execution, or frontend work.
- Stages 1-3 established stable stream events, step execution ownership state, and assignment-only `parallel_executor` behavior. Stage 4 now makes assigned researcher executor nodes real while preserving the old default route.

## Stage Role in Mainline

- This is stage 4 of `.codex/graph-advanced-execution-plan.md`.
- It adds executor-aware researcher behavior for nodes such as `researcher_0` and `researcher_1`.
- It bridges the completed assignment stage to the later advanced graph wiring stage by proving that assigned research steps can be executed by named researcher nodes.

## Mainline Progression

- Stage 1, `add-stable-stream-event-contract`, stabilized SSE fields such as `sequence`, `event_type`, `node_name`, `node_type`, `executor_id`, `step_id`, `phase`, `status`, `display_title`, and `payload`.
- Stage 2, `add-step-execution-state-model`, introduced deterministic step ids, execution metadata on `ResearchStep`, `StepExecutionStatus`, and lightweight execution node state on `ResearchState`.
- Stage 3, `add-parallel-executor-assignment`, added disabled-by-default advanced execution config and an assignment-only `ParallelExecutorNode`.
- A comparison against `C:/MainData/code/Codex_project/deepresearch-main` found no major route drift in the first three stages. Differences are intentional simplifications or small future alignment points.
- This stage implemented the `researcher_n` execution half without adding coder nodes, RAG, Redis, MCP, Docker execution, frontend work, or default advanced graph routing.
- Future stages can now register or wire dynamic researcher nodes knowing that per-node ownership, dynamic status transitions, event metadata, and the legacy `researcher` compatibility path are covered by focused tests.

## Related Stage Handoffs

- Immediate upstream completed stage: `.codex/tasks/add-parallel-executor-assignment.md`.
- Earlier upstream stages: `.codex/tasks/add-step-execution-state-model.md` and `.codex/tasks/add-stable-stream-event-contract.md`.
- Planning reference: `.codex/graph-advanced-execution-plan.md`.
- Historical Graph migration references only: `.codex/graph-dynamic-orchestration-plan.md` and older Graph task handoffs under `.codex/tasks/`.
- Likely next stage after this one: `add-minimal-coder-node`.
- This stage is complete as of 2026-05-24 and should be treated as the upstream base for the next advanced execution stage.

## Goal

Add executor-aware researcher nodes so `researcher_0`, `researcher_1`, and future researcher instances can execute only the `RESEARCH` steps assigned to themselves by `parallel_executor`, while the existing `researcher` node path remains compatible.

Status: complete.

## Task Theme / User Intent

- Continue the staged advanced execution work after verifying that stages 1-3 have not drifted from `deepresearch-main`.
- Align with the main project's named executor semantics while keeping M-agent minimal and runnable.
- Make the next step small, testable, and safe: implement multi-researcher execution behavior, not graph fan-out routing or coder execution.

## Acceptance Criteria

- Preserve the existing `researcher` compatibility node for the current default Graph path.
- Add a constructor or node variant that supports an `executorId` and returns names such as `researcher_0`.
- A dynamic researcher node processes only `RESEARCH` steps whose status means the step belongs to that node, initially `assigned_researcher_<id>`.
- `researcher_1` must not process a step assigned to `researcher_0`.
- When a dynamic researcher starts work, it writes `processing_researcher_<id>`, sets timing/error metadata safely, and records node state.
- On success, it writes `completed_researcher_<id>`, stores the observation in the step, adds the observation to `ResearchState`, and records node completion.
- On failure, it writes `error_researcher_<id>`, stores a non-empty step error, records node failure, and emits a failure event with a non-empty error.
- SSE events emitted by dynamic researcher nodes include stable metadata resolving to `node_name=researcher_0`, `node_type=researcher`, `executor_id=0`, and the relevant `step_id`.
- `ResearchNodeGraphAction` and `ResearchNodeMetadata` should continue to handle dynamic node names correctly.
- Focused tests cover executor-specific selection, status transitions, event metadata, and old `researcher` compatibility behavior.
- `mvn test` passes.
- Real HTTP/SSE E2E verifies the old/default route still works and does not accidentally emit `researcher_0` or `parallel_executor` unless advanced routing is explicitly enabled in a later stage.
- Backend service is stopped after E2E and port `18080` is released.

## Scope

- Work only inside `C:/MainData/code/Codex_project/M-agent`.
- This stage may modify researcher node code, shared execution status helpers, event metadata handling, graph node registration helpers, focused tests, and this handoff.
- This stage should not wire the advanced execution route into the default Graph topology.
- This stage should not add `CoderNode`, coder prompts, Docker execution, MCP tools, RAG, Redis, or frontend behavior.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/graph`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/agent`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-multi-researcher-executors.md`

### Read-only Reference Roots

- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/graph-advanced-execution-plan.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-parallel-executor-assignment.md`
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

- Implementation is complete and ready to commit.
- The current commit before this stage commit is the completed stage 3 commit `b3502d3ba7168d131f65d2cf770df83415f701d3`.
- `ResearcherNode` now supports both the Spring-managed compatibility node named `researcher` and dynamic instances created with an executor id, such as `new ResearcherNode(agent, "0")` returning `researcher_0`.
- Dynamic researcher nodes process at most one assigned `RESEARCH` step per invocation, matching the main project's `findAssignedStep` shape.
- Dynamic researcher nodes silently emit no events when no step is assigned to them, which keeps future fan-out safe when there are more researcher nodes than ready steps.
- Dynamic researcher nodes transition assigned steps through `processing_researcher_n`, `completed_researcher_n`, and `error_researcher_n`, record node state, preserve non-empty errors, add successful observations, and emit step-level events with `step_id`.
- The compatibility `researcher` node still processes all matching non-terminal steps selected by `ResearchTeamDecision` and keeps legacy `processing`, `completed`, and `error: ...` statuses.
- `StepExecutionStatus.isAssignedTo(status, nodeName)` was added as the minimal ownership helper.
- `ResearchNodeMetadata` already supports dynamic executor node naming such as `researcher_0`; no metadata code change was required.
- The first three stages were compared with `deepresearch-main`; no major route drift was found.
- `main` has no upstream branch configured.
- Older unrelated untracked `.codex` files remain in the workspace and should be left alone.

## Completed

- Resumed from this handoff and rechecked `AGENTS.md`, git status, current branch, current commit, and scope safety before editing.
- Extended `ResearcherNode` with an additive executor-id constructor and kept the existing constructor annotated for Spring injection.
- Added dynamic researcher step selection, dynamic processing/completed/error statuses, node-state updates, observations, and step events.
- Added a dynamic no-assigned-step empty completion path to match `deepresearch-main` semantics.
- Avoided double-counting attempts when dynamic researcher execution follows `ParallelExecutorNode` assignment; old compatibility execution still increments attempts.
- Added `StepExecutionStatus.isAssignedTo(status, nodeName)`.
- Added focused tests for dynamic ownership, status transition during execution, event metadata, no-op when another researcher owns the step, dynamic failure events, and compatibility behavior.
- Ran focused tests, full Maven tests, and real HTTP/SSE regression through Docker PostgreSQL and real model provider path.
- Stopped the local backend service and confirmed port `18080` was released.

## Decisions

- Treat `deepresearch-main` as a read-only reference. Do not copy its full SmartAgent, MCP, search-filter, reflection, or streaming infrastructure into M-agent in this stage.
- Preserve the old `researcher` node path for compatibility. Dynamic researcher nodes are additive.
- Align with the main project's ownership semantics: a dynamic researcher processes only the step assigned to its own node name and returns without events when no step is assigned.
- Dynamic researcher execution processes one assigned step per invocation, not all assigned steps, to prepare for graph fan-out.
- Dynamic researcher start does not increment attempt when the assignment stage already did; this avoids counting a single advanced execution as two attempts.
- Reflection statuses such as `waiting_reflecting_<nodeName>` and `waiting_processing_<nodeName>` are not required for this stage unless a tiny helper makes future compatibility safer. Do not introduce the full reflection flow now.
- The non-blank `executionRes` skip guard was not added in this stage because the dynamic researcher ownership guard is sufficient for the assigned-status path and the default route remains unchanged.
- Keep advanced execution disabled by default until the explicit graph wiring stage.

## Evidence / References

- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/graph-advanced-execution-plan.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-parallel-executor-assignment.md`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/ResearcherNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/ParallelExecutorNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/graph/ResearchNodeGraphAction.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchNodeMetadata.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchEvent.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchStep.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchState.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/StepExecutionStatus.java`
- `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/ResearcherNode.java`
- `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/ParallelExecutorNode.java`
- `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/CoderNode.java`
- `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/ResearchTeamNode.java`
- `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/util/StateUtil.java`
- `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/util/ReflectionUtil.java`

## Files Touched

- `.codex/tasks/add-multi-researcher-executors.md`
- `src/main/java/top/lanshan/manmu/model/StepExecutionStatus.java`
- `src/main/java/top/lanshan/manmu/node/ResearcherNode.java`
- `src/test/java/top/lanshan/manmu/model/StepExecutionStatusTest.java`
- `src/test/java/top/lanshan/manmu/node/ResearcherNodeTest.java`

## Commands Run

- `Get-Content -LiteralPath 'C:\Users\20232\.codex\skills\task-handoff\SKILL.md'`
- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` returned no upstream configured.
- `git merge-base HEAD '@{upstream}'` returned no upstream configured.
- `Get-Content -LiteralPath '.codex\graph-advanced-execution-plan.md'`
- `Get-Content -LiteralPath '.codex\tasks\add-parallel-executor-assignment.md'`
- `Get-Content -LiteralPath 'AGENTS.md'`
- `rg --files src/main/java src/test/java | rg "(ResearcherNode|ParallelExecutorNode|ResearchNodeGraphAction|ResearchGraphBuilder|StepExecutionStatus|ResearchStep|ResearchState|ResearchNodeMetadata|ResearchEvent)"`
- `Get-Content -LiteralPath 'src\main\java\top\lanshan\manmu\node\ResearcherNode.java'`
- `Get-Content -LiteralPath 'src\main\java\top\lanshan\manmu\graph\ResearchNodeGraphAction.java'`
- `Get-Content -LiteralPath 'C:\MainData\code\Codex_project\deepresearch-main\src\main\java\com\alibaba\cloud\ai\example\deepresearch\node\ResearcherNode.java'`
- `Get-Content -LiteralPath 'C:\MainData\code\Codex_project\deepresearch-main\src\main\java\com\alibaba\cloud\ai\example\deepresearch\util\ReflectionUtil.java'`
- `mvn '-Dtest=ResearcherNodeTest,StepExecutionStatusTest' test`
- `mvn '-Dtest=ResearcherNodeTest,StepExecutionStatusTest,ParallelExecutorNodeTest,ResearchNodeGraphActionTest' test`
- `mvn test`
- `mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=18080"`
- `curl.exe -sS -N -H "Content-Type: application/json" --data-binary "@target/http-check/direct-request-deepseek.json" http://localhost:18080/api/research/stream --max-time 120`
- `curl.exe -sS http://localhost:18080/api/reports/http-direct-20260524115840`
- `curl.exe -sS http://localhost:18080/api/sessions/http-direct-20260524115840/threads/http-direct-20260524115840`
- `Get-NetTCPConnection -LocalPort 18080 -ErrorAction SilentlyContinue`

## Verification

- Focused tests passed: `mvn '-Dtest=ResearcherNodeTest,StepExecutionStatusTest' test`.
- Related tests passed: `mvn '-Dtest=ResearcherNodeTest,StepExecutionStatusTest,ParallelExecutorNodeTest,ResearchNodeGraphActionTest' test`.
- Full Maven test suite passed after source changes: `mvn test` with 123 tests, 0 failures, 0 errors.
- After adding one final test assertion, `mvn '-Dtest=ResearcherNodeTest' test` passed.
- Real HTTP/SSE regression used Docker PostgreSQL on localhost:5432 and backend on port 18080.
- First real HTTP attempt hit a DashScope 429 rate-limit response, then the model was switched through `/api/model/switch` to configured DeepSeek for validation.
- Successful real HTTP/SSE regression thread: `http-direct-20260524115840`.
- SSE output completed with `coordinator` and `__END__` events and `graph.completed`.
- SSE scan found `forbiddenMatches=0` for `parallel_executor|researcher_0`, confirming the default route did not accidentally emit advanced execution nodes.
- Report endpoint returned the persisted report for `http-direct-20260524115840`.
- Session history endpoint returned status `COMPLETED` with `report_thread_id=http-direct-20260524115840`.
- Backend service was stopped after E2E and `Get-NetTCPConnection -LocalPort 18080` confirmed the port was released.

## Known Failures / Blockers

- No implementation blocker is known for the next stage.
- `main` has no upstream branch configured.
- The advanced graph route is still intentionally not wired; dynamic researcher nodes are currently covered by direct node tests rather than default graph wiring.
- The current `ResearcherNode` still uses legacy status strings for the compatibility path by design. Future stages must avoid breaking this old path.
- The advanced execution plan file appears garbled when read in the current console encoding, but its task structure and stage ids are still identifiable from prior handoffs and readable identifiers.
- A real HTTP attempt with DashScope returned 429 rate limiting; DeepSeek validation succeeded. Do not treat the DashScope 429 as a code failure.

## Next Actions

1. Commit this completed stage with only the touched source/test/handoff files staged; leave older unrelated untracked `.codex` files alone.
2. Start the next stage, likely `add-minimal-coder-node`, by mirroring the dynamic researcher pattern for `coder_n` while preserving the old `processor` path.
3. In a later graph wiring stage, register dynamic researcher/coder nodes and advanced routing only behind the existing disabled-by-default advanced execution configuration.

## Open Questions

- Should the next stage be `add-minimal-coder-node` before graph wiring, as planned, or should dynamic researcher/coder registration be introduced first?
- Should `ParallelExecutorNode` attempt counting be revisited in a future cleanup to align even more closely with `deepresearch-main`, where assignment only writes status?
- Should a non-blank `executionRes` skip guard be added before advanced graph wiring, or left until reflection/retry semantics exist?

## Avoid / Do Not Redo

- Do not rewrite the stable event contract from stage 1.
- Do not rework the step execution model from stage 2 except for tiny helpers required by dynamic researchers.
- Do not redo `ParallelExecutorNode` from stage 3 except for a small, directly justified alignment guard.
- Do not wire `parallel_executor` into the default Graph path in this stage.
- Do not add `CoderNode`, coder prompts, Docker execution, MCP tools, RAG, Redis, frontend work, or full parallel graph fan-out in this stage.
- Do not break old `researcher` / `processor` linear Graph behavior.
- Do not read, print, or commit API keys from `.local/model-providers.json`.
- Do not modify `deepresearch-main`; it is read-only reference material.
- Do not stage older unrelated untracked `.codex` files unless the user explicitly asks.
- Do not leave a local backend service running after E2E.

## Resume Prompt
Resume task add-multi-researcher-executors. Read .codex/tasks/add-multi-researcher-executors.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
