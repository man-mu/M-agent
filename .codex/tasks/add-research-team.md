# Task Handoff: add-research-team
Updated: 2026-05-21 after implementing lightweight ResearchTeam
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 30a7449d798269b020761a206a0b112a96e2c5449
Current Commit: aecb7fa30b959d8bc462da5c04cb95f33c968ff0

## Project Mainline

- This project is a Java 17 / Spring Boot 3.4.x DeepResearch-lite backend under `C:/MainData/code/Codex_project/M-agent`.
- The long-term direction is to imitate the reference project at `C:/MainData/code/Codex_project/deepresearch-main` in a deliberately reduced form.
- The current strategy is to grow the business workflow semantics first while keeping `SimpleResearchRunner`, then migrate to Spring AI Alibaba Graph later when interrupt/resume/conditional edges/parallelism are needed.
- This stage has now added a lightweight `research_team` control node that makes Plan/Step execution status drive routing before the project adopts a real graph engine.
- The project must use real model/API paths. New feature work must not introduce mock implementations or mock data.

## Stage Role in Mainline

- This stage adds a lightweight `ResearchTeamNode` layer before migrating to Spring AI Alibaba Graph.
- It makes the current Plan/Step status model useful for workflow control: decide whether steps remain, route execution to researcher-like handling, and decide when the workflow may proceed to reporter.

## Mainline Progression

- Earlier stages initialized the Git repo, removed mock runtime paths, added `/chat/stream` DeepResearch-compatible SSE envelopes, and upgraded the Plan/Step state model.
- This stage turned the upgraded Step state fields into actual orchestration behavior.
- `ResearchTeamNode` now emits `research_team` decision events and records a `ResearchTeamDecision` on `ResearchState`.
- `SimpleResearchRunner` now loops through `research_team -> researcher` until the team routes to reporter.
- `ResearcherNode` now executes only the pending step type selected by the team, so RESEARCH steps complete before PROCESSING steps.
- `PlannerOutputMapper` now tolerates real model planner output that omits a top-level title by deriving a conservative title from the user query while still requiring real steps.
- Future stages can add `InformationNode`, real search, human feedback, `/chat/stop`, `/chat/resume`, and eventually Spring AI Alibaba Graph.

## Related Stage Handoffs

- None known. This is the first `.codex/tasks` handoff in this repository.

## Goal

- Add a lightweight `ResearchTeamNode` to the current backend so the workflow more closely resembles the reference DeepResearch control flow while still using `SimpleResearchRunner`.
- Status: implemented and verified.

## Task Theme / User Intent

- The user is building a personal DeepResearch-lite project for learning and implementation.
- The user asked whether to add `ResearchTeam` or migrate to Spring AI Alibaba Graph next; the chosen path is to add `ResearchTeam` first.
- The user plans to start a new session and continue by adding `ResearchTeam`.

## Acceptance Criteria

- Done: added a real, non-mock `ResearchTeamNode` orchestration layer.
- Done: kept the app runnable on the real LLM/provider path.
- Done: used existing `ResearchPlan` and `ResearchStep` fields: `executionStatus`, `executionRes`, `stepType`, `needWebSearch`.
- Done: intentionally extended `/api/research/stream` and `/chat/stream` with visible `research_team` decision events.
- Done: added tests for the new orchestration behavior.
- Done: ran `mvn test` with Java 17 successfully.
- Done: committed the completed small stage with Chinese commit message `添加ResearchTeam轻量编排`.

## Scope

- Work in `C:/MainData/code/Codex_project/M-agent`.
- Inspect the reference project at `C:/MainData/code/Codex_project/deepresearch-main` as read-only guidance.
- Keep this stage backend-focused; do not import the Vue frontend, Redis, RAG, MCP, or full Spring AI Alibaba Graph yet.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/AGENTS.md` only if project instructions need a minimal update
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-research-team.md` if updating this handoff

### Read-only Reference Roots

- `C:/MainData/code/Codex_project/deepresearch-main`
- Existing Git history in `C:/MainData/code/Codex_project/M-agent`

### Forbidden Write Roots

