# Task Handoff: add-chat-stop-session-lifecycle
Updated: 2026-05-22 10:53:56 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 6d74a289ea912ebe86e950d0365b04883dc3301a
Current Commit: See `git rev-parse HEAD` on resume; this handoff is stored inside the stage commit, so the exact commit hash changes when the handoff itself is amended.

## Project Mainline

- This project is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under `C:/MainData/code/Codex_project/M-agent`.
- The long-term direction is to imitate `C:/MainData/code/Codex_project/deepresearch-main` in a deliberately reduced, runnable backend-first form.
- The project is growing DeepResearch workflow semantics in `SimpleResearchRunner` before migrating to Spring AI Alibaba Graph.
- Completed stages removed mock runtime paths, added DeepResearch-compatible SSE envelopes, upgraded Plan/Step state, added a lightweight `research_team` control node, added Bocha-only real web search through an `information` node, added a real `processor` node for PROCESSING steps, and added a human feedback plan gate for `/chat`.
- The current stable auto-accepted backend loop is: planner -> information -> research_team -> researcher -> research_team -> processor -> research_team -> reporter -> `__END__`.
- The current interactive loop is: `/chat/stream` with `auto_accepted_plan=false` runs planner -> `human_feedback` waiting; `/chat/resume` with `feedback=true` continues execution; `/chat/resume` with `feedback=false` replans with `feedback_content` and waits again.
- This stage adds minimal chat session lifecycle control through `/chat/stop`, so the new pause/resume boundary can also be cancelled or cleaned up.
- New feature work must not introduce mock agents, mock search, fabricated search results, local secret leaks, Redis, RAG, MCP, frontend code, or full Graph migration unless a later task explicitly asks for those layers.

## Stage Role in Mainline

- This stage adds a minimal `/chat/stop` and session lifecycle surface after the human feedback plan gate.
- It exists because M-agent can now pause and resume chat research workflows, and it now needs to cancel or clean up a paused chat workflow before heavier persistence or graph migration work.
- It aligns with `deepresearch-main`'s `ChatController.stopGraph(...)` and `GraphProcess.stopGraph(GraphId)` response semantics at the API level while keeping the local MVP in-memory and WebFlux/SSE-first.

## Mainline Progression

- `add-research-team` introduced a controlled loop around step execution.
- `add-information-node-bocha-search` added real Bocha `information` search before execution.
- `add-processor-node-search-context` split PROCESSING into a dedicated `processor` path that consumes prior observations and site information.
- `add-human-feedback-plan-gate` evolved the workflow from automatic run-to-completion to interactive controllable research with planner pause, accept resume, and rejected replan.
- `add-chat-stop-session-lifecycle` builds on the in-memory paused-state boundary by adding paused-thread stop/cleanup semantics before any heavier session persistence or Graph migration.
- Future stages can build on this lifecycle boundary to add running-task cancellation, pause expiration, `/chat/status`, frontend plan controls, report history, or a Spring AI Alibaba Graph-backed execution model.

## Related Stage Handoffs

- `add-research-team`: completed lightweight `ResearchTeamNode` and runner loop.
- `add-information-node-bocha-search`: completed Bocha-only `InformationNode` and real HTTP verification.
- `add-processor-node-search-context`: completed processor agent/node/routing and real HTTP verification.
- `add-human-feedback-plan-gate`: completed `/chat` human feedback plan gate, in-memory paused state, `/chat/resume`, and focused tests.

## Goal

- Add minimal chat stop and session lifecycle support so M-agent can cancel or clean up a chat research thread waiting at the human feedback plan gate, while staying aligned with `deepresearch-main` stop response semantics.

## Task Theme / User Intent

- The user wants to continue aligning M-agent with `C:/MainData/code/Codex_project/deepresearch-main`.
- The prior stage completed `add-human-feedback-plan-gate`; this stage is the natural follow-up: stop/cancel and lifecycle cleanup for the newly interactive chat workflow.
- The implementation imitates reference stop API semantics without copying the full Graph, executor, MemorySaver, Redis, frontend, RAG, MCP, or report-history stack.

