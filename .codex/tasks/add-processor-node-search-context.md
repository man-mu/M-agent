# Task Handoff: add-processor-node-search-context
Updated: 2026-05-21 22:44:46 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: e124fa1327d84c95bc8e535ef19044e1d8e80de2
Current Commit: e124fa1327d84c95bc8e535ef19044e1d8e80de2

## Project Mainline

- This project is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under `C:/MainData/code/Codex_project/M-agent`.
- The long-term direction is to imitate `C:/MainData/code/Codex_project/deepresearch-main` in a deliberately reduced, runnable backend-first form.
- The project is growing DeepResearch workflow semantics before migrating to Spring AI Alibaba Graph.
- Prior stages removed mock runtime paths, added DeepResearch-compatible SSE envelopes, upgraded Plan/Step state, added a lightweight `research_team` control node, changed the default backend port to `8080`, and added Bocha-only real web search through an `information` node.
- The current mainline is: grow the minimal node semantics in `SimpleResearchRunner`, prove each stage through real provider paths, then migrate to Graph only after the simplified behavior is stable.
- New feature work must not introduce mock agents, mock search, fabricated search results, or local secret leaks.

## Stage Role in Mainline

- This stage should add a dedicated ProcessorNode for `StepType.PROCESSING`.
- It exists because the Bocha stage made real search available, but processing steps are still executed by `ResearcherNode`; that caused live workflow output where a PROCESSING step claimed it had no search context even though previous research observations and site information existed in `ResearchState`.
- The stage should imitate the reference project's PROCESSING/Coder direction, while keeping the local MVP backend small and not importing MCP, parallel executor, reflection, or Graph infrastructure.

## Mainline Progression

- `add-research-team` made Plan/Step execution status drive the controlled loop: planner -> research_team -> researcher -> research_team -> reporter.
- `add-information-node-bocha-search` extended the loop to: planner -> information -> research_team -> researcher -> research_team -> reporter and made `needWebSearch` call real Bocha.
- This stage should extend the loop to: planner -> information -> research_team -> researcher/processor -> research_team -> reporter.
- Future stages can add human feedback, `/chat/stop`, `/chat/resume`, richer frontend rendering of `siteInformation`, and eventually Spring AI Alibaba Graph once planner/information/researcher/processor/reporter semantics are stable.

## Related Stage Handoffs

- `add-research-team`: completed lightweight `ResearchTeamNode` and runner loop.
- `add-information-node-bocha-search`: completed Bocha-only `InformationNode` and real HTTP verification.

## Goal

- Add a lightweight real `ProcessorNode` so PROCESSING steps consume prior observations, Bocha `siteInformation`, and step context instead of being handled by the researcher path as if no context exists.

## Task Theme / User Intent

- The user asked to start a new session and implement: "imitate `C:/MainData/code/Codex_project/deepresearch-main` and go with adding ProcessorNode so PROCESSING steps consume real search context."
- The desired direction is not a full Graph migration. It is a small backend stage that repairs the workflow semantics exposed by the live Bocha test.
- The project is a personal DeepResearch-lite learning implementation, so keep the architecture clear, minimal, and grounded in real production-required data paths.

## Acceptance Criteria

- Add a real, non-mock ProcessorNode or equivalent under `top.lanshan.manmu.node`.
- Add a real processor agent boundary, likely `ProcessorAgent` and `LlmProcessorAgent`, using the same real `AgentClient` path as other agents.
- `ResearchTeamNode` and `SimpleResearchRunner` should route `StepType.RESEARCH` to `ResearcherNode` and `StepType.PROCESSING` to `ProcessorNode`; do not keep PROCESSING execution inside `ResearcherNode`.
- Processor prompt should include the current processing step, `state.observations()`, `state.siteInformation()`, and relevant plan/step context.
- PROCESSING output should write `executionRes`, mark the step completed/error, and add a processing result to state in a way the reporter can use. It may use `state.addObservation(...)` for the MVP, or a clearly named new list if that is cleaner.
- Preserve existing `/api/research/stream` and `/chat/stream` compatibility while adding visible `processor` events.
- Update `/chat/stream` display title mapping for `processor`.
- Update tests for route decisions and exact event sequences.
- Add focused tests for `ProcessorNode` consuming prior observations/search context.
- Run Java 17 verification. Prefer full `mvn test`; if real provider tests fail due external API/network conditions, record the exact failure and run focused non-network tests.
- Commit the completed small stage with a Chinese commit message.

