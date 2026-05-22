# Task Handoff: add-human-feedback-plan-gate
Updated: 2026-05-22 00:00:00 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: f887169d09e4cdd1325006768b8bea740f545051
Current Commit: f887169d09e4cdd1325006768b8bea740f545051

## Project Mainline

- This project is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under `C:/MainData/code/Codex_project/M-agent`.
- The long-term direction is to imitate `C:/MainData/code/Codex_project/deepresearch-main` in a deliberately reduced, runnable backend-first form.
- The project is growing DeepResearch workflow semantics in `SimpleResearchRunner` before migrating to Spring AI Alibaba Graph.
- Completed stages removed mock runtime paths, added DeepResearch-compatible SSE envelopes, upgraded Plan/Step state, added a lightweight `research_team` control node, added Bocha-only real web search through an `information` node, and added a real `processor` node for PROCESSING steps.
- The current stable backend loop is: planner -> information -> research_team -> researcher -> research_team -> processor -> research_team -> reporter -> `__END__`.
- The next mainline direction is to make that automatic loop controllable by adding a human feedback plan gate, aligned with the reference project's HITL behavior but without migrating to Graph yet.
- New feature work must not introduce mock agents, mock search, fabricated search results, or local secret leaks.

## Stage Role in Mainline

- This stage should add a minimal human-in-the-loop plan gate after planning.
- It exists because the backend can now complete an end-to-end real workflow, but users cannot inspect, accept, or revise the generated plan before web search and execution begin.
- It should align with `deepresearch-main`'s `human_feedback` node and `/chat/resume` contract at the semantic level while keeping the local MVP small and WebFlux/SSE-first.

## Mainline Progression

- `add-research-team` introduced a controlled loop around step execution.
- `add-information-node-bocha-search` added real Bocha `information` search before execution.
- `add-processor-node-search-context` split PROCESSING into a dedicated `processor` path that consumes prior observations and site information.
- `add-human-feedback-plan-gate` should evolve the workflow from "automatic run-to-completion" to "interactive controllable research": planner emits plan, workflow pauses when auto-accept is disabled, user resumes with accept or revision feedback, then execution continues or replans.
- Future stages can build on this pause/resume state boundary to add `/chat/stop`, richer session state, frontend plan editing, and eventually Spring AI Alibaba Graph.

## Related Stage Handoffs

- `add-research-team`: completed lightweight `ResearchTeamNode` and runner loop.
- `add-information-node-bocha-search`: completed Bocha-only `InformationNode` and real HTTP verification.
- `add-processor-node-search-context`: completed processor agent/node/routing and real HTTP verification.

## Goal

- Add a lightweight human feedback plan gate so M-agent can pause after planner output, let the user accept or reject the plan, and resume execution or regenerate the plan with feedback.

## Task Theme / User Intent

- The user wants to start a new session and align with `C:/MainData/code/Codex_project/deepresearch-main` for the next stage named `add-human-feedback-plan-gate`.
- The user specifically asked whether the reference project has human feedback; investigation confirmed it has HITL via `HumanFeedbackNode`, `HumanFeedbackDispatcher`, `/chat/resume`, and Graph `interruptBefore("human_feedback")`.
- The intended implementation should imitate reference semantics, not copy the full Graph, MemorySaver, Redis, frontend, RAG, MCP, or parallel executor stack.

## Acceptance Criteria

- Add a minimal local human feedback concept, likely a `HumanFeedbackNode` or equivalent stage in the MVP runner.
- Support a request option matching the existing API style, likely `auto_accepted_plan` / `autoAcceptPlan`, so callers can choose whether the generated plan is auto-accepted.
- When auto-accept is disabled, `/chat/stream` and/or `/api/research/stream` should emit planner output plus a visible `human_feedback` waiting event, then stop before `information`, `researcher`, `processor`, and `reporter`.
- Add a resume endpoint, likely `/chat/resume` first, that accepts `session_id`, `thread_id`, `feedback`, and `feedback_content`.
- If `feedback=true`, resume from the saved state and continue information -> research_team -> researcher/processor -> reporter.
- If `feedback=false`, carry `feedback_content` into planner context and regenerate the plan before reaching execution.
- Keep saved state in memory for the MVP; do not introduce Redis or Graph checkpointers in this stage.
- Preserve existing auto-accepted behavior for current clients and tests.
- Add focused tests for pause after planning, accept resume, reject-and-replan behavior, missing thread errors, and existing auto-run compatibility.
- Run Java 17 verification with `mvn test`; if real provider tests fail due external network or provider limits, record exact failure and run focused non-network tests.
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
- Current commit: `f887169d09e4cdd1325006768b8bea740f545051`.
- Working tree was clean before this new handoff file was written.
- No upstream is configured for `main`.
- Backend default port is `8080`.
- Project instruction says any manually started backend service must be closed after testing.
- Current M-agent has no human feedback plan gate yet.
- Current `ChatRequest` already has `autoAcceptedPlan` fields in the public envelope, but existing runner behavior still auto-runs through the full workflow.
- Current runner requires named nodes `planner`, `information`, `research_team`, `researcher`, `processor`, and `reporter`.
- Current `ResearchState` is in-process per run and has no session registry, checkpoint, paused-state store, or resume contract yet.

## Completed

- Confirmed `deepresearch-main` has HITL support.
- Identified key reference files:
  - `HumanFeedbackNode`
  - `HumanFeedbackDispatcher`
  - `ChatController`
  - `FeedbackRequest`
  - `DeepResearchConfiguration`
  - `DeepResearch.http`