- `C:/MainData/code/Codex_project/deepresearch-main`
- `C:/MainData/code/Codex_project/M-agent/.local`
- `C:/MainData/code/Codex_project/M-agent/target`
- `C:/MainData/code/Codex_project/M-agent/.idea`
- Any file containing API keys or local secrets

## Current State

- Git branch: `main`.
- Working tree was clean after implementation commit `aecb7fa30b959d8bc462da5c04cb95f33c968ff0`.
- A later metadata commit updates this handoff status only.
- No upstream is configured for `main`.
- Current runner is a minimal controlled loop: planner -> research_team -> researcher -> research_team -> reporter.
- `/chat/stream` exists and wraps internal `ResearchEvent` values into a DeepResearch-like SSE envelope.
- `ResearchPlan` now has `hasEnoughContext`, `thought`, and `steps`.
- `ResearchStep` now has `needWebSearch`, `stepType`, `executionRes`, and `executionStatus`.

## Completed

- Git initialized and baseline committed.
- Runtime mock agent/search path removed in earlier work.
- `/chat/stream` added with request fields compatible with the reference shape: `session_id`, `thread_id`, `max_step_num`, `auto_accepted_plan`, `enable_deepresearch`, `query`.
- `/chat/stream` responses include `nodeName`, `graphId`, `displayTitle`, `content`, and `siteInformation`.
- Plan/Step state model upgraded.
- `ResearcherNode` writes `processing`, `completed`, or `error` into each step.
- Added `ResearchTeamRoute` and `ResearchTeamDecision`.
- Added `ResearchTeamNode` with terminal-step detection and routing to either `RESEARCHER` or `REPORTER`.
- Updated `ResearchState` to carry the latest `ResearchTeamDecision`.
- Updated `SimpleResearchRunner` to require named nodes and execute the `research_team -> researcher` loop until reporter route.
- Updated `ResearcherNode` to require a team decision and execute only pending steps of the selected `StepType`.
- Updated `/chat/stream` display title mapping for `research_team`.
- Updated the researcher prompt so the same real LLM agent can handle both RESEARCH and PROCESSING steps in this MVP.
- Updated real LLM workflow tests for the new `research_team` event sequence and reduced repeated DashScope pressure by using DeepSeek in the envelope test.
- Added planner title fallback for real model output that omits the top-level plan title.
- Project-level `AGENTS.md` updated with:
  - commit after each small phase
  - Chinese commit messages
  - no mock for new feature work; use real production-required data

## Decisions

- Do not migrate to Spring AI Alibaba Graph yet.
- Add `ResearchTeam` first to solidify workflow semantics before changing execution engines.
- Keep `SimpleResearchRunner` for now.
- Do not introduce mock data or mock services for new features.
- Real search is not yet implemented; `needWebSearch` exists but should not be faked.
- In this MVP, PROCESSING steps are handled by the existing real researcher LLM path rather than a new mock coder/processor agent.
- Missing top-level planner titles are recoverable from the user query; missing or empty real steps still fail.

## Evidence / References

- Reference graph configuration: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/config/DeepResearchConfiguration.java`
- Reference `ResearchTeamNode`: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/ResearchTeamNode.java`
- Reference `ParallelExecutorNode`: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/ParallelExecutorNode.java`
- Current runner: `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- Current nodes: `src/main/java/top/lanshan/manmu/node`
- Current Plan/Step models: `src/main/java/top/lanshan/manmu/model/ResearchPlan.java`, `src/main/java/top/lanshan/manmu/model/ResearchStep.java`
- Current ResearchTeam implementation: `src/main/java/top/lanshan/manmu/node/ResearchTeamNode.java`
- Current routing models: `src/main/java/top/lanshan/manmu/model/ResearchTeamDecision.java`, `src/main/java/top/lanshan/manmu/model/ResearchTeamRoute.java`
- Current team tests: `src/test/java/top/lanshan/manmu/node/ResearchTeamNodeTest.java`
- Project instructions: `AGENTS.md`

## Files Touched

