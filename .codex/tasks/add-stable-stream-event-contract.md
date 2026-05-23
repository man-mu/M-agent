# Task Handoff: add-stable-stream-event-contract
Updated: 2026-05-23
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: cae13d8391ea525253188b0babfa43a7848be6b6
Current Commit: 712e83f46c69358b748670265b2efe2ac930b56a

## Project Mainline

- M-agent is a Java 17 / Maven / Spring Boot 3.4.x simplified backend that recreates the core behavior of `C:/MainData/code/Codex_project/deepresearch-main` under package `top.lanshan.manmu`.
- The project has migrated from the old simple runner to the default Graph-based backend using Spring AI Alibaba Graph.
- The current Graph path supports direct answer, automatic research, manual plan gate, accepted resume, rejected replan, stop, report persistence, session history, and real HTTP/SSE validation.
- The active long-running direction is to build a Graph advanced execution skeleton before adding RAG, Redis, MCP, Docker coder execution, or a full frontend.
- This stage established the stable streaming event contract that future stages will reuse for step execution state, `parallel_executor`, `researcher_n`, and `coder_n`.

## Stage Role in Mainline

- This stage is the first step of `.codex/graph-advanced-execution-plan.md`.
- It stabilizes SSE response fields and event classification so future advanced nodes can emit predictable, frontend-friendly timeline events.
- It deliberately did not add parallel executor, coder node, RAG, Redis, MCP, Docker execution, or frontend code.

## Mainline Progression

- Previous stage created and committed `.codex/graph-advanced-execution-plan.md`.
- This stage turned the plan's event envelope into code while preserving `/chat/stream` compatibility.
- Future stages should build on `ResearchEvent`, `ResearchStreamEventType`, `ResearchNodeMetadata`, and the enriched `ChatStreamResponse` rather than adding new controller-specific switches.

## Related Stage Handoffs

- Inherits from `.codex/graph-advanced-execution-plan.md`.
- Historical reference only: `.codex/graph-dynamic-orchestration-plan.md` and older graph migration handoffs under `.codex/tasks/`.
- Suggested next stage: `add-step-execution-state-model`.

## Goal

Implement the first advanced execution stage: a stable streaming event contract for Graph research runs.

## Task Theme / User Intent

- Continue recreating a simplified version of `deepresearch-main`, but in staged, reviewable work.
- Make WebFlux SSE output stable enough for future frontend timeline rendering and advanced nodes such as `parallel_executor`, `researcher_n`, and `coder_n`.
- Keep the implementation conservative and compatible with existing M-agent behavior.

## Acceptance Criteria

- Stable envelope fields exist: `sequence`, `event_type`, `node_name`, `node_type`, `executor_id`, `step_id`, `phase`, `status`, `display_title`, `content`, `payload`, `site_information`, `done`, `timestamp`, and `graph_id`.
- `/chat/stream` preserves old fields: `nodeName`, `graphId`, `displayTitle`, `content`, and `siteInformation`.
- Stable event types cover current lifecycle: `node.started`, `node.delta`, `node.completed`, `node.failed`, `plan.generated`, `human_feedback.waiting`, `human_feedback.accepted`, `human_feedback.rejected`, `report.completed`, `graph.completed`, `graph.stopped`, and `graph.failed`. `graph.started` is reserved in the enum for later explicit start events.
- Per-thread increasing sequence values are assigned by `GraphResearchRunner`.
- Node display title and metadata logic is centralized outside `ChatController`.
- Focused tests and full `mvn test` pass.
- Real HTTP/SSE E2E covers direct answer, auto accepted research, manual pause, and stop of a paused thread.
- Backend service is stopped after E2E and port `18080` is released.

## Scope

- Write scope was limited to the M-agent project at `C:/MainData/code/Codex_project/M-agent`.
- Main code areas touched:
  - `src/main/java/top/lanshan/manmu/model`
  - `src/main/java/top/lanshan/manmu/api`
  - `src/main/java/top/lanshan/manmu/runner`
  - `src/test/java/top/lanshan/manmu`
  - `.codex/tasks/add-stable-stream-event-contract.md`
