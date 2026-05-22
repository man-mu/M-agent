# Task Handoff: add-running-chat-stop-cancellation
Updated: 2026-05-22 11:13:44 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 0eade48614a0e6470bb106edf3b959dd6cc939c1
Current Commit: 0eade48614a0e6470bb106edf3b959dd6cc939c1

## Project Mainline

- This project is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under `C:/MainData/code/Codex_project/M-agent`.
- The long-term direction is to imitate `C:/MainData/code/Codex_project/deepresearch-main` in a deliberately reduced, runnable backend-first form.
- The project is growing DeepResearch workflow semantics in `SimpleResearchRunner` before migrating to Spring AI Alibaba Graph.
- Completed stages removed mock runtime paths, added DeepResearch-compatible SSE envelopes, upgraded Plan/Step state, added a lightweight `research_team` control node, added Bocha-only real web search through an `information` node, added a real `processor` node for PROCESSING steps, added a human feedback plan gate for `/chat`, and added a minimal `/chat/stop` endpoint for paused-state cleanup.
- The current stable auto-accepted backend loop is: planner -> information -> research_team -> researcher -> research_team -> processor -> research_team -> reporter -> `__END__`.
- The current interactive loop is: `/chat/stream` with `auto_accepted_plan=false` runs planner -> `human_feedback` waiting; `/chat/resume` with `feedback=true` continues execution; `/chat/resume` with `feedback=false` replans with `feedback_content` and waits again; `/chat/stop` can remove a paused `human_feedback` state.
- The next mainline step is to make `/chat/stop` cancel a running chat workflow, aligning more closely with `deepresearch-main`'s `GraphProcess.stopGraph(GraphId)` future cancellation semantics.
- New feature work must not introduce mock agents, mock search, fabricated search results, local secret leaks, Redis, RAG, MCP, frontend code, or full Graph migration unless a later task explicitly asks for those layers.

## Stage Role in Mainline

- This stage should add running-task cancellation on top of the already completed paused-state `/chat/stop` lifecycle surface.
- It exists because M-agent now exposes the stop endpoint and can clean paused states, but it still cannot stop an in-flight LLM/search/reporter workflow once execution has started.
- It should align with `deepresearch-main` at the behavior level: the reference stores running graph work in `graphTaskFutureMap`, `/chat/stop` removes the task, and a non-completed future is cancelled.
- The local MVP should stay WebFlux/SSE-first and in-memory; the goal is a small runner task registry or subscription model, not a full Spring AI Alibaba Graph migration.

## Mainline Progression

- `add-research-team` introduced a controlled loop around step execution.
- `add-information-node-bocha-search` added real Bocha `information` search before execution.
- `add-processor-node-search-context` split PROCESSING into a dedicated `processor` path that consumes prior observations and site information.
- `add-human-feedback-plan-gate` evolved the workflow from automatic run-to-completion to interactive controllable research with planner pause, accept resume, and rejected replan.
- `add-chat-stop-session-lifecycle` added `/chat/stop`, reference-style `ApiResponse`, paused-state cleanup, resume-after-stop error behavior, and real curl verification.
- `add-running-chat-stop-cancellation` should complete the lifecycle boundary by making stop work during active execution before future stages add status, expiration, frontend controls, report history, or Graph migration.

## Related Stage Handoffs

- `add-research-team`: completed lightweight `ResearchTeamNode` and runner loop.
- `add-information-node-bocha-search`: completed Bocha-only `InformationNode` and real HTTP verification.
- `add-processor-node-search-context`: completed processor agent/node/routing and real HTTP verification.
- `add-human-feedback-plan-gate`: completed `/chat` human feedback plan gate, in-memory paused state, `/chat/resume`, and focused tests.
- `add-chat-stop-session-lifecycle`: completed `/chat/stop` for paused-state cleanup and committed it as `0eade48614a0e6470bb106edf3b959dd6cc939c1`.

## Goal

