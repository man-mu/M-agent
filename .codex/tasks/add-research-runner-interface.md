# Task Handoff: add-research-runner-interface
Updated: 2026-05-22 20:05:00 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: d61bf0cf1c3f8f2a7783332c5ff016e555ac1833
Current Commit: 1d8fd37d8db5e76e44721d371eb09e4292868444

## Project Mainline

- This repository is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under package `top.lanshan.manmu`.
- The long-running direction is a simplified but real production-path DeepResearch workflow: real LLM calls, real search provider calls, WebFlux SSE progress, PostgreSQL persistence, pause/resume/stop lifecycle, and no runtime mock fallback.
- The previous stage completed the serial pre-Graph foundations: `coordinator`, `plan_validator`, and `human_feedback` are explicit node-shaped components, while `research_team` routes to `researcher`, `processor`, and `reporter`.
- The agreed migration direction is to introduce Spring AI Alibaba Graph as a dynamic orchestration layer for the existing nodes without expanding into RAG, MCP, Redis, front-end work, professional KB, coder agents, or full parallel execution.
- The Graph migration plan is saved at `.codex/graph-dynamic-orchestration-plan.md` and remains the source of truth for the sequence of stage tasks.

## Stage Role in Mainline

- This stage created the first pluggability point before implementing a Graph runner.
- Controllers now depend on a `ResearchRunner` abstraction instead of `SimpleResearchRunner`.
- `SimpleResearchRunner` remains the default runtime path and still owns current behavior.
- Later stages can add `GraphResearchRunner` behind the same controller-facing contract with less API churn.

## Mainline Progression

- Stage 1 of the eight-stage Graph migration is complete.
- The project now has a runner abstraction, Graph dependency availability, and default `mvp.research.runner=simple` scaffolding.
- The likely next stage is `add-graph-state-adapter`, which should add Graph state container helpers without changing the default runtime path.

## Related Stage Handoffs

- `.codex/tasks/pre-graph-core-nodes.md`
- `.codex/tasks/add-background-investigator-pre-planner-search.md`
- `.codex/tasks/add-human-feedback-plan-gate.md`
- `.codex/tasks/add-information-node-bocha-search.md`
- `.codex/tasks/add-research-team.md`
- `.codex/tasks/add-query-rewrite-multi-query.md`
- `.codex/tasks/add-processor-node-search-context.md`
- `.codex/tasks/add-chat-stop-session-lifecycle.md`
- `.codex/tasks/add-running-chat-stop-cancellation.md`
- `.codex/tasks/add-postgres-report-persistence.md`
- `.codex/tasks/add-session-history-persistence.md`

## Goal

- Implement the first Graph migration stage: introduce a `ResearchRunner` abstraction and runner selection scaffolding while preserving existing `SimpleResearchRunner` behavior.
- Do not implement `GraphResearchRunner` in this stage.
- Do not change the default runtime path from simple to graph in this stage.

## Task Theme / User Intent

- The user asked to resume `add-research-runner-interface` from a handoff and continue from the stored next actions.
- The intent was to execute only the first small migration stage, verify it through tests and real HTTP/SSE, and commit it with a Chinese commit message.

## Acceptance Criteria

- Add unified interface `top.lanshan.manmu.runner.ResearchRunner`.
- Interface methods match the existing controller-facing runner surface:
  - `Flux<ResearchEvent> run(ResearchRequest request)`
  - `Flux<ResearchEvent> runChat(ResearchRequest request, String sessionId)`
  - `Flux<ResearchEvent> runUntilPlanGate(ResearchRequest request, String sessionId)`
  - `Flux<ResearchEvent> resume(String threadId, ResumeDecision decision)`
  - `Mono<Boolean> stopAndRecord(String threadId)`
- Make `SimpleResearchRunner` implement the interface.
- Make `ChatController` and `ResearchController` depend on `ResearchRunner`.
- Add default runner selection scaffolding with `mvp.research.runner=simple`.
- Add Spring AI Alibaba Graph core dependency for later stages using the existing BOM-managed version.
- Run focused tests and full `mvn test` with Java 17.
- Start the backend locally, run a real curl SSE smoke test through the unchanged simple path, verify PostgreSQL history/report persistence, stop the backend, and confirm port closure.
- Commit the stage with Chinese message `抽象研究运行器接口`.