## Acceptance Criteria

- Add a `/chat/stop` endpoint that accepts a `GraphId`-style JSON body with `session_id` and `thread_id`.
- For a thread paused at `human_feedback`, stopping should remove the in-memory paused state and return a successful structured response.
- `/chat/resume` for a stopped or unknown thread should return the existing clear structured `human_feedback` error event or an equivalent explicit lifecycle error.
- Preserve current default auto-run behavior and existing `/api/research/stream` behavior.
- Keep state in memory for this MVP; do not introduce Redis, database tables, Graph checkpointers, frontend code, or full Spring AI Alibaba Graph migration.
- Consider a small runner-level lifecycle API, such as `stop(threadId)` or `stop(GraphId)`, that can later grow to running-task cancellation.
- Add focused tests for stopping a paused thread, stopping a missing thread, resume after stop, and existing resume/auto-run compatibility.
- Run Java 17 verification with `mvn test`.
- If a manual backend service is started for HTTP verification, close it and confirm the temporary port is released.
- Commit the completed stage with a Chinese commit message.

## Scope

- Work only in `C:/MainData/code/Codex_project/M-agent`.
- Inspect `C:/MainData/code/Codex_project/deepresearch-main` as read-only guidance.
- Keep this stage backend-focused and minimal.
- Prefer in-memory state management for the MVP; do not add Redis, database tables, Graph saver infrastructure, frontend code, RAG, MCP, or full Spring AI Alibaba Graph migration.
- Preserve real provider paths and do not introduce mock production behavior.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/AGENTS.md` only if project instructions need a minimal Chinese update
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-chat-stop-session-lifecycle.md`

### Read-only Reference Roots

- `C:/MainData/code/Codex_project/deepresearch-main`
- Existing Git history in `C:/MainData/code/Codex_project/M-agent`
- Existing task handoffs under `C:/MainData/code/Codex_project/M-agent/.codex/tasks`

### Forbidden Write Roots

- `C:/MainData/code/Codex_project/deepresearch-main`
- `C:/MainData/code/Codex_project/M-agent/.local`
- `C:/MainData/code/Codex_project/M-agent/target`
- `C:/MainData/code/Codex_project/M-agent/.idea`
- Any file containing API keys or local secrets

## Current State

- Git branch: `main`.
- Stage implementation and this handoff are committed together; run `git rev-parse HEAD` on resume for the exact current commit.
- Working tree was clean after the final handoff amend.
- No upstream is configured for `main`.
- Backend default port is `8080`.
- Project instruction says any manually started backend service must be closed after testing.
- Current M-agent has `/chat/stream`, `/chat/resume`, `/chat/stop`, and `/api/research/stream`.
- Current `SimpleResearchRunner` stores paused states in memory by `threadId` in `pausedStates`.
- Current `SimpleResearchRunner.resume(...)` removes paused state before accepting or rejecting; rejected feedback replans and stores the state again.
- Current `SimpleResearchRunner.stop(threadId)` removes paused state and returns whether a paused thread was found.
- Current code does not track running Flux subscriptions or futures for cancellation; this stage supports paused-state cleanup and explicit stopped/unknown behavior only.

## Completed

- Completed the upstream `add-human-feedback-plan-gate` stage and committed it as `6d74a28`.
- Confirmed `deepresearch-main` exposes `POST /chat/stop`.
- Confirmed reference stop semantics:
  - `ChatController.stopGraph(@RequestBody GraphId graphId)` calls `graphProcess.stopGraph(graphId)`.
  - Success returns `ApiResponse.success(graphId.threadId())`.
  - Failure returns `ApiResponse.error("Failure", graphId.threadId())`.
  - `GraphProcess.stopGraph(GraphId)` removes the future from `graphTaskFutureMap`; missing future returns false; completed future returns true; running future is cancelled.