- Add minimal running chat stop/cancellation support so M-agent can cancel or end an in-flight `/chat/stream` or accepted `/chat/resume` workflow, while staying aligned with `deepresearch-main` stop semantics and keeping the local MVP small.

## Task Theme / User Intent

- The user wants the next session to implement `add-running-chat-stop-cancellation` and continue aligning M-agent with `C:/MainData/code/Codex_project/deepresearch-main`.
- The user accepted that the completed endpoints have no discovered blocking bugs in covered paths, but noted the important remaining boundary: `/chat/stop` currently only clears paused state and does not cancel running LLM/search work.
- The next implementation should imitate reference lifecycle behavior without copying the full Graph, executor, MemorySaver, Redis, frontend, RAG, MCP, or report-history stack.

## Acceptance Criteria

- Preserve the existing `/chat/stop` paused-state behavior and response shape.
- Add a lightweight runner-level lifecycle registry for running chat workflows keyed by `threadId`.
- Make `/chat/stop` return success for a currently running chat thread and trigger cancellation or termination of its active execution.
- Cover both auto-accepted `/chat/stream` runs and `/chat/resume` accepted runs when practical within the MVP.
- A stopped stream should end clearly for the client, preferably through an explicit `stopped`, `error`, or terminal SSE event rather than a silent hung connection.
- Stopping a missing or already cleaned-up thread should keep returning the existing reference-style failure response.
- Preserve current default auto-run behavior, existing `/api/research/stream` behavior, paused-state stop behavior, and resume-after-stop error behavior.
- Keep state in memory for this MVP; do not introduce Redis, database tables, Graph checkpointers, frontend code, or full Spring AI Alibaba Graph migration.
- Add focused tests for running-task registration, stop of a running stream, cleanup after completion, stop of a missing thread, paused-state compatibility, and resume compatibility.
- Run Java 17 verification with `mvn test`.
- Run manual curl HTTP verification when useful; if starting the backend on port `8080`, kill any process occupying the port first if needed, then close the backend service and confirm the port is released.
- Commit the completed stage with a Chinese commit message.

## Scope

- Work only in `C:/MainData/code/Codex_project/M-agent`.
- Inspect `C:/MainData/code/Codex_project/deepresearch-main` as read-only guidance.
- Keep this stage backend-focused and minimal.
- Prefer in-memory task/subscription state management for the MVP; do not add Redis, database tables, Graph saver infrastructure, frontend code, RAG, MCP, or full Spring AI Alibaba Graph migration.
- Preserve real provider paths and do not introduce mock production behavior.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/AGENTS.md` only if project instructions need a minimal Chinese update
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-running-chat-stop-cancellation.md`

### Read-only Reference Roots

- `C:/MainData/code/Codex_project/deepresearch-main`
- Existing Git history in `C:/MainData/code/Codex_project/M-agent`
- Existing task handoffs under `C:/MainData/code/Codex_project/M-agent/.codex/tasks`
- Ignored manual verification artifacts under `C:/MainData/code/Codex_project/M-agent/target`

### Forbidden Write Roots

- `C:/MainData/code/Codex_project/deepresearch-main`
- `C:/MainData/code/Codex_project/M-agent/.local`
- `C:/MainData/code/Codex_project/M-agent/target`
- `C:/MainData/code/Codex_project/M-agent/.idea`
- `C:/MainData/code/Codex_project/M-agent/.claude`
- Any file containing API keys or local secrets

## Current State

- Git branch: `main`.
- Current commit: `0eade48614a0e6470bb106edf3b959dd6cc939c1`.
- No upstream is configured for `main`; upstream and merge-base commands fail with "no upstream configured".
- Working tree currently has an unrelated untracked `.claude/settings.local.json`; do not edit, delete, or commit it unless the user explicitly asks.
- Ignored `target/` contains manual curl verification artifacts from the previous stage; they are not source changes.
- Port `8080` was released after the manual curl test; no backend service should be left running by the previous session.
- Current M-agent has `/chat/stream`, `/chat/resume`, `/chat/stop`, and `/api/research/stream`.
- Current `SimpleResearchRunner.stop(threadId)` only removes paused states from `pausedStates`; it does not know about running Flux subscriptions, tasks, or futures.