## Scope

- Work only in `C:/MainData/code/Codex_project/M-agent`.
- Inspect `C:/MainData/code/Codex_project/deepresearch-main` as read-only guidance.
- Keep this stage backend-focused and minimal.
- Do not import the reference project's Vue frontend, Redis, RAG modules, MCP modules, full Spring AI Alibaba Graph, reflection loop, or parallel executor stack.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/AGENTS.md` only if project instructions need a minimal Chinese update
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-processor-node-search-context.md`

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
- Working tree is clean at the time this handoff is written.
- Current commit: `e124fa1327d84c95bc8e535ef19044e1d8e80de2`.
- No upstream is configured for `main`.
- Backend default port is `8080`.
- Project instruction says any manually started backend service must be closed after testing.
- Current runner has named nodes for planner, information, research_team, researcher, and reporter.
- Current runner loop is planner -> information -> research_team -> researcher -> research_team -> reporter.
- `ResearchTeamNode.decide(...)` returns `ResearchTeamRoute.RESEARCHER` for both RESEARCH and PROCESSING pending steps, using `nextStepType` to tell `ResearcherNode` what to run.
- `ResearcherNode` filters by `decision.nextStepType()` and currently executes both RESEARCH and PROCESSING steps.
- `ResearchState` already carries `observations`, `searchContexts`, and de-duplicated `siteInformation`.
- Bocha key is configured locally in `.local/model-providers.json`, but `.local` is forbidden to write or print.

## Completed

- Prior stages:
  - Git repository initialized.
  - Runtime mock agent/search path removed.
  - `/chat/stream` added with DeepResearch-compatible envelope fields.
  - Plan/Step state model upgraded.
  - Lightweight `ResearchTeamNode` added and verified.
  - Bocha-only `InformationNode` added and verified.
- Live HTTP verification after Bocha key was configured:
  - Existing port `8080` had another Java process and was not touched.
  - Current backend was started on temporary port `18081`.
  - HTTP switched model to `deepseek-chat`.
  - `POST /api/research/stream` with a fresh OpenAI Codex query produced `information -> research_team -> researcher -> reporter -> __END__`, real Bocha `siteInformation`, and `done=true`.
  - `POST /chat/stream` also exposed `siteInformation` in the DeepResearch-compatible envelope.
  - A clean UTF-8 `curl.exe --data-binary` run produced a valid Chinese query in the `information.search_completed` payload and returned 5 Bocha source results.
  - The temporary backend service on `18081` was closed and the port was confirmed released.
- Current task handoff summarizes the next stage; no implementation has started for ProcessorNode yet.

## Decisions

- Add ProcessorNode next before human feedback, stop/resume, or Graph migration.
- Imitate the reference project's PROCESSING/Coder split at the semantic level only.
- Do not import `CoderNode`, `ParallelExecutorNode`, MCP, reflection, or Graph machinery into this MVP stage.
- Keep `SimpleResearchRunner` for now.
- Do not fake processor results; use the real LLM provider path through `AgentClient`.
- Keep API keys out of source, tests, assertions, logs, commits, and handoff files.

## Evidence / References

- Reference PROCESSING assignment: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/ParallelExecutorNode.java`
- Reference coder/processing node: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/CoderNode.java`
- Reference researcher node: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/ResearcherNode.java`
- Current runner: `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- Current team node: `src/main/java/top/lanshan/manmu/node/ResearchTeamNode.java`
- Current researcher node: `src/main/java/top/lanshan/manmu/node/ResearcherNode.java`
- Current information node: `src/main/java/top/lanshan/manmu/node/InformationNode.java`
- Current state/model files: `src/main/java/top/lanshan/manmu/model/ResearchState.java`, `src/main/java/top/lanshan/manmu/model/ResearchStep.java`, `src/main/java/top/lanshan/manmu/model/ResearchTeamDecision.java`, `src/main/java/top/lanshan/manmu/model/ResearchTeamRoute.java`, `src/main/java/top/lanshan/manmu/model/StepSearchContext.java`, `src/main/java/top/lanshan/manmu/model/SiteInformation.java`
- Current agent path: `src/main/java/top/lanshan/manmu/agent/client/AgentClient.java`, `src/main/java/top/lanshan/manmu/agent/LlmResearcherAgent.java`, `src/main/java/top/lanshan/manmu/agent/LlmReporterAgent.java`
- Current tests to update: `src/test/java/top/lanshan/manmu/node/ResearchTeamNodeTest.java`, `src/test/java/top/lanshan/manmu/runner/SimpleResearchRunnerTest.java`, `src/test/java/top/lanshan/manmu/api/ResearchControllerLlmWorkflowTest.java`

## Files Touched

- This handoff writes `.codex/tasks/add-processor-node-search-context.md`.
- No implementation files have been changed for this stage yet.

## Commands Run

- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name @{upstream}` failed because no upstream is configured.
- `git merge-base HEAD @{upstream}` failed because no upstream is configured.
- Read `AGENTS.md` with UTF-8.
- Read `.codex/tasks/add-information-node-bocha-search.md`.
- Read `.codex/tasks/add-research-team.md`.
- Read current runner, researcher, research team, state, step, researcher agent, reporter agent, route, and decision files.
- Read reference `ParallelExecutorNode`, `CoderNode`, and `ResearcherNode`.
- Live HTTP verification commands from the previous user request included temporary backend startup on `18081`, HTTP model switch to `deepseek-chat`, `/api/research/stream`, `/chat/stream`, clean `curl.exe --data-binary` UTF-8 SSE run, and service shutdown. Response artifacts were saved under `target/`, which is ignored and must not be committed.

