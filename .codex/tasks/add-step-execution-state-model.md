# Task Handoff: add-step-execution-state-model
Updated: 2026-05-23
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 712e83f46c69358b748670265b2efe2ac930b56a
Current Commit: 712e83f46c69358b748670265b2efe2ac930b56a

## Project Mainline

- M-agent is a Java 17 / Maven / Spring Boot 3.4.x simplified backend that recreates the core DeepResearch-style behavior of `C:/MainData/code/Codex_project/deepresearch-main` under package `top.lanshan.manmu`.
- The default runtime path is the Graph-based backend using Spring AI Alibaba Graph, WebFlux SSE, PostgreSQL persistence, and real model providers.
- The project already supports direct answer, automatic research, manual plan gate, accepted resume, rejected replan, stop, report persistence, session history, and real HTTP/SSE validation.
- The active long-running direction is to build a Graph advanced execution skeleton before adding RAG, Redis, MCP, Docker coder execution, or frontend work.
- The prior stage `add-stable-stream-event-contract` was committed as `712e83f46c69358b748670265b2efe2ac930b56a` and established stable event fields and event types for future step and executor timeline events.

## Stage Role in Mainline

- This is stage 2 of `.codex/graph-advanced-execution-plan.md`.
- It upgrades plan step identity and execution status semantics so later stages can add `parallel_executor`, `researcher_n`, and `coder_n` without ambiguous string checks.
- It should prepare the model and routing logic only; it should not add the parallel executor graph topology yet.

## Mainline Progression

- Stage 1 stabilized SSE output with `sequence`, `event_type`, `node_name`, `node_type`, `executor_id`, `step_id`, `phase`, `status`, `display_title`, `payload`, and compatibility fields.
- This stage should make every `ResearchStep` schedulable and traceable by stable id, assigned node, attempt count, timestamps, status, result, and error.
- Future stages should build assignment and dynamic executor nodes on the new `StepExecutionStatus` helper instead of reintroducing duplicated prefix checks in individual nodes.

## Related Stage Handoffs

- Upstream completed stage: `.codex/tasks/add-stable-stream-event-contract.md`.
- Planning reference: `.codex/graph-advanced-execution-plan.md`.
- Historical reference only: `.codex/graph-dynamic-orchestration-plan.md` and older graph migration handoffs under `.codex/tasks/`.
- Likely next stage after this one: `add-parallel-executor-assignment`.

## Goal

Upgrade the step execution state model so each plan step can be safely identified, assigned, processed, completed, retried later, and summarized by multi-executor graph nodes.

## Task Theme / User Intent

- Continue the staged advanced Graph execution work without widening into RAG, Redis, MCP, Docker execution, or frontend features.
- Align M-agent step status semantics with the important DeepResearch main project ideas while keeping the simplified backend runnable.
- Preserve existing behavior for the old linear `researcher` / `processor` path and old serialized plan statuses.

## Acceptance Criteria

- Extend `ResearchStep` with stable execution fields: `id`, `assignedNode`, `attempt`, `error`, `startedAt`, `completedAt`, `executionStatus`, and `executionRes`.
- Add a central helper, likely `StepExecutionStatus`, that supports:
  - `pending`
  - `assigned_<nodeName>`
  - `processing_<nodeName>`
  - `completed_<nodeName>`
  - `error_<nodeName>`
- Preserve compatibility with old status strings: `pending`, `processing`, `completed`, and `error: <message>`.
- Generate stable ids for planner-created steps after output mapping when ids are missing, for example `step-1`, `step-2`.
- Migrate terminal and prefix status checks in `ResearchTeamNode`, `ResearcherNode`, and `ProcessorNode` to the helper.
- Add lightweight execution state fields to `ResearchState`: `runningNodes`, `completedNodes`, `failedNodes`, and `lastAssignedNodes`, unless current code inspection shows a narrower equivalent is safer.
- Focused tests cover planner step id auto-fill, dynamic assigned/processing non-terminal statuses, dynamic completed/error terminal statuses, and old completed/error compatibility.
- `mvn test` passes.
- Real HTTP/SSE E2E confirms automatic research completes and saves a report, and manual pause plus accepted resume remains unaffected.
- Backend service is stopped after E2E and port `18080` is released.

## Scope

- Work only inside `C:/MainData/code/Codex_project/M-agent`.
- This stage should touch the step model, status helper, planner output mapping, graph node status checks, related state fields, and tests.
- Use `C:/MainData/code/Codex_project/deepresearch-main` as read-only reference only.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/agent`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner` only if stable `step_id` propagation requires runner-level event enrichment.
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

- The stage has not started yet.
- Branch is `main`.
- No upstream branch is configured for `main`.
- Current code baseline is commit `712e83f46c69358b748670265b2efe2ac930b56a`.
- `git status --short --branch` shows only older unrelated untracked `.codex` planning/task files before this handoff file is created.
- `ResearchStep` currently has `title`, `description`, `needWebSearch`, `stepType`, `executionRes`, `executionStatus`, and `searchContext`; constructors use old pending/completed/error status strings.
- `PlannerOutputMapper` currently creates steps without ids and appends a processing step when needed.
- `ResearchTeamNode`, `ResearcherNode`, and `ProcessorNode` currently duplicate terminal status logic with `startsWith("completed")` and `startsWith("error")`.