- Confirmed reference semantics:
  - Graph is compiled with `interruptBefore("human_feedback")`.
  - `/chat/resume` accepts `feedback` and `feedback_content`.
  - `feedback=true` proceeds to `research_team`.
  - `feedback=false` stores `feedback_content`, clears resume state, and routes back to `planner`.
- Completed prior processor stage and committed it.
- Created this next-stage handoff so a fresh session can continue directly.

## Decisions

- Recommended next stage is `add-human-feedback-plan-gate` before `/chat/stop`, richer frontend rendering, or Graph migration.
- Implement semantic alignment first; do not migrate to Spring AI Alibaba Graph in this stage.
- Use in-memory saved `ResearchState` or a small state registry for the MVP rather than Redis or Graph checkpoints.
- Preserve current auto-run behavior by default so existing clients and tests remain compatible.
- Start with `/chat/resume` compatibility because the reference project exposes human feedback through `/chat/resume`.

## Evidence / References

- Reference docs: `C:/MainData/code/Codex_project/deepresearch-main/README.md`, `README_zh.md`, and `DeepResearch.http` mention HITL and `/chat/resume`.
- Reference node: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/HumanFeedbackNode.java`
- Reference dispatcher: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/dispatcher/HumanFeedbackDispatcher.java`
- Reference controller: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/controller/ChatController.java`
- Reference request model: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/model/req/FeedbackRequest.java`
- Reference graph wiring: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/config/DeepResearchConfiguration.java`
- Current M-agent chat request: `src/main/java/top/lanshan/manmu/model/ChatRequest.java`
- Current M-agent chat controller: `src/main/java/top/lanshan/manmu/api/ChatController.java`
- Current M-agent runner: `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- Current M-agent state: `src/main/java/top/lanshan/manmu/model/ResearchState.java`
- Current M-agent plan and steps: `src/main/java/top/lanshan/manmu/model/ResearchPlan.java`, `src/main/java/top/lanshan/manmu/model/ResearchStep.java`

## Files Touched

- `.codex/tasks/add-human-feedback-plan-gate.md`

## Commands Run

- `Get-Content -Raw C:\Users\20232\.codex\skills\task-handoff\SKILL.md`
- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` failed because no upstream is configured.
- `git merge-base HEAD '@{upstream}'` failed because no upstream is configured.
- `Get-Content -Encoding UTF8 -Raw AGENTS.md`
- `Get-ChildItem .codex\tasks -File`
- `Get-Content -Raw .codex\tasks\add-processor-node-search-context.md`
- `git log --oneline -5`
- `rg -n "human|feedback|interrupt|resume|HITL|approval|approve|plan feedback|human_feedback|user_feedback" C:\MainData\code\Codex_project\deepresearch-main`
- `rg --files C:\MainData\code\Codex_project\deepresearch-main | rg "(?i)(feedback|human|interrupt|resume|graph|controller|chat|plan)"`
- Read reference `HumanFeedbackNode`, `HumanFeedbackDispatcher`, `ChatController`, `DeepResearchConfiguration`, `FeedbackRequest`, and `DeepResearch.http`.

## Verification

- No implementation verification has run for this new stage because implementation has not started.
- Current repository state before this handoff was clean.
- Most recent prior implementation verification from `add-processor-node-search-context`:
  - `mvn test` with Java 17 passed 24 tests.
  - Manual real HTTP SSE verification on temporary port `18082` observed planner -> information -> researcher -> processor -> reporter -> `__END__`.
  - Temporary backend service was stopped and port `18082` was released.

## Known Failures / Blockers

- No upstream Git branch configured.
- Real LLM/provider tests can fail from external API rate limits or network timeouts.
- This new stage needs a careful state-lifetime design because current `ResearchState` is created inside one `SimpleResearchRunner.run(...)` call.
- A rejected plan needs a clean way to pass `feedback_content` back into planner prompt context; current local `PlannerAgent.plan(query, maxSteps)` does not yet accept feedback text.
- Some previous handoff content may show Chinese mojibake if read without UTF-8; use `Get-Content -Encoding UTF8` for Chinese docs and source comments.

## Next Actions

- Inspect current `ChatRequest`, `ResearchRequest`, `ChatController`, `ResearchController`, `SimpleResearchRunner`, `PlannerNode`, `PlannerAgent`, and tests to choose the smallest in-memory pause/resume design.
- Implement a minimal saved-state/resume path: auto-accepted requests keep current behavior; non-auto-accepted chat requests emit `human_feedback` waiting after planner and store paused state by `threadId`; `/chat/resume` accepts or rejects the plan.
- Add focused tests for accept resume, reject-and-replan, missing paused state, and existing auto-run compatibility; then run Java 17 verification.

## Open Questions

- Should human feedback initially apply only to `/chat/stream`, matching the reference project, or also to `/api/research/stream`?
- Should `feedback=false` preserve the old plan as history, or simply replace it after replanning for the MVP?
- Should paused state expire or be removed immediately after resume in this stage?

## Avoid / Do Not Redo

- Do not edit `C:/MainData/code/Codex_project/deepresearch-main`.
- Do not migrate to Spring AI Alibaba Graph yet.
- Do not add Redis, persistent databases, RAG, MCP, frontend changes, or Graph saver/checkpoint infrastructure in this stage.
- Do not introduce mock agent output, mock search, or fake search context in production code.
- Do not commit `.local`, `target`, `.idea`, API keys, generated SSE/curl output, or logs.
- Do not leave manually started backend services running after tests.

## Resume Prompt
Resume task add-human-feedback-plan-gate. Read .codex/tasks/add-human-feedback-plan-gate.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