## Completed

- Completed and committed `add-chat-stop-session-lifecycle` as `0eade48614a0e6470bb106edf3b959dd6cc939c1`.
- Added local `ApiResponse<T>` and reference-style `/chat/stop` response shape.
- Added `SimpleResearchRunner.stop(String threadId)` for paused-state cleanup.
- Added focused tests for paused stop, missing stop, and resume-after-stop behavior.
- Ran `mvn test` with Java 17 in the previous stage; 34 tests passed.
- Ran manual curl full-chain real HTTP verification after the commit:
  - DeepSeek provider key save returned HTTP 200 without exposing the key.
  - Model switch to `deepseek-chat` returned HTTP 200.
  - `/api/model/test` returned `ok=true`.
  - `/chat/stream` with `auto_accepted_plan=false` returned planner and `human_feedback` SSE messages.
  - `/chat/resume` with accepted feedback reached `information`, `research_team`, `researcher`, `processor`, `reporter`, and `__END__` without an error event.
  - `/chat/stop` on a paused thread returned reference-style success.
  - `/chat/stop` on a missing thread returned reference-style failure.
  - `/chat/resume` after stop returned an expected `human_feedback` error SSE with a missing paused state reason.
  - `/api/research/stream` with a current-news query exercised real model plus Bocha search and reached `__END__` without an error event.
  - The manually started backend was stopped and port `8080` was released.

## Decisions

- The next stage should not jump directly to Spring AI Alibaba Graph or MemorySaver; that migration is larger and could destabilize the runnable MVP.
- Implement running stop first because it is the missing lifecycle behavior between current M-agent and the reference `deepresearch-main` stop path.
- Use an in-memory task registry or subscription registry keyed by `threadId` rather than Redis or persistent storage.
- Prefer clear SSE terminal behavior for stopped streams so clients are not left waiting.
- Keep `/api/research/stream` unchanged unless a shared runner lifecycle refactor naturally affects it and tests prove behavior is preserved.

## Evidence / References

- Reference stop controller: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/controller/ChatController.java`, lines around 146-150.
- Reference graph lifecycle: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/controller/graph/GraphProcess.java`, lines around 63, 110-144, and 275-283.
- Reference behavior: `GraphProcess` stores a `Future<?>` in `graphTaskFutureMap`; `stopGraph(GraphId)` removes it, returns false if missing, true if already done, otherwise calls `future.cancel(true)`.
- Local chat controller: `src/main/java/top/lanshan/manmu/api/ChatController.java`.
- Local runner: `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`.
- Local API response model: `src/main/java/top/lanshan/manmu/model/ApiResponse.java`.
- Local graph id model: `src/main/java/top/lanshan/manmu/model/GraphId.java`.
- Local feedback request model: `src/main/java/top/lanshan/manmu/model/FeedbackRequest.java`.
- Local runner tests: `src/test/java/top/lanshan/manmu/runner/SimpleResearchRunnerTest.java`.
- Local chat controller tests: `src/test/java/top/lanshan/manmu/api/ChatControllerTest.java`.
- Previous stage handoff: `.codex/tasks/add-chat-stop-session-lifecycle.md`.
- Manual curl output artifacts are ignored under `target/manual-*.sse` and `target/manual-server*.log`.

## Files Touched

- `.codex/tasks/add-running-chat-stop-cancellation.md`

## Commands Run

