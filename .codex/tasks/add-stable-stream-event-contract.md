# Task Handoff: add-stable-stream-event-contract
Updated: 2026-05-23
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 8ee4248d513820d8f59e3ba6faaa2dd12c112977
Current Commit: 8ee4248d513820d8f59e3ba6faaa2dd12c112977

## Project Mainline

- M-agent is a Java 17 / Maven / Spring Boot 3.4.x simplified backend that recreates the core behavior of `C:/MainData/code/Codex_project/deepresearch-main` under package `top.lanshan.manmu`.
- The project already migrated from the old simple runner to a default Graph-based backend using Spring AI Alibaba Graph.
- The current Graph path supports direct answer, automatic research, manual plan gate, accepted resume, rejected replan, stop, report persistence, session history, and real HTTP/SSE validation.
- The next mainline goal is to build the Graph advanced execution skeleton before adding RAG, Redis, MCP, Docker coder execution, or a full frontend.
- This skeleton should align with `deepresearch-main` responsibilities: stable streaming events first, then richer step execution state, then `parallel_executor`, `researcher_n`, and `coder_n`.

## Stage Role in Mainline

- This stage is the first step of `.codex/graph-advanced-execution-plan.md`.
- It stabilizes the SSE event contract so later advanced nodes can emit predictable, frontend-friendly events without forcing repeated controller/API rewrites.
- It must not introduce parallel executor, coder node, RAG, Redis, MCP, Docker execution, or frontend code.

## Mainline Progression

- Previous stage created and committed the plan at `.codex/graph-advanced-execution-plan.md` in commit `8ee4248`.
- This stage should turn the plan's event envelope into code while preserving current behavior.
- Future stages should build on this event contract for step assignment, executor metadata, and node timeline rendering.

## Related Stage Handoffs

- Existing older graph migration handoffs are present under `.codex/tasks/`, but many are currently untracked in this workspace.
- This stage inherits from:
  - `.codex/graph-advanced-execution-plan.md`
  - `.codex/graph-dynamic-orchestration-plan.md` as historical context only
- New stage sequence expected after this:
  - `add-step-execution-state-model`
  - `add-parallel-executor-assignment`
  - `add-multi-researcher-executors`
  - `add-minimal-coder-node`
  - `wire-advanced-execution-graph`
  - `enable-advanced-execution-default`
  - `review-advanced-execution-readiness`

## Goal

Implement the first advanced execution stage: a stable streaming event contract for Graph research runs.

## Task Theme / User Intent

- The user wants to continue recreating a simplified version of `deepresearch-main`, but with staged, reviewable work.
- This stage should make the WebFlux SSE output stable enough for future frontend timeline rendering and for advanced nodes such as `parallel_executor`, `researcher_n`, and `coder_n`.
- The implementation should be conservative, aligned with existing M-agent code, and verified through real HTTP/SSE calls.

## Acceptance Criteria

- Add or extend a stable event envelope with at least these concepts:
  - `sequence`
  - `event_type`
  - `node_name`
  - `node_type`
  - `executor_id`
  - `step_id`
  - `phase`
  - `status`
  - `display_title`
  - `content`
  - `payload`
  - `site_information`
  - `done`
  - `timestamp`
  - `graph_id`
- Preserve compatibility for `/chat/stream` consumers. Prefer adding fields to the existing response shape or wrapping conversion internally instead of breaking current JSON.
- Define stable event types for the existing lifecycle, at least:
  - `graph.started`
  - `node.started`
  - `node.delta`
  - `node.completed`
  - `node.failed`
  - `plan.generated`
  - `human_feedback.waiting`
  - `human_feedback.accepted`
  - `human_feedback.rejected`
  - `report.completed`
  - `graph.completed`
  - `graph.stopped`
  - `graph.failed`
- Maintain per-thread increasing `sequence` values in `GraphResearchRunner` or a small nearby helper.
- Centralize node display title logic instead of letting `ChatController` keep growing hardcoded switch logic.
- Add focused tests for:
  - direct answer event envelope
  - auto research sequence increment
  - human feedback waiting event type
  - stop event type
- Run `mvn test` if shared API/controller/runner behavior is touched.
- Run real HTTP/SSE E2E for:
  - `/chat/stream` direct answer
  - `/chat/stream` auto accepted research
  - `/chat/stream` manual pause
  - `/chat/stop` paused thread
- Stop the backend service after E2E and confirm port `18080` is released.
- Commit the completed stage with a Chinese commit message, suggested: `稳定图编排流式事件协议`.

## Scope

- Write scope is limited to the M-agent project at `C:/MainData/code/Codex_project/M-agent`.
- Main code areas likely involved:
  - `src/main/java/top/lanshan/manmu/model`
  - `src/main/java/top/lanshan/manmu/api`
  - `src/main/java/top/lanshan/manmu/runner`
  - `src/main/java/top/lanshan/manmu/graph`
  - `src/test/java/top/lanshan/manmu`
  - `AGENTS.md` only if a new pitfall is discovered
- Read-only reference root:
  - `C:/MainData/code/Codex_project/deepresearch-main`

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources` only if event display metadata or prompts need a small compatible adjustment
- `C:/MainData/code/Codex_project/M-agent/AGENTS.md` only for real pitfalls discovered during this stage
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-stable-stream-event-contract.md` when pausing/updating this handoff