## Verification

- Current working tree is clean.
- Most recent implementation verification from the Bocha stage:
  - Command: `mvn test` with `JAVA_HOME=C:\WorkResources\JDKs\JDK17`.
  - Result: `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- Real HTTP verification after Bocha key setup:
  - `/api/research/stream`: returned real `information.search_completed` event with 5 Bocha `siteInformation` entries and final `__END__` `done=true`.
  - `/chat/stream`: returned `siteInformation` in the envelope for information events.
  - Clean UTF-8 curl request confirmed Chinese query arrived intact.
- No manual backend service was left running; temporary port `18081` was confirmed closed.

## Known Failures / Blockers

- No upstream Git branch configured.
- Real LLM/provider tests can fail from external API rate limits or network timeouts.
- The live Bocha HTTP test showed the exact semantic issue this next stage should fix: PROCESSING was still handled by `ResearcherNode`, and one processing observation claimed no search context was available even though previous observations and site information existed in state.
- Some terminal output may show Chinese text as mojibake in commands; use `Get-Content -Encoding UTF8` when reading Chinese docs.

## Next Actions

- Implement `ProcessorAgent`/`LlmProcessorAgent`, `ProcessorNode`, and `processor.md` prompt so PROCESSING steps consume `state.observations()` and `state.siteInformation()`.
- Update `ResearchTeamRoute`, `ResearchTeamNode`, and `SimpleResearchRunner` so RESEARCH routes to researcher, PROCESSING routes to processor, and reporter runs only after all steps are terminal.
- Add/update focused tests, run Java 17 verification, then run a real HTTP chain with Bocha on a temporary port and close the backend service before stopping.

## Open Questions

- Should processing outputs be appended to the existing `state.observations()` list, or should `ResearchState` get a dedicated `processingResults` list for cleaner reporter prompts?
- Should `/chat/stream` use display title `内容加工`, `信息整理`, or `处理执行` for the processor node?
- Should final reporter/done events also carry aggregate `siteInformation`, or should that remain limited to information events for now?

## Avoid / Do Not Redo

- Do not edit `C:/MainData/code/Codex_project/deepresearch-main`.
- Do not repeat Bocha implementation work unless a bug is directly encountered.
- Do not introduce mock processor output or fake search context in production code.
- Do not add providers other than Bocha for search.
- Do not migrate to Spring AI Alibaba Graph yet.
- Do not commit `.local`, `target`, `.idea`, API keys, generated SSE/curl output, or logs.
- Do not leave manually started backend services running after tests.

## Resume Prompt
Resume task add-processor-node-search-context. Read .codex/tasks/add-processor-node-search-context.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
