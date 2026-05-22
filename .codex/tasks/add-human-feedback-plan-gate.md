# Task Handoff: add-human-feedback-plan-gate
Updated: 2026-05-22 10:31:00 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 9ffccdbaad015db3f5b31ac1ba1d6dd9d20f9461
Current Commit: 9ffccdbaad015db3f5b31ac1ba1d6dd9d20f9461 before the implementation commit

## Project Mainline

- This project is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under `C:/MainData/code/Codex_project/M-agent`.
- The long-term direction is to imitate `C:/MainData/code/Codex_project/deepresearch-main` in a deliberately reduced, runnable backend-first form.
- The project is growing DeepResearch workflow semantics in `SimpleResearchRunner` before migrating to Spring AI Alibaba Graph.
- Completed stages removed mock runtime paths, added DeepResearch-compatible SSE envelopes, upgraded Plan/Step state, added a lightweight `research_team` control node, added Bocha-only real web search through an `information` node, added a real `processor` node for PROCESSING steps, and now added a human feedback plan gate for `/chat`.
- The current stable auto-accepted backend loop is: planner -> information -> research_team -> researcher -> research_team -> processor -> research_team -> reporter -> `__END__`.
- The current interactive loop is: `/chat/stream` with `auto_accepted_plan=false` runs planner -> `human_feedback` waiting; `/chat/resume` with `feedback=true` continues execution; `/chat/resume` with `feedback=false` replans with `feedback_content` and waits again.
- New feature work must not introduce mock agents, mock search, fabricated search results, or local secret leaks.

## Stage Role in Mainline

- This stage adds a minimal human-in-the-loop plan gate after planning.
- It exists because the backend could already complete an end-to-end real workflow, but users could not inspect, accept, or revise the generated plan before web search and execution began.
- It aligns with `deepresearch-main`'s `human_feedback` node and `/chat/resume` contract at the semantic level while keeping the local MVP small and WebFlux/SSE-first.

## Mainline Progression

- `add-research-team` introduced a controlled loop around step execution.
- `add-information-node-bocha-search` added real Bocha `information` search before execution.
- `add-processor-node-search-context` split PROCESSING into a dedicated `processor` path that consumes prior observations and site information.
- `add-human-feedback-plan-gate` evolves the workflow from automatic run-to-completion to interactive controllable research: planner emits a plan, the workflow pauses when auto-accept is disabled, the user resumes with accept or revision feedback, and rejected feedback regenerates the plan before another review gate.
- Future stages can build on this pause/resume state boundary to add `/chat/stop`, expiration for paused state, richer session state, frontend plan editing, and eventually Spring AI Alibaba Graph.

## Related Stage Handoffs

- `add-research-team`: completed lightweight `ResearchTeamNode` and runner loop.
- `add-information-node-bocha-search`: completed Bocha-only `InformationNode` and real HTTP verification.
- `add-processor-node-search-context`: completed processor agent/node/routing and real HTTP verification.

## Goal

- Add a lightweight human feedback plan gate so M-agent can pause after planner output, let the user accept or reject the plan, and resume execution or regenerate the plan with feedback.

## Task Theme / User Intent

- The user wanted to resume the `add-human-feedback-plan-gate` stage and continue aligning `M-agent` with `C:/MainData/code/Codex_project/deepresearch-main`.
- The reference project has HITL via `HumanFeedbackNode`, `HumanFeedbackDispatcher`, `/chat/resume`, and Graph `interruptBefore("human_feedback")`.
- The local implementation imitates those semantics without copying Graph, MemorySaver, Redis, frontend, RAG, MCP, or parallel executor infrastructure.

## Acceptance Criteria

- Add a minimal local human feedback concept in the MVP runner.
- Support the existing `auto_accepted_plan` / `autoAcceptPlan` chat request option.
- When auto-accept is disabled, `/chat/stream` emits planner output plus a visible `human_feedback` waiting event, then stops before `information`, `researcher`, `processor`, and `reporter`.
- Add `/chat/resume` accepting `session_id`, `thread_id`, `feedback`, and `feedback_content`.
- If `feedback=true`, resume from saved state and continue information -> research_team -> researcher/processor -> reporter.
- If `feedback=false`, carry `feedback_content` into planner context, regenerate the plan, and pause at `human_feedback` again before execution.
- Keep saved state in memory for the MVP; do not introduce Redis or Graph checkpointers.
- Preserve existing auto-accepted behavior for current clients and tests.
- Add focused tests for pause after planning, accept resume, reject-and-replan behavior, missing thread errors, and existing auto-run compatibility.
- Run Java 17 verification with `mvn test`.
- Commit the completed stage with a Chinese commit message.

