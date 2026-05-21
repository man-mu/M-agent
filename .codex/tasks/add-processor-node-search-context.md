# Task Handoff: add-processor-node-search-context
Updated: 2026-05-21 23:04:50 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: e124fa1327d84c95bc8e535ef19044e1d8e80de2
Current Commit: 02548b6642e1456d2ddf38da374c872261e515cc

## Project Mainline

- This project is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under `C:/MainData/code/Codex_project/M-agent`.
- The long-term direction is to imitate `C:/MainData/code/Codex_project/deepresearch-main` in a deliberately reduced, runnable backend-first form.
- The project is growing DeepResearch workflow semantics in `SimpleResearchRunner` before migrating to Spring AI Alibaba Graph.
- Prior stages removed mock runtime paths, added DeepResearch-compatible SSE envelopes, upgraded Plan/Step state, added a lightweight `research_team` control node, changed the default backend port to `8080`, and added Bocha-only real web search through an `information` node.
- This stage added the missing `processor` path so the simplified loop now has stable planner, information, research_team, researcher, processor, reporter, and `__END__` semantics.
- New feature work must not introduce mock agents, mock search, fabricated search results, or local secret leaks.

## Stage Role in Mainline

- This stage adds a dedicated `ProcessorNode` for `StepType.PROCESSING`.
- It repairs the Bocha-stage semantic gap where processing steps were executed by `ResearcherNode` and could claim no search context existed even though prior observations and `siteInformation` were already in `ResearchState`.
- It imitates the reference project's PROCESSING/Coder split at the semantic level only, without importing MCP, parallel executor, reflection, Redis, RAG, frontend, or Graph infrastructure.

## Mainline Progression

- `add-research-team` made Plan/Step execution status drive the controlled loop: planner -> research_team -> researcher -> research_team -> reporter.
- `add-information-node-bocha-search` extended the loop to: planner -> information -> research_team -> researcher -> research_team -> reporter with real Bocha web search.
- This stage extends the loop to: planner -> information -> research_team -> researcher -> research_team -> processor -> research_team -> reporter.
- Future stages can build on stable node semantics to add human feedback, `/chat/stop`, `/chat/resume`, richer frontend rendering of `siteInformation`, and eventually Spring AI Alibaba Graph.

## Related Stage Handoffs

- `add-research-team`: completed lightweight `ResearchTeamNode` and runner loop.
- `add-information-node-bocha-search`: completed Bocha-only `InformationNode` and real HTTP verification.

## Goal

- Add a lightweight real `ProcessorNode` so PROCESSING steps consume prior observations, Bocha `siteInformation`, and step context instead of being handled by the researcher path.

## Task Theme / User Intent

- The user asked to resume and implement the stage: imitate `C:/MainData/code/Codex_project/deepresearch-main` by adding `ProcessorNode` so PROCESSING steps consume real search context.
- The desired direction is not a full Graph migration. It is a small backend stage that repairs workflow semantics while keeping the learning MVP clear and runnable.
- The project is a personal DeepResearch-lite implementation, so changes should remain minimal, explicit, and grounded in real production-required data paths.

## Acceptance Criteria

- Add a real, non-mock `ProcessorNode` under `top.lanshan.manmu.node`.
- Add `ProcessorAgent` and `LlmProcessorAgent` using the same real `AgentClient` path as other agents.
- Route `StepType.RESEARCH` to `ResearcherNode` and `StepType.PROCESSING` to `ProcessorNode`.
- Processor prompt must include the current processing step, `state.observations()`, `state.siteInformation()`, and plan context.
- PROCESSING output must write `executionRes`, mark the step completed/error, and add a processing result to state for reporter use.
- Preserve existing `/api/research/stream` and `/chat/stream` compatibility while adding visible `processor` events.
- Update `/chat/stream` display title mapping for `processor`.
- Update tests for route decisions, event sequences, plan normalization, and processor context consumption.
- Run Java 17 verification and a real HTTP chain on a temporary port, then close the backend service.
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
- Current commit: `02548b6642e1456d2ddf38da374c872261e515cc`.
- Working tree was clean immediately after the implementation commit. This handoff update is an uncommitted documentation continuity update unless committed separately.
- No upstream is configured for `main`.
- Backend default port remains `8080`.
- Project instruction says any manually started backend service must be closed after testing.
- Current runner has named nodes for planner, information, research_team, researcher, processor, and reporter.
- Current runner loop executes planner -> information -> research_team -> selected executor -> research_team until reporter route, then reporter and `__END__`.
- `ResearchTeamNode.decide(...)` now returns `ResearchTeamRoute.RESEARCHER` for pending RESEARCH work and `ResearchTeamRoute.PROCESSOR` for PROCESSING work after research steps are terminal.
- `PlannerOutputMapper` now normalizes multi-step plans so the final step is PROCESSING and at least one earlier step is RESEARCH; when `maxSteps > 1` but the model returns one step, it adds a deterministic final "Synthesize findings" PROCESSING step.
- `ResearchState` still uses the existing `observations` list for both research observations and processor results.