## Scope

- Implemented in `C:/MainData/code/Codex_project/M-agent`.
- No changes were made to `C:/MainData/code/Codex_project/deepresearch-main`.
- Changes were scoped to runner abstraction, controller injection, runner properties/configuration, Graph dependency, application config, and controller tests.
- No Graph state adapter, Graph node adapter, or Graph runner implementation was started.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/pom.xml`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/test/resources`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-research-runner-interface.md`

### Read-only Reference Roots

- `C:/MainData/code/Codex_project/deepresearch-main`
- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks`
- `C:/MainData/code/Codex_project/M-agent/.codex/graph-dynamic-orchestration-plan.md`
- Existing git history and prior commits.

### Forbidden Write Roots

- `C:/MainData/code/Codex_project/deepresearch-main`
- `C:/MainData/code/Codex_project/M-agent/.local`
- `C:/MainData/code/Codex_project/M-agent/target`
- `C:/MainData/code/Codex_project/M-agent/.idea`
- `C:/MainData/code/Codex_project/M-agent/.claude`
- Any file containing local API keys, provider credentials, or secrets.

## Current State

- Current branch is `main`.
- Current commit is `1d8fd37d8db5e76e44721d371eb09e4292868444` (`抽象研究运行器接口`).
- No upstream branch is configured for `main`; merge-base is unavailable.
- Working tree has only expected untracked local handoff/planning files and `.claude/`.
- The backend service used for verification was stopped, and port `18080` was confirmed closed.

## Completed

- Added `ResearchRunner` interface.
- Added `ResearchRunnerProperties` and `ResearchRunnerConfiguration`.
- Added `mvp.research.runner: simple` to `application.yml`.
- Added `spring-ai-alibaba-graph-core` dependency, managed by existing `spring-ai-alibaba.version=1.0.0.4` BOM.
- Made `SimpleResearchRunner` implement `ResearchRunner`.
- Added `@ConditionalOnProperty(prefix = "mvp.research", name = "runner", havingValue = "simple", matchIfMissing = true)` to keep simple runner as default.
- Updated `ChatController` and `ResearchController` to inject `ResearchRunner`.
- Updated `ChatControllerTest` to mock `ResearchRunner`.
- Ran focused tests, full tests, and real HTTP/SSE smoke test.
- Committed code with message `抽象研究运行器接口`.

## Decisions

- Stage 1 includes `spring-ai-alibaba-graph-core` because the local Maven cache and BOM confirm the artifact exists for version `1.0.0.4`.
- Keep default runtime as `simple`; `graph` currently has no bean and should be introduced in a later stage.
- Do not add any fallback mock runner or mock search path.
- Do not commit `.codex` handoff/planning files unless the user explicitly asks; existing workflow leaves them untracked.

## Evidence / References

- `.codex/graph-dynamic-orchestration-plan.md`: full eight-stage Graph migration plan in Chinese.
- `src/main/java/top/lanshan/manmu/runner/ResearchRunner.java`: new controller-facing runner contract.
- `src/main/java/top/lanshan/manmu/runner/ResearchRunnerProperties.java`: runner selection property.
- `src/main/java/top/lanshan/manmu/runner/ResearchRunnerConfiguration.java`: enables runner properties.
- `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`: now implements `ResearchRunner` and remains default when `mvp.research.runner=simple` or missing.
- `src/main/java/top/lanshan/manmu/api/ChatController.java`: now depends on `ResearchRunner`.
- `src/main/java/top/lanshan/manmu/api/ResearchController.java`: now depends on `ResearchRunner`.
- `src/main/resources/application.yml`: default runner config.
- `src/test/java/top/lanshan/manmu/api/ChatControllerTest.java`: mock type changed to `ResearchRunner`.
- `pom.xml`: adds `spring-ai-alibaba-graph-core`.

## Files Touched