## Scope

- Work only in `C:/MainData/code/Codex_project/M-agent`.
- Inspect `C:/MainData/code/Codex_project/deepresearch-main` as read-only guidance.
- Keep this stage backend-focused and minimal.
- Use in-memory state management for the MVP; do not add Redis, database tables, Graph saver infrastructure, frontend code, RAG, MCP, or full Spring AI Alibaba Graph migration.
- Preserve real provider paths and do not introduce mock production behavior.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/AGENTS.md` only if project instructions need a minimal Chinese update
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-human-feedback-plan-gate.md`

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
- No upstream branch is configured for `main`.
- Implementation is complete and ready to commit after this handoff update.
- `/api/research/stream` remains auto-run only and unchanged at the API contract level.
- `/chat/stream` now branches on `ChatRequest.autoAcceptedPlan()`.
- `SimpleResearchRunner` now stores paused states in memory by `threadId`.
- Missing paused state on resume returns a structured `human_feedback` error event.
- `PlannerAgent` now has a feedback-aware overload; `LlmPlannerAgent` injects human feedback into the planner prompt only for rejected/replanned runs.
- No manual backend service was left running; verification used Maven tests and Spring Boot test-managed random ports.

## Completed

- Added `FeedbackRequest` for `/chat/resume` with `session_id`, `thread_id`, `feedback`, and `feedback_content` JSON fields.
- Added `ResumeDecision` for runner-level resume intent.
- Added in-memory paused state handling in `SimpleResearchRunner`.
- Added `runUntilPlanGate(...)` to emit planner events and a `human_feedback` waiting event.
- Added `resume(...)` to accept a plan and continue execution, or reject a plan, replan with feedback, and pause again.
- Updated `ChatController` to honor `auto_accepted_plan=false` and expose `/chat/resume` using the existing chat SSE envelope.
- Updated planner flow so rejected feedback reaches the real LLM planner prompt path.
- Added runner tests for pause, accepted resume, rejected replan-and-wait, missing paused state, and preserved auto-run behavior.
- Added controller tests for `/chat/stream` plan gate routing and `/chat/resume` request/envelope behavior.

## Decisions

- Human feedback applies initially to `/chat/stream` and `/chat/resume`, matching the reference project's chat-oriented HITL contract.
- `/api/research/stream` remains unchanged for compatibility and simple backend verification.
- Rejected feedback regenerates the plan and pauses again, matching the reference Graph behavior with `interruptBefore("human_feedback")`.
- Paused state is removed when resume begins. If feedback is rejected, the newly replanned state is stored again for the next resume.
- No expiration, stop endpoint, persistence, Redis, frontend, or Graph checkpointing was added in this stage.

## Evidence / References

- Reference docs: `C:/MainData/code/Codex_project/deepresearch-main/README.md`, `README_zh.md`, and `DeepResearch.http` mention HITL and `/chat/resume`.
- Reference node: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/HumanFeedbackNode.java`.
- Reference dispatcher: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/dispatcher/HumanFeedbackDispatcher.java`.
- Reference controller: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/controller/ChatController.java`.
- Reference graph wiring: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/config/DeepResearchConfiguration.java`.
- Local request model: `src/main/java/top/lanshan/manmu/model/ChatRequest.java`.
- Local resume request model: `src/main/java/top/lanshan/manmu/model/FeedbackRequest.java`.
- Local chat controller: `src/main/java/top/lanshan/manmu/api/ChatController.java`.
- Local runner: `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`.
- Local state: `src/main/java/top/lanshan/manmu/model/ResearchState.java`.
- Local planner: `src/main/java/top/lanshan/manmu/agent/PlannerAgent.java`, `src/main/java/top/lanshan/manmu/agent/LlmPlannerAgent.java`, and `src/main/java/top/lanshan/manmu/node/PlannerNode.java`.

## Files Touched

