# Task Handoff: add-research-team
Updated: 2026-05-21
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 9f8a76c440a917ebee40f66d0750be9905605449
Current Commit: 9f8a76c440a917ebee40f66d0750be9905605449

## Project Mainline

- This project is a Java 17 / Spring Boot 3.4.x DeepResearch-lite backend under `C:/MainData/code/Codex_project/M-agent`.
- The long-term direction is to imitate the reference project at `C:/MainData/code/Codex_project/deepresearch-main` in a deliberately reduced form.
- The current strategy is to grow the business workflow semantics first while keeping `SimpleResearchRunner`, then migrate to Spring AI Alibaba Graph later when interrupt/resume/conditional edges/parallelism are needed.
- The project must use real model/API paths. New feature work must not introduce mock implementations or mock data.

## Stage Role in Mainline

- The next stage should add a lightweight `ResearchTeam` layer before migrating to Spring AI Alibaba Graph.
- This stage should make the current Plan/Step status model useful for workflow control: decide whether steps remain, route execution to researcher-like handling, and decide when the workflow may proceed to reporter.

## Mainline Progression

- Earlier stages initialized the Git repo, removed mock runtime paths, added `/chat/stream` DeepResearch-compatible SSE envelopes, and upgraded the Plan/Step state model.
- The next stage should turn the upgraded Step state fields into actual orchestration behavior.
- Future stages can add `InformationNode`, real search, human feedback, `/chat/stop`, `/chat/resume`, and eventually Spring AI Alibaba Graph.

## Related Stage Handoffs

- None known. This is the first `.codex/tasks` handoff in this repository.

## Goal

- Add a lightweight `ResearchTeamNode` to the current backend so the workflow more closely resembles the reference DeepResearch control flow while still using `SimpleResearchRunner`.

## Task Theme / User Intent

- The user is building a personal DeepResearch-lite project for learning and implementation.
- The user asked whether to add `ResearchTeam` or migrate to Spring AI Alibaba Graph next; the chosen path is to add `ResearchTeam` first.
- The user plans to start a new session and continue by adding `ResearchTeam`.

## Acceptance Criteria

- Add a real, non-mock `ResearchTeamNode` or equivalent orchestration layer.
- Keep the app runnable on the real LLM/provider path.
- Use existing `ResearchPlan` and `ResearchStep` fields: `executionStatus`, `executionRes`, `stepType`, `needWebSearch`.
- Preserve existing `/api/research/stream` and `/chat/stream` behavior unless intentionally extending their events.
- Add or update tests for the new orchestration behavior.
- Run `mvn test` with Java 17.
- Commit each completed small stage with a Chinese commit message.

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
- Working tree was clean when this handoff was written.
- Current commit: `9f8a76c440a917ebee40f66d0750be9905605449`.
- No upstream is configured for `main`.
- Current runner is still linear: planner -> researcher -> reporter.
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

## Evidence / References

- Reference graph configuration: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/config/DeepResearchConfiguration.java`
- Reference `ResearchTeamNode`: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/ResearchTeamNode.java`
- Reference `ParallelExecutorNode`: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/ParallelExecutorNode.java`
- Current runner: `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- Current nodes: `src/main/java/top/lanshan/manmu/node`
- Current Plan/Step models: `src/main/java/top/lanshan/manmu/model/ResearchPlan.java`, `src/main/java/top/lanshan/manmu/model/ResearchStep.java`
- Project instructions: `AGENTS.md`

## Files Touched

- This handoff writes `.codex/tasks/add-research-team.md`.
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

## Verification

- Last known full verification before this handoff:
  - `mvn test`
  - Result: `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- No code changes beyond this handoff were made after that verification.

## Known Failures / Blockers

- No upstream Git branch configured.
- Some terminal output may show Chinese text as mojibake, but reading `AGENTS.md` with UTF-8 works.
- Real API tests depend on `.local/model-providers.json` containing valid DashScope and DeepSeek keys.
- `needWebSearch` is present but real search is not implemented yet. Do not fake search data.

## Next Actions

- Add `ResearchTeamNode` to inspect `ResearchPlan.steps()` and decide whether all steps are completed or whether research execution should continue.
- Integrate `ResearchTeamNode` into `SimpleResearchRunner` between planner/information-style handling and researcher/reporter; keep behavior linear and minimal for now.
- Add tests for `ResearchTeamNode` using real domain objects only, then run `mvn test` and commit with a Chinese commit message.

## Open Questions

- Whether to add a separate `InformationNode` in the same stage or leave it for the next stage.
- Whether `ResearchTeamNode` should emit visible SSE events immediately or remain an internal control node first.
- How to handle `StepType.PROCESSING` before a real coder/processor node exists.

## Avoid / Do Not Redo

- Do not migrate to Spring AI Alibaba Graph in the next step unless the user explicitly changes direction.
- Do not reintroduce mock agents, mock search, or fabricated search results.
- Do not edit `deepresearch-main`; use it only as a reference.
- Do not commit `.local`, `target`, `.idea`, or secrets.

## Resume Prompt
Resume task add-research-team. Read .codex/tasks/add-research-team.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