## Completed

- Added `ProcessorAgent` and `LlmProcessorAgent`.
- Added `ProcessorNode` with `processor` SSE events: `started`, `step_completed`, `completed`, and error handling.
- Added `src/main/resources/prompts/processor.md`.
- Updated `ResearchTeamRoute`, `ResearchTeamNode`, and `SimpleResearchRunner` to route PROCESSING to the processor node.
- Updated `/chat/stream` title mapping with `processor -> 信息整理`.
- Updated planner prompt to ask for a final PROCESSING step when more than one step is allowed.
- Updated `PlannerOutputMapper` to enforce a stable research-before-processing shape for multi-step workflows.
- Added focused tests for `ProcessorNode`, `LlmProcessorAgent`, planner normalization, and the new runner/team route.
- Updated real controller workflow tests to expect the processor path while allowing provider-dependent extra information search events.
- Ran full Java 17 verification and manual HTTP SSE verification.
- Committed implementation as `02548b6642e1456d2ddf38da374c872261e515cc` with message `新增处理节点消费搜索上下文`.

## Decisions

- Processor output is appended to existing `state.observations()` for the MVP; no separate `processingResults` state list was added.
- The `/chat/stream` display title for `processor` is `信息整理`.
- The processor uses the same real `AgentClient` boundary as planner/researcher/reporter.
- The plan normalization belongs in `PlannerOutputMapper`, not in the runner, because the runner should execute the plan shape it receives.
- Real provider workflow tests should assert node subsequences rather than exact information event counts because `need_web_search` is model-dependent.
- Do not fake processor results; all production processor execution goes through the real LLM provider path.

## Evidence / References

- Reference PROCESSING assignment: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/ParallelExecutorNode.java`
- Reference coder/processing node: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/CoderNode.java`
- Current runner: `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- Current team node: `src/main/java/top/lanshan/manmu/node/ResearchTeamNode.java`
- New processor node: `src/main/java/top/lanshan/manmu/node/ProcessorNode.java`
- New processor agent: `src/main/java/top/lanshan/manmu/agent/ProcessorAgent.java`, `src/main/java/top/lanshan/manmu/agent/LlmProcessorAgent.java`
- Planner normalization: `src/main/java/top/lanshan/manmu/agent/PlannerOutputMapper.java`
- Processor prompt: `src/main/resources/prompts/processor.md`
- Updated tests: `src/test/java/top/lanshan/manmu/node/ProcessorNodeTest.java`, `src/test/java/top/lanshan/manmu/agent/LlmProcessorAgentTest.java`, `src/test/java/top/lanshan/manmu/agent/PlannerOutputMapperTest.java`, `src/test/java/top/lanshan/manmu/node/ResearchTeamNodeTest.java`, `src/test/java/top/lanshan/manmu/runner/SimpleResearchRunnerTest.java`, `src/test/java/top/lanshan/manmu/api/ResearchControllerLlmWorkflowTest.java`

## Files Touched

- `src/main/java/top/lanshan/manmu/agent/LlmProcessorAgent.java`
- `src/main/java/top/lanshan/manmu/agent/ProcessorAgent.java`
- `src/main/java/top/lanshan/manmu/agent/PlannerOutputMapper.java`
- `src/main/java/top/lanshan/manmu/api/ChatController.java`
- `src/main/java/top/lanshan/manmu/model/ResearchTeamRoute.java`
- `src/main/java/top/lanshan/manmu/node/ProcessorNode.java`
- `src/main/java/top/lanshan/manmu/node/ResearchTeamNode.java`
- `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- `src/main/resources/prompts/planner.md`
- `src/main/resources/prompts/processor.md`
- `src/test/java/top/lanshan/manmu/agent/LlmProcessorAgentTest.java`
- `src/test/java/top/lanshan/manmu/agent/PlannerOutputMapperTest.java`
- `src/test/java/top/lanshan/manmu/api/ResearchControllerLlmWorkflowTest.java`
- `src/test/java/top/lanshan/manmu/node/ProcessorNodeTest.java`
- `src/test/java/top/lanshan/manmu/node/ResearchTeamNodeTest.java`
- `src/test/java/top/lanshan/manmu/runner/SimpleResearchRunnerTest.java`
- `.codex/tasks/add-processor-node-search-context.md`