## Completed

- Stage 1 stable streaming event contract is complete and committed.
- This handoff establishes the next stage context and scope.

## Decisions

- Keep the old string status surface compatible while adding dynamic node-scoped status helpers.
- Prefer adding a small model-level helper over scattering string parsing in nodes.
- Generate deterministic planner step ids locally; do not require LLM output to provide ids.
- Do not introduce `parallel_executor`, dynamic executor nodes, coder node, or advanced graph routing in this stage.
- Keep E2E assertions focused on lifecycle, persistence, and status compatibility rather than exact natural-language model output.

## Evidence / References

- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/graph-advanced-execution-plan.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-stable-stream-event-contract.md`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchStep.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchState.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/agent/PlannerOutputMapper.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/ResearchTeamNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/ResearcherNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/ProcessorNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/agent/PlannerOutputMapperTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/node/ResearchTeamNodeTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/node/ResearcherNodeTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/node/ProcessorNodeTest.java`

## Files Touched

- `.codex/tasks/add-step-execution-state-model.md`

## Commands Run

- `Get-Content -Raw C:\Users\20232\.codex\skills\task-handoff\SKILL.md`
- `Get-Content -Raw AGENTS.md`
- `git status --short --branch`
- `git rev-parse HEAD`
- `git diff --stat`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` returned no upstream configured.
- `git log --oneline --decorate -5`
- `Get-Content -Raw .codex/tasks/add-stable-stream-event-contract.md`
- `Get-Content -Raw .codex/graph-advanced-execution-plan.md`
- `rg --files src/main/java src/test/java | rg "ResearchStep|ResearchState|ResearchTeamNode|ResearcherNode|ProcessorNode|Planner|Plan"`
- `rg "class ResearchStep|record ResearchStep|enum ResearchStep|ResearchStep" src/main/java/top/lanshan/manmu src/test/java/top/lanshan/manmu`
- `rg "status\(|getStatus|setStatus|completed|pending|error:" src/main/java/top/lanshan/manmu src/test/java/top/lanshan/manmu -g "*.java"`
- `Get-Content -Raw src/main/java/top/lanshan/manmu/model/ResearchStep.java`
- `Get-Content -Raw src/main/java/top/lanshan/manmu/model/ResearchState.java`
- `Get-Content -Raw src/main/java/top/lanshan/manmu/node/ResearchTeamNode.java`
- `Get-Content -Raw src/main/java/top/lanshan/manmu/agent/PlannerOutputMapper.java`

## Verification

- Not started for this stage.
- Inherited previous stage verification from `add-stable-stream-event-contract`: focused tests passed, full `mvn test` passed, real HTTP/SSE E2E passed for direct answer, auto accepted research, manual pause, stop of paused thread, persistence checks, and service shutdown with port `18080` released.

## Known Failures / Blockers

- No blocker is known before implementation starts.
- No upstream branch is configured for `main`.
- `.codex/graph-advanced-execution-plan.md` displayed with mojibake in the current PowerShell session; rely on stable handoff content and targeted source inspection if the terminal encoding remains wrong.
- Older unrelated untracked `.codex` planning/task files remain in the workspace and should not be modified or staged.

## Next Actions

1. Inspect `ResearchStep`, `ResearchState`, `PlannerOutputMapper`, `ResearchTeamNode`, `ResearcherNode`, `ProcessorNode`, and their focused tests; then add `StepExecutionStatus` and extend `ResearchStep` with backward-compatible constructors and JSON fields.
2. Generate missing step ids in planner output mapping, migrate node terminal/status checks to the helper, add lightweight execution state fields to `ResearchState`, and add focused tests for dynamic and legacy statuses.
3. Run focused tests, `mvn test`, package/start the backend, perform real HTTP/SSE E2E for auto research and manual pause plus accepted resume, stop the service, confirm port `18080` is released, commit with a Chinese message, and update this handoff before stopping.

## Open Questions

- Should `ResearchStep.id` be serialized as `id` only, or should a future response also expose it as `step_id` through event payload conversion?
- Should `attempt` start at `0` before assignment or `1` at first assignment? Prefer the smallest behavior that keeps current tests stable.
- Should `runningNodes`, `completedNodes`, `failedNodes`, and `lastAssignedNodes` be simple lists/sets of node names, or maps keyed by node name to step id? Inspect expected next-stage assignment needs before deciding.

## Avoid / Do Not Redo

- Do not rewrite the stable event contract from stage 1.
- Do not add `parallel_executor`, `researcher_n`, `coder_n`, RAG, Redis, MCP, Docker executor, or frontend code in this stage.
- Do not break old `pending`, `processing`, `completed`, or `error: <message>` status compatibility.
- Do not depend on exact LLM natural-language output in tests.
- Do not read, print, or commit API keys from `.local/model-providers.json`.
- Do not modify `deepresearch-main`; it is read-only reference material.
- Do not stage the older untracked `.codex` files unless the user explicitly asks.
- Do not leave a local backend service running after E2E.

## Resume Prompt
Resume task add-step-execution-state-model. Read .codex/tasks/add-step-execution-state-model.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