- Confirmed reference streaming emits a stopped/end message when processing is interrupted.
- Added local `ApiResponse<T>` with `code`, `status`, `message`, and `data` fields matching the reference response shape.
- Added `SimpleResearchRunner.stop(String threadId)` to remove in-memory paused state and return false for blank or missing thread IDs.
- Added `POST /chat/stop` to `ChatController`; success returns `ApiResponse.success(threadId)`, missing state returns `ApiResponse.error("Failure", threadId)`.
- Added focused runner tests for stopping a paused thread, resume-after-stop, and stopping a missing thread.
- Added focused controller tests for `/chat/stop` success and missing-thread failure responses.
- Verified the full Maven test suite with Java 17.
- Committed the stage with a Chinese message meaning "add chat stop lifecycle endpoint".

## Decisions

- `/chat/stop` compatibility is the right first lifecycle surface because the reference project exposes stop through `/chat/stop`.
- Keep the first local implementation small: stop paused states, make resume-after-stop explicit, preserve auto-run behavior, and defer true running-task cancellation until the runner has a task registry or subscription model.
- Use the existing `GraphId` model for the stop request because local `ChatController` already uses it in chat envelopes and the reference project uses `GraphId` for stop.
- Use a local `ApiResponse<T>` model for this endpoint to match the reference stop response without changing SSE chat envelopes.

## Evidence / References

- Reference stop controller: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/controller/ChatController.java`, lines around 146-150.
- Reference graph lifecycle: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/controller/graph/GraphProcess.java`, lines around 61-64, 205-221, 240-276.
- Reference stop response model: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/model/ApiResponse.java`.
- Reference stop request model: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/model/req/GraphId.java`.
- Reference resume request model: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/model/req/FeedbackRequest.java`.
- Reference HTTP examples: `C:/MainData/code/Codex_project/deepresearch-main/DeepResearch.http`.
- Local chat controller: `src/main/java/top/lanshan/manmu/api/ChatController.java`.
- Local runner: `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`.
- Local API response model: `src/main/java/top/lanshan/manmu/model/ApiResponse.java`.
- Local graph id model: `src/main/java/top/lanshan/manmu/model/GraphId.java`.
- Local feedback request model: `src/main/java/top/lanshan/manmu/model/FeedbackRequest.java`.
- Local runner tests: `src/test/java/top/lanshan/manmu/runner/SimpleResearchRunnerTest.java`.
- Local chat controller tests: `src/test/java/top/lanshan/manmu/api/ChatControllerTest.java`.

## Files Touched

- `.codex/tasks/add-chat-stop-session-lifecycle.md`
- `src/main/java/top/lanshan/manmu/api/ChatController.java`
- `src/main/java/top/lanshan/manmu/model/ApiResponse.java`
- `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- `src/test/java/top/lanshan/manmu/api/ChatControllerTest.java`
- `src/test/java/top/lanshan/manmu/runner/SimpleResearchRunnerTest.java`

## Commands Run