- `C:/MainData/code/Codex_project/M-agent/pom.xml`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/api/ChatController.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/api/ResearchController.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/ResearchRunner.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/ResearchRunnerConfiguration.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/ResearchRunnerProperties.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources/application.yml`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/api/ChatControllerTest.java`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-research-runner-interface.md`

## Commands Run

- `git status --short --branch`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'`
- `git merge-base HEAD '@{upstream}'`
- `rg --files`
- `rg "SimpleResearchRunner|ResearchRunner|@ConfigurationProperties|EnableConfigurationProperties|mvp\\.research|mvp:" src pom.xml`
- `mvn '-Dtest=ChatControllerTest,SimpleResearchRunnerTest' test` with `JAVA_HOME=C:\WorkResources\JDKs\JDK17`
- `mvn test` with `JAVA_HOME=C:\WorkResources\JDKs\JDK17`
- `docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"`
- `Get-NetTCPConnection -LocalPort 18080`
- `mvn -DskipTests -Dspring-boot.run.arguments=--server.port=18080 spring-boot:run`
- `curl.exe --no-buffer --max-time 180 -H "Content-Type: application/json" -X POST "http://localhost:18080/chat/stream" --data-binary "@<request-file>"`
- `Invoke-RestMethod GET http://localhost:18080/api/sessions/<session>/threads/<thread>`
- `Invoke-RestMethod GET http://localhost:18080/api/reports/<thread>/exists`
- `Invoke-RestMethod GET http://localhost:18080/api/reports/<thread>`
- `Stop-Process` for the verification Maven/Java processes.
- `git diff --check`
- `git add -- <stage files>`
- `git commit -m "抽象研究运行器接口"`

## Verification

- Focused tests passed: `ChatControllerTest` and `SimpleResearchRunnerTest`, 22 tests.
- Full Java 17 `mvn test` passed: 89 tests, 0 failures, 0 errors.
- Docker PostgreSQL container `manmu-postgres` was running and healthy during E2E verification.
- Real backend was started on port `18080`.
- Real `/chat/stream` SSE smoke test used `deepseek/deepseek-chat`, `enable_deepresearch=false`, and the unchanged simple runner path.
- SSE output included `coordinator` and `event:done`.
- Session history lookup returned status `COMPLETED`.
- Report exists lookup returned `true`.
- Report retrieval returned the generated model response.
- Verification service was stopped, and port `18080` was confirmed closed.

## Known Failures / Blockers

- No upstream branch is configured for `main`; merge-base is unavailable.
- `.claude/` is untracked and must be ignored unless the user explicitly asks otherwise.
- `.codex/graph-dynamic-orchestration-plan.md`, `.codex/tasks/pre-graph-core-nodes.md`, and this handoff file are untracked. Do not treat that as a scope violation.
- If `mvp.research.runner=graph` is set before a later stage provides `GraphResearchRunner`, the application will not have a `ResearchRunner` bean. This is expected for stage 1.
- Spring AI Alibaba Graph API may differ by version; later stages should inspect official docs and the actual `spring-ai-alibaba-graph-core` dependency source/Javadocs before implementing graph-specific APIs.

## Next Actions

1. Start a new stage handoff for `add-graph-state-adapter` or resume/create `.codex/tasks/add-graph-state-adapter.md`.
2. Implement only the Graph state adapter layer: state key constants and helper utilities around existing `ResearchState` and accumulated `ResearchEvent` values.
3. Add focused unit tests for the adapter and run `mvn test`; do not switch default runner or implement `GraphResearchRunner` yet.

## Open Questions

- Exact Spring AI Alibaba Graph state type and preferred APIs should be verified from local dependency sources or official docs during the next stage.
- Decide in a future stage whether to keep enum values `SIMPLE`/`GRAPH` or move to string-based runner selection once `GraphResearchRunner` exists.

## Avoid / Do Not Redo

- Do not redo the completed pre-Graph node work unless tests expose a regression.
- Do not rework `SimpleResearchRunner` internals during the state-adapter stage.
- Do not implement `GraphResearchRunner`, graph node adapter, or graph execution edges in `add-graph-state-adapter`.
- Do not switch the default runner to graph until the dedicated default-switch stage.
- Do not remove `SimpleResearchRunner` until the final removal stage.
- Do not introduce RAG, MCP, Redis, front-end work, professional KB, coder agents, or full parallel executor.
- Do not edit `.local`, `.claude`, `target`, `.idea`, or secrets.

## Resume Prompt
Resume task add-research-runner-interface. Read .codex/tasks/add-research-runner-interface.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