### Read-only Reference Roots

- `C:/MainData/code/Codex_project/M-agent/.codex/graph-advanced-execution-plan.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/graph-dynamic-orchestration-plan.md`
- `C:/MainData/code/Codex_project/deepresearch-main`
- `C:/Users/20232/.codex/skills/task-handoff/SKILL.md`

### Forbidden Write Roots

- `C:/MainData/code/Codex_project/deepresearch-main`
- `C:/MainData/code/Codex_project/M-agent/.local`
- `C:/MainData/code/Codex_project/M-agent/target`
- `C:/MainData/code/Codex_project/M-agent/.idea`
- `C:/MainData/code/Codex_project/M-agent/.claude`
- Existing untracked older `.codex/tasks/*.md` files unless the user explicitly asks to curate or commit them

## Current State

- Current commit is `8ee4248` (`制定高级图执行骨架计划`).
- Branch is `main`.
- No upstream branch is configured.
- `git diff --stat` and `git diff --name-only` are empty at handoff creation.
- `git status --short` shows only older untracked planning/task files:
  - `.codex/graph-dynamic-orchestration-plan.md`
  - `.codex/tasks/add-graph-auto-research-runner.md`
  - `.codex/tasks/add-graph-human-feedback-accept-resume.md`
  - `.codex/tasks/add-graph-node-adapter.md`
  - `.codex/tasks/add-graph-state-adapter.md`
  - `.codex/tasks/add-research-runner-interface.md`
  - `.codex/tasks/pre-graph-core-nodes.md`
  - `.codex/tasks/switch-default-runner-to-graph.md`
- The new handoff file itself is being added for this task.

## Completed

- Created and committed `.codex/graph-advanced-execution-plan.md` in commit `8ee4248`.
- Identified stage 1 as `add-stable-stream-event-contract`.
- Read the task-handoff skill instructions and project `AGENTS.md`.
- Captured git snapshot for the new session.

## Decisions

- Stage 1 should stabilize event shape before any step-state or graph-routing changes.
- Do not introduce advanced execution nodes in this stage.
- Preserve `/chat/stream` compatibility.
- Handoff files must be English, even though project docs and final user responses are Chinese.
- Project plan documents remain Chinese.

## Evidence / References

- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/graph-advanced-execution-plan.md`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/api/ChatController.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ChatStreamResponse.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchEvent.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/GraphResearchRunner.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/graph/ResearchGraphBuilder.java`
- `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/config/DeepResearchConfiguration.java`
- `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/model/enums/NodeNameEnum.java`
- `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/model/enums/StreamNodePrefixEnum.java`

## Files Touched

- `.codex/tasks/add-stable-stream-event-contract.md` created for this handoff.

## Commands Run

- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name @{upstream}` failed because no upstream is configured.
- `git merge-base HEAD @{upstream}` failed because no upstream is configured.
- `Get-Content -Encoding UTF8 -Raw AGENTS.md`
- `Get-Content -Encoding UTF8 -Raw .codex/graph-advanced-execution-plan.md`

## Verification

- No implementation verification has run for this stage yet.
- Prior stage committed the plan only; stage 1 development should run focused tests, `mvn test` if shared behavior changes, and real HTTP/SSE E2E before committing.

## Known Failures / Blockers

- No upstream branch is configured for `main`; use current `HEAD` as base unless a remote is configured later.
- Workspace already contains older untracked `.codex` planning/task files. Do not assume they are part of this stage.
- Real model/provider calls can be quota-limited. Use `/api/model/switch` to switch to an available configured provider if needed, without reading or exposing `.local/model-providers.json`.

## Next Actions

1. Inspect current `ChatStreamResponse`, `ResearchEvent`, `ChatController`, and `GraphResearchRunner` to choose the smallest compatible event envelope design.
2. Implement stable event type/title/sequence helpers and update controller or runner conversion while preserving existing JSON fields.
3. Add focused tests, run required Maven validation, then perform real HTTP/SSE E2E and commit with a Chinese message.

## Open Questions

- Whether to add new fields directly to `ChatStreamResponse` or introduce a new nested `ResearchStreamEvent` field while preserving old fields.
- Whether `/api/research/stream` should expose the same enriched contract now or remain on raw `ResearchEvent` until a later compatibility pass.
- Whether node title centralization should live under `model`, `api`, or a small `stream`/`event` package.

## Avoid / Do Not Redo

- Do not add `parallel_executor`, `researcher_n`, `coder_n`, RAG, Redis, MCP, Docker executor, or frontend code in this stage.
- Do not rewrite the Graph runner lifecycle while stabilizing the event contract.
- Do not use mock agents or mock search fallback.
- Do not read, print, or commit API keys from `.local/model-providers.json`.
- Do not modify `deepresearch-main`; it is read-only reference material.
- Do not stage the older untracked `.codex` files unless the user explicitly asks.
- Do not leave a local backend service running after E2E; always stop it and confirm port `18080` is released.

## Resume Prompt

Resume task add-stable-stream-event-contract. Read .codex/tasks/add-stable-stream-event-contract.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