- `Get-Content -Raw C:\Users\20232\.codex\skills\task-handoff\SKILL.md`
- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git rev-parse HEAD`
- `git log --oneline -5`
- `Get-ChildItem -LiteralPath .codex/tasks -File | Select-Object -ExpandProperty Name`
- `Get-Content -Raw .codex/tasks/add-human-feedback-plan-gate.md`
- `Get-Content -Encoding UTF8 -Raw AGENTS.md`
- `rg -n "stop|Stop|/chat/stop|terminate|cancel|resume|session|thread_id|threadId" C:\MainData\code\Codex_project\deepresearch-main\src\main\java C:\MainData\code\Codex_project\deepresearch-main\DeepResearch.http C:\MainData\code\Codex_project\deepresearch-main\README.md C:\MainData\code\Codex_project\deepresearch-main\README_zh.md`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` failed because no upstream is configured.
- `git merge-base HEAD '@{upstream}'` failed because no upstream is configured.
- Read targeted reference snippets from `ChatController.java`, `GraphProcess.java`, `GraphId.java`, `FeedbackRequest.java`, and `DeepResearch.http`.
- `rg -n "stopGraph|/stop|stop" C:\MainData\code\Codex_project\deepresearch-main\src\main\java\com\alibaba\cloud\ai\example\deepresearch\controller`
- `rg -n "runUntilPlanGate|resume\(|pausedStates|FeedbackRequest|ResumeDecision|autoAcceptedPlan" src/main/java/top/lanshan/manmu src/test/java/top/lanshan/manmu`
- `Get-Content -LiteralPath .codex/tasks/add-chat-stop-session-lifecycle.md`
- `Get-Content -LiteralPath src/main/java/top/lanshan/manmu/api/ChatController.java`
- `Get-Content -LiteralPath src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- `Get-Content -LiteralPath src/main/java/top/lanshan/manmu/model/GraphId.java`
- `Get-Content -LiteralPath src/main/java/top/lanshan/manmu/model/FeedbackRequest.java`
- `Get-Content -LiteralPath src/test/java/top/lanshan/manmu/api/ChatControllerTest.java`
- `Get-Content -LiteralPath src/test/java/top/lanshan/manmu/runner/SimpleResearchRunnerTest.java`
- `Get-Content -LiteralPath C:\MainData\code\Codex_project\deepresearch-main\src\main\java\com\alibaba\cloud\ai\example\deepresearch\model\ApiResponse.java`
- `mvn "-Dtest=SimpleResearchRunnerTest,ChatControllerTest" test` with `JAVA_HOME=C:\WorkResources\JDKs\JDK17` passed 11 tests.
- `mvn test` with `JAVA_HOME=C:\WorkResources\JDKs\JDK17` passed 34 tests.
- `git diff --check` passed with only expected Windows LF-to-CRLF warnings.
- `git commit -m <Chinese message meaning add chat stop lifecycle endpoint>` created the stage commit before final handoff metadata amends.

## Verification

- `mvn "-Dtest=SimpleResearchRunnerTest,ChatControllerTest" test` with Java 17 passed 11 tests.
- `mvn test` with Java 17 passed 34 tests.
- Existing real-provider workflow tests in `ResearchControllerLlmWorkflowTest` passed during the full suite.
- No manual backend service was started outside Maven tests.
- `git diff --check` passed with only expected line-ending conversion warnings.

## Known Failures / Blockers

- No upstream Git branch is configured.
- Current runner does not track running task futures or subscriptions, so full interruption of in-flight LLM/search calls remains deferred.
- Paused state is process-local and has no expiration.
- Real LLM/provider tests can fail from external API rate limits or network timeouts.

## Next Actions

- Future stage: add running-task cancellation only after introducing a clear task registry/subscription model.
- Future stage: consider `/chat/status`, pause expiration, or frontend controls on top of this lifecycle boundary.

## Open Questions

- None for this stage.

## Avoid / Do Not Redo

- Do not edit `C:/MainData/code/Codex_project/deepresearch-main`.
- Do not migrate to Spring AI Alibaba Graph yet.
- Do not add Redis, persistent databases, RAG, MCP, frontend changes, or Graph saver/checkpoint infrastructure in this stage.
- Do not introduce mock agent output, mock search, or fake search context in production code.
- Do not commit `.local`, `target`, `.idea`, API keys, generated SSE/curl output, or logs.
- Do not leave manually started backend services running after tests.
- Do not redo the already completed human feedback plan gate unless a test proves it must change for stop support.

## Resume Prompt
Resume task add-chat-stop-session-lifecycle. Read .codex/tasks/add-chat-stop-session-lifecycle.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