## Commands Run

- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` failed because no upstream is configured.
- `git merge-base HEAD '@{upstream}'` failed because no upstream is configured.
- Read `AGENTS.md` with UTF-8.
- Read current runner, researcher, research team, state, step, agent, controller, prompt, and test files.
- Read reference `ParallelExecutorNode` and `CoderNode`.
- `mvn "-Dtest=ResearchTeamNodeTest,SimpleResearchRunnerTest,ProcessorNodeTest,LlmProcessorAgentTest" test` with Java 17: passed 6 tests.
- `mvn test` with Java 17 initially failed because real-provider workflow expectations were too exact and the planner could return only processing.
- `mvn "-Dtest=PlannerOutputMapperTest,ResearchControllerLlmWorkflowTest,ResearchTeamNodeTest,SimpleResearchRunnerTest,ProcessorNodeTest,LlmProcessorAgentTest" test` with Java 17: passed 15 tests after planner normalization.
- `mvn test` with Java 17: passed 24 tests.
- Started backend with `mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18082`, switched model to `deepseek-chat`, ran `/api/research/stream`, and stopped the temporary service.
- `git diff --check`: passed, with only CRLF warnings.
- `git commit -m "新增处理节点消费搜索上下文"`: created commit `02548b6642e1456d2ddf38da374c872261e515cc`.

## Verification

- Full test suite:
  - Command: `mvn test` with `JAVA_HOME=C:\WorkResources\JDKs\JDK17`.
  - Result: `Tests run: 24, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- Focused/live test slice:
  - Command: `mvn "-Dtest=PlannerOutputMapperTest,ResearchControllerLlmWorkflowTest,ResearchTeamNodeTest,SimpleResearchRunnerTest,ProcessorNodeTest,LlmProcessorAgentTest" test`.
  - Result: `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- Manual real HTTP verification:
  - Started backend on temporary port `18082`.
  - Switched model to `deepseek-chat`.
  - Sent `POST /api/research/stream` with `maxSteps=2`.
  - Observed node sequence: planner -> planner -> information -> information -> information -> research_team -> researcher -> researcher -> researcher -> research_team -> processor -> processor -> processor -> research_team -> reporter -> reporter -> `__END__`.
  - Observed 3 processor events, at least one `information.search_completed`, and final done event.
  - Stopped the temporary backend and confirmed port `18082` was released.

## Known Failures / Blockers

- No upstream Git branch configured.
- Real LLM/provider tests can still fail from external API rate limits or network timeouts.
- `target/` contains ignored manual verification logs and SSE artifacts; do not commit them.
- Some terminal output may show Chinese text as mojibake in non-UTF-8 reads; use `Get-Content -Encoding UTF8` for Chinese docs.

## Next Actions

- Choose the next stage: human feedback, `/chat/stop` and `/chat/resume`, richer frontend rendering of `siteInformation`, or Graph migration preparation.
- If continuing processor semantics, consider whether a dedicated `processingResults` field would be cleaner than appending processor results to `state.observations()`.
- If improving API output, decide whether final reporter/done events should carry aggregate `siteInformation` in addition to information events.

## Open Questions

- Should future stages add a dedicated `processingResults` list to `ResearchState` for cleaner reporter prompts?
- Should final reporter/done events also carry aggregate `siteInformation`, or should that remain limited to information events for now?
- Which next mainline stage should happen first: human feedback, stop/resume, frontend rendering, or Graph migration preparation?

## Avoid / Do Not Redo

- Do not edit `C:/MainData/code/Codex_project/deepresearch-main`.
- Do not repeat Bocha implementation work unless a bug is directly encountered.
- Do not introduce mock processor output, mock search, or fake search context in production code.
- Do not add providers other than Bocha for search.
- Do not migrate to Spring AI Alibaba Graph yet.
- Do not commit `.local`, `target`, `.idea`, API keys, generated SSE/curl output, or logs.
- Do not leave manually started backend services running after tests.

## Resume Prompt
Resume task add-processor-node-search-context. Read .codex/tasks/add-processor-node-search-context.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