- `Get-Content -Raw C:\Users\20232\.codex\skills\task-handoff\SKILL.md`
- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` failed because no upstream is configured.
- `git merge-base HEAD '@{upstream}'` failed because no upstream is configured.
- `Get-Content -Encoding UTF8 -Raw AGENTS.md`
- `Get-ChildItem -LiteralPath .codex/tasks -File | Select-Object -ExpandProperty Name`
- `Get-Content -LiteralPath .codex/tasks/add-chat-stop-session-lifecycle.md`
- `git log --oneline -8`
- `git show --stat --oneline --decorate --no-renames HEAD`
- `Get-ChildItem -Force -Recurse -LiteralPath .claude -ErrorAction SilentlyContinue | Select-Object FullName,Length,LastWriteTime`
- `Get-ChildItem -LiteralPath target -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -like 'manual-*.sse' -or $_.Name -like 'manual-server*.log' } | Select-Object Name,Length,LastWriteTime`
- `rg -n "stopGraph|graphTaskFutureMap|cancel|future|processStream|doOnCancel|stopped|stop" C:\MainData\code\Codex_project\deepresearch-main\src\main\java\com\alibaba\cloud\ai\example\deepresearch\controller C:\MainData\code\Codex_project\deepresearch-main\src\main\java\com\alibaba\cloud\ai\example\deepresearch\model C:\MainData\code\Codex_project\deepresearch-main\DeepResearch.http`
- `Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz"`
- Read targeted reference snippets from `GraphProcess.java`, local `ChatController.java`, and local `SimpleResearchRunner.java`.
- `Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object LocalAddress,LocalPort,State,OwningProcess`

## Verification

- No implementation verification has run for this new stage because implementation has not started.
- Previous stage verification:
  - `mvn test` with Java 17 passed 34 tests.
  - Manual curl real HTTP verification passed the model, chat gate, resume, paused stop, resume-after-stop, and Bocha search paths.
  - The manually started backend was stopped and port `8080` was released.

## Known Failures / Blockers

- No upstream Git branch is configured.
- Unrelated untracked `.claude/settings.local.json` exists in the working tree; treat it as outside this task scope.
- Current runner does not track running task futures, subscriptions, or sinks, so `/chat/stop` cannot yet cancel in-flight LLM/search calls.
- Full cancellation of blocking provider calls may be cooperative only; Reactor cancellation may stop downstream emission before it actually interrupts a synchronous HTTP call.
- Paused state is process-local and has no expiration.
- Real LLM/provider tests can fail from external API rate limits or network timeouts.

## Next Actions

- Inspect `SimpleResearchRunner`, `ChatController`, and tests; design the smallest in-memory running-task registry that can cancel or terminate `/chat/stream` and accepted `/chat/resume` executions by `threadId` while preserving paused-state stop behavior.
- Implement running cancellation and focused tests for active stream stop, accepted resume stop, cleanup after normal completion, missing stop, and resume-after-stop compatibility.
- Run Java 17 `mvn test`; optionally run manual curl against port `8080` with real provider keys, killing any existing `8080` owner first and closing the backend afterward; commit with a Chinese message.

## Open Questions

- What exact SSE event should a stopped local stream emit: `error`, `done`, or a dedicated `stopped` phase/node?
- Should `/api/research/stream` also be cancellable through the same registry, or should this stage limit cancellation to `/chat/*` to match `deepresearch-main`'s `/chat/stop` API?
- How much cancellation can be guaranteed for synchronous model/search calls in the current Reactor chain without replacing providers or moving to an executor/future model?

## Avoid / Do Not Redo

- Do not edit `C:/MainData/code/Codex_project/deepresearch-main`.
- Do not migrate to Spring AI Alibaba Graph yet.
- Do not add Redis, persistent databases, RAG, MCP, frontend changes, or Graph saver/checkpoint infrastructure in this stage.
- Do not introduce mock agent output, mock search, or fake search context in production code.
- Do not commit `.local`, `target`, `.idea`, `.claude`, API keys, generated SSE/curl output, or logs.
- Do not leave manually started backend services running after tests.
- Do not redo the already completed paused-state `/chat/stop` implementation unless tests prove it must change for running cancellation support.

## Resume Prompt
Resume task add-running-chat-stop-cancellation. Read .codex/tasks/add-running-chat-stop-cancellation.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