- Read-only reference root:
  - `C:/MainData/code/Codex_project/deepresearch-main`

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-stable-stream-event-contract.md`

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
- Existing untracked older `.codex/tasks/*.md` files unless the user explicitly asks to curate or commit them.

## Current State

- Implementation, tests, real E2E, service shutdown, and handoff update are complete.
- Branch is `main`.
- No upstream branch is configured.
- Older unrelated untracked `.codex` planning/task files still exist and were not modified or staged.
- Completed stage is committed as `712e83f46c69358b748670265b2efe2ac930b56a`.

## Completed

- Added `ResearchStreamEventType` for stable lifecycle event types.
- Added `ResearchNodeMetadata` for centralized node title, node type, and executor id metadata.
- Extended `ResearchEvent` with stable fields and a `withSequence(...)` enrichment method.
- Extended `ChatStreamResponse` with stable snake_case envelope fields while preserving legacy camelCase fields.
- Updated `GraphResearchRunner` to assign per-thread increasing sequence values and preserve sequence continuity across manual pause/resume until terminal events.
- Updated `ChatController` and `ResearchController` to use stable event type classification while keeping old SSE event names (`message`, `done`, `error`, `stopped`).
- Added focused tests for direct answer envelope, auto research sequence increments, human feedback waiting event type, and stop event type.
- Ran focused tests, full Maven tests, packaged the jar, real HTTP/SSE E2E, and stopped the backend.

## Decisions

- The stable contract is added directly to the existing `/chat/stream` response instead of replacing it with a new wrapper, preserving old consumers.
- `/api/research/stream` now emits enriched raw `ResearchEvent` objects, including both legacy `threadId` and stable `thread_id`.
- SSE `event:` headers stay compatible (`message`, `done`, `error`, `stopped`); detailed stable types live in JSON `event_type`.
- `graph.started` remains an enum value reserved for a future explicit start event; this stage did not add synthetic start events to avoid changing lifecycle semantics.
- Node title metadata lives in `model` because it is shared by raw research events and chat envelope conversion.

## Evidence / References

- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/graph-advanced-execution-plan.md`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchStreamEventType.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchNodeMetadata.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchEvent.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ChatStreamResponse.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/GraphResearchRunner.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/api/ChatController.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/api/ResearchController.java`
- `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/model/enums/NodeNameEnum.java`
- `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/model/enums/StreamNodePrefixEnum.java`

## Files Touched

- `.codex/tasks/add-stable-stream-event-contract.md`
- `src/main/java/top/lanshan/manmu/api/ChatController.java`
- `src/main/java/top/lanshan/manmu/api/ResearchController.java`
- `src/main/java/top/lanshan/manmu/model/ChatStreamResponse.java`
- `src/main/java/top/lanshan/manmu/model/ResearchEvent.java`
- `src/main/java/top/lanshan/manmu/model/ResearchNodeMetadata.java`
- `src/main/java/top/lanshan/manmu/model/ResearchStreamEventType.java`
- `src/main/java/top/lanshan/manmu/runner/GraphResearchRunner.java`
- `src/test/java/top/lanshan/manmu/api/ChatControllerTest.java`
- `src/test/java/top/lanshan/manmu/api/ResearchControllerLlmWorkflowTest.java`
- `src/test/java/top/lanshan/manmu/runner/GraphResearchRunnerTest.java`

## Commands Run

- `git status --short --branch`
- `git log --oneline --decorate -5`
- `git diff --stat`
- `git diff --name-only`
- `Get-Content` for task handoff, `AGENTS.md`, current code, tests, and selected deepresearch-main reference enum files.
- `mvn '-Dtest=ChatControllerTest,GraphResearchRunnerTest' test`
- `mvn test`
- `mvn -DskipTests package`
- `java -jar target/deepresearch-mvp-0.1.0-SNAPSHOT.jar --server.port=18080`
- `curl.exe -sS http://localhost:18080/api/model/current`
- `curl.exe -sS -X POST http://localhost:18080/api/model/switch ...` using request body in `target/http-check/stable-switch-deepseek.json`
- `curl.exe -sS -N --max-time 120 -X POST http://localhost:18080/chat/stream ...` for direct answer.
- `curl.exe -sS -N --max-time 240 -X POST http://localhost:18080/chat/stream ...` for auto accepted research.
- `curl.exe -sS -N --max-time 180 -X POST http://localhost:18080/chat/stream ...` for manual pause.
- `curl.exe -sS -X POST http://localhost:18080/chat/stop ...` for stopped paused thread.
- `curl.exe -sS http://localhost:18080/api/reports/stable-auto-thread/exists`
- `curl.exe -sS http://localhost:18080/api/sessions/stable-auto-session/threads/stable-auto-thread`
- `curl.exe -sS http://localhost:18080/api/sessions/stable-manual-session/threads/stable-manual-thread`
- `Stop-Process` for the verification backend PID.
- `Get-NetTCPConnection -LocalPort 18080`
- `git add .codex/tasks/add-stable-stream-event-contract.md src/main/java/top/lanshan/manmu/api/ChatController.java src/main/java/top/lanshan/manmu/api/ResearchController.java src/main/java/top/lanshan/manmu/model/ChatStreamResponse.java src/main/java/top/lanshan/manmu/model/ResearchEvent.java src/main/java/top/lanshan/manmu/model/ResearchNodeMetadata.java src/main/java/top/lanshan/manmu/model/ResearchStreamEventType.java src/main/java/top/lanshan/manmu/runner/GraphResearchRunner.java src/test/java/top/lanshan/manmu/api/ChatControllerTest.java src/test/java/top/lanshan/manmu/api/ResearchControllerLlmWorkflowTest.java src/test/java/top/lanshan/manmu/runner/GraphResearchRunnerTest.java`
- `git commit -m <Chinese message meaning "stabilize graph orchestration streaming event protocol">`

## Verification

- Focused tests passed: `mvn '-Dtest=ChatControllerTest,GraphResearchRunnerTest' test` ran 17 tests with 0 failures.
- Full tests passed: `mvn test` ran 101 tests with 0 failures.
- Packaging passed: `mvn -DskipTests package`.
- Docker PostgreSQL was healthy through container `manmu-postgres`; backend logs showed Flyway connected to PostgreSQL 17.9 and schema version 2.
- Real backend started on `http://localhost:18080` using Java 17.
- Runtime model was switched to `deepseek/deepseek-chat` through `/api/model/switch`; `/api/model/current` returned `apiKeyConfigured=true`. No key was printed or committed.
- Real HTTP/SSE E2E files saved under ignored `target/http-check/`:
  - `stable-direct.sse`: direct answer completed with `graph.completed`.
  - `stable-auto.sse`: auto accepted research completed with `plan.generated`, `report.completed`, and `graph.completed`; 18 events had sequence `1..18`.
  - `stable-manual-pause.sse`: manual plan gate stopped at `human_feedback.waiting`.
  - `stable-stop-response.json`: `/chat/stop` returned success for the paused thread.
- Persistence checks:
  - `stable-auto-report-exists.json` reported report existence `true`.
  - `stable-auto-history.json` reported `COMPLETED`.
  - `stable-direct-history.json` reported `COMPLETED`.
  - `stable-manual-history-after-stop.json` reported `STOPPED`.
- Backend service was stopped and `Get-NetTCPConnection -LocalPort 18080` returned no listener (`PORT_18080_RELEASED`).

## Known Failures / Blockers

- No blocker remains for this stage.
- No upstream branch is configured for `main`.
- Older unrelated untracked `.codex` planning/task files remain in the workspace and should not be staged unless the user asks.
- During E2E validation, an initial PowerShell assertion script falsely reported a missing `plan.generated` because of the comparison expression; direct inspection and a corrected assertion confirmed `stable-auto.sse` contained `planner:plan.generated` at sequence 7.

## Next Actions

1. Resume the next stage `add-step-execution-state-model` from `.codex/tasks/add-step-execution-state-model.md`.
2. Reuse `ResearchEvent`, `ResearchStreamEventType`, `ResearchNodeMetadata`, and `ChatStreamResponse` for future `step_id`, executor assignment, and dynamic node timeline events.
3. Keep older unrelated untracked `.codex` planning/task files untouched unless the user explicitly asks to curate or commit them.

## Open Questions

- Future stage should decide the exact `step_id` generation scheme before wiring it into event payloads.
- Future stage should decide whether to emit an explicit `graph.started` event or keep it reserved until frontend timeline work requires it.

## Avoid / Do Not Redo

- Do not add `parallel_executor`, `researcher_n`, `coder_n`, RAG, Redis, MCP, Docker executor, or frontend code in this completed stage.
- Do not rewrite the Graph runner lifecycle while building on this contract.
- Do not use mock agents or mock search fallback.
- Do not read, print, or commit API keys from `.local/model-providers.json`.
- Do not modify `deepresearch-main`; it is read-only reference material.
- Do not stage the older untracked `.codex` files unless the user explicitly asks.
- Do not leave a local backend service running after E2E.

## Resume Prompt

Resume task add-stable-stream-event-contract. Read .codex/tasks/add-stable-stream-event-contract.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