- `.codex/tasks/add-human-feedback-plan-gate.md`
- `src/main/java/top/lanshan/manmu/agent/LlmPlannerAgent.java`
- `src/main/java/top/lanshan/manmu/agent/PlannerAgent.java`
- `src/main/java/top/lanshan/manmu/api/ChatController.java`
- `src/main/java/top/lanshan/manmu/model/FeedbackRequest.java`
- `src/main/java/top/lanshan/manmu/model/ResearchState.java`
- `src/main/java/top/lanshan/manmu/node/PlannerNode.java`
- `src/main/java/top/lanshan/manmu/runner/ResumeDecision.java`
- `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- `src/test/java/top/lanshan/manmu/api/ChatControllerTest.java`
- `src/test/java/top/lanshan/manmu/runner/SimpleResearchRunnerTest.java`

## Commands Run

- `Get-Content -LiteralPath .codex/tasks/add-human-feedback-plan-gate.md`
- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` failed because no upstream is configured.
- `Get-Content -Encoding UTF8 -Raw AGENTS.md`
- `rg --files src/main/java src/test/java`
- `git log --oneline -8`
- Read local `ChatRequest`, `ResearchRequest`, `ChatController`, `ResearchController`, `SimpleResearchRunner`, `PlannerNode`, `PlannerAgent`, `ResearchState`, `ResearchEvent`, `ChatStreamResponse`, `GraphId`, `LlmPlannerAgent`, `SimpleResearchRunnerTest`, and `ResearchControllerLlmWorkflowTest`.
- `rg -n "ChatController|chat/stream|WebTestClient|SimpleResearchRunner|PlannerAgent|autoAccepted|auto_accepted|ResearchRequest" src/test/java src/main/java`
- Read reference `HumanFeedbackDispatcher`, `HumanFeedbackNode`, `DeepResearchConfiguration`, and `InformationNode`.
- `mvn -Dtest=SimpleResearchRunnerTest test` with Java 17 passed 5 tests.
- `mvn '-Dtest=SimpleResearchRunnerTest,ChatControllerTest' test` with Java 17 passed 7 tests.
- `mvn '-Dtest=ChatControllerTest' test` with Java 17 passed 2 tests.
- `git diff --check` passed with only line-ending warnings.
- `mvn test` with Java 17 passed 30 tests. It was run twice after implementation; both runs passed.

## Verification

- Focused runner tests passed: 5 tests, 0 failures.
- Focused runner plus controller tests passed: 7 tests, 0 failures.
- Focused controller tests passed after display-title polish: 2 tests, 0 failures.
- Full Java 17 verification passed twice with `mvn test`: 30 tests, 0 failures, 0 errors, 0 skipped.
- Existing real-provider workflow tests in `ResearchControllerLlmWorkflowTest` passed during the full suite.
- No manual backend service was started outside Maven tests.

## Known Failures / Blockers

- No upstream Git branch is configured for `main`.
- In-memory paused state is intentionally process-local and will be lost on application restart.
- Paused state has no expiration or stop/cancel endpoint yet.
- `/api/research/stream` does not expose the human feedback plan gate in this stage by design.

## Next Actions

- Commit the completed implementation and handoff with a Chinese commit message.
- For a future stage, consider adding `/chat/stop` or paused-state expiration on top of the new in-memory resume boundary.
- For a future UI stage, render the `human_feedback` waiting event and call `/chat/resume` with accept/reject plan feedback.

## Open Questions

- Should a later stage expose plan review for `/api/research/stream`, or keep HITL chat-only until the Graph migration?
- What expiration policy should paused states use once sessions can accumulate?
- Should rejected plan history be retained for the frontend, or is replacing the plan sufficient for the MVP?

## Avoid / Do Not Redo

- Do not edit `C:/MainData/code/Codex_project/deepresearch-main`.
- Do not migrate to Spring AI Alibaba Graph yet.
- Do not add Redis, persistent databases, RAG, MCP, frontend changes, or Graph saver/checkpoint infrastructure in this stage.
- Do not introduce mock agent output, mock search, or fake search context in production code.
- Do not commit `.local`, `target`, `.idea`, API keys, generated SSE/curl output, or logs.
- Do not leave manually started backend services running after tests.

## Resume Prompt
Resume task add-human-feedback-plan-gate. Read .codex/tasks/add-human-feedback-plan-gate.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