- This handoff writes `.codex/tasks/add-research-team.md`.
- This stage touched:
  - `src/main/java/top/lanshan/manmu/agent/LlmPlannerAgent.java`
  - `src/main/java/top/lanshan/manmu/agent/PlannerOutputMapper.java`
  - `src/main/java/top/lanshan/manmu/api/ChatController.java`
  - `src/main/java/top/lanshan/manmu/model/ResearchState.java`
  - `src/main/java/top/lanshan/manmu/model/ResearchTeamDecision.java`
  - `src/main/java/top/lanshan/manmu/model/ResearchTeamRoute.java`
  - `src/main/java/top/lanshan/manmu/node/ReporterNode.java`
  - `src/main/java/top/lanshan/manmu/node/ResearcherNode.java`
  - `src/main/java/top/lanshan/manmu/node/ResearchTeamNode.java`
  - `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
  - `src/main/resources/prompts/researcher.md`
  - `src/test/java/top/lanshan/manmu/agent/PlannerOutputMapperTest.java`
  - `src/test/java/top/lanshan/manmu/api/ResearchControllerLlmWorkflowTest.java`
  - `src/test/java/top/lanshan/manmu/node/ResearchTeamNodeTest.java`
- Prior completed stages touched:
  - `AGENTS.md`
  - `src/main/java/top/lanshan/manmu/api/ChatController.java`
  - `src/main/java/top/lanshan/manmu/model/ChatRequest.java`
  - `src/main/java/top/lanshan/manmu/model/ChatStreamResponse.java`
  - `src/main/java/top/lanshan/manmu/model/GraphId.java`
  - `src/main/java/top/lanshan/manmu/model/ResearchPlan.java`
  - `src/main/java/top/lanshan/manmu/model/ResearchStep.java`
  - `src/main/java/top/lanshan/manmu/model/StepType.java`
  - `src/main/java/top/lanshan/manmu/node/ResearcherNode.java`
  - related agent, prompt, and tests

## Commands Run

- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name @{upstream}` failed because no upstream is configured.
- `git merge-base HEAD @{upstream}` failed because no upstream is configured.
- Earlier verified command in this session: `mvn test` with `JAVA_HOME=C:\WorkResources\JDKs\JDK17`.
- `mvn test -Dtest=ResearchTeamNodeTest`
- `mvn test -Dtest=PlannerOutputMapperTest`
- `mvn test`

## Verification

- Latest full verification:
  - Command: `mvn test` with `JAVA_HOME=C:\WorkResources\JDKs\JDK17`.
  - Result: `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- Additional focused verification:
  - `mvn test -Dtest=ResearchTeamNodeTest`: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
  - `mvn test -Dtest=PlannerOutputMapperTest`: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- During verification, one earlier full run failed because DashScope returned 429 during reporter generation, and another failed because a real DashScope planner response omitted the top-level title. The title issue was fixed; the later full run passed.

## Known Failures / Blockers

- No upstream Git branch configured.
- Some terminal output may show Chinese text as mojibake, but reading `AGENTS.md` with UTF-8 works.
- Real API tests depend on `.local/model-providers.json` containing valid DashScope and DeepSeek keys.
- `needWebSearch` is present but real search is not implemented yet. Do not fake search data.
- Real provider tests can still be temporarily rate-limited by external model APIs.

## Next Actions

- Next feature stage: add a lightweight `InformationNode` or real search integration without faking `needWebSearch` results.
- Later stage: split PROCESSING steps into a dedicated real processor/coder node before migrating to Spring AI Alibaba Graph.

## Open Questions

- Whether to add a separate `InformationNode` next or prioritize real search first.
- Whether future `ResearchTeamNode` events need richer frontend payloads beyond the current decision record.
- Whether `StepType.PROCESSING` should become a dedicated real processor/coder node in the next backend stage.

## Avoid / Do Not Redo

- Do not migrate to Spring AI Alibaba Graph in the next step unless the user explicitly changes direction.
- Do not reintroduce mock agents, mock search, or fabricated search results.
- Do not edit `deepresearch-main`; use it only as a reference.
- Do not commit `.local`, `target`, `.idea`, or secrets.

## Resume Prompt
Resume task add-research-team. Read .codex/tasks/add-research-team.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
