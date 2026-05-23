# Task Handoff: remove-simple-research-runner
Updated: 2026-05-23 16:01:30 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: d8d392c9d5c829a5f46aa085241970feb72a9dcf
Current Commit: d8d392c9d5c829a5f46aa085241970feb72a9dcf

## Project Mainline

- This repository is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under package `top.lanshan.manmu`.
- The product direction is a simplified but real production-path DeepResearch workflow: real LLM calls, real search provider calls, WebFlux SSE progress, PostgreSQL-backed persistence, pause/resume/stop lifecycle, and no runtime mock fallback.
- The Graph migration is being done as small reversible stages around existing production nodes rather than by importing the full upstream DeepResearch stack.
- Stage 1 completed runner selection and Graph dependency scaffolding.
- Stage 2 completed the Graph state adapter.
- Stage 3 completed the reusable `ResearchNode` to Graph action adapter.
- Stage 4 completed the explicit Graph auto-complete runner path.
- Stage 5 completed Graph manual plan-gate pause and accepted resume.
- Stage 6 completed Graph rejected feedback replan, maximum-iteration continuation, paused stop, and running stop.
- Stage 7 switched the default runtime path from `SimpleResearchRunner` to `GraphResearchRunner` while keeping `SimpleResearchRunner` as a short-term fallback.
- Stage 8 should remove `SimpleResearchRunner` and clean up the last explicit simple fallback path.

## Stage Role in Mainline

- This is stage 8 of the Graph migration: `remove-simple-research-runner`.
- This stage exists because Graph is now the default runner and has already passed the required focused and real HTTP/SSE validations.
- The stage should delete the legacy serial runner and collapse the codebase onto the Graph path only.
- After this stage, the remaining work should be about tidying any tests, docs, or stale messages that still mention the simple fallback.

## Mainline Progression

- Before stage 7, Graph was opt-in via `mvp.research.runner=graph` and default configuration remained `simple`.
- Stage 7 changed default configuration and runner wiring so normal application startup uses Graph, including when the property is missing.
- Stage 8 should remove the explicit simple fallback configuration and all runner-selection branches that exist only to support `SimpleResearchRunner`.
- Stage 8 must preserve the current Graph behavior and the real HTTP/SSE validation shape.
- The mainline should still avoid RAG, MCP, Redis, front-end work, professional KB, coder agents, and full parallel execution.

## Related Stage Handoffs

- `.codex/tasks/switch-default-runner-to-graph.md`
- `.codex/tasks/add-graph-feedback-replan-stop.md`
- `.codex/tasks/add-graph-human-feedback-accept-resume.md`
- `.codex/tasks/add-graph-auto-research-runner.md`
- `.codex/tasks/add-graph-node-adapter.md`
- `.codex/tasks/add-graph-state-adapter.md`
- `.codex/tasks/add-research-runner-interface.md`
- `.codex/tasks/pre-graph-core-nodes.md`
- `.codex/graph-dynamic-orchestration-plan.md`

## Goal

- Remove `SimpleResearchRunner`.
- Remove the explicit `mvp.research.runner=simple` fallback path.
- Migrate or delete simple-only tests so the suite describes the Graph-only runtime truth.
- Keep the Graph default path and all lifecycle behavior intact.
- Finish with focused tests, full tests, real HTTP/SSE verification, and a Chinese commit.

## Task Theme / User Intent

- The user wants the last stage of the Graph migration completed after verifying that Graph is now the normal runtime path.
- The user prefers small independently verified stages with Chinese commits, real PostgreSQL, real model-provider paths, and no mock runtime fallback.
- This handoff is for a fresh session that should start directly from the removal work.

## Acceptance Criteria

- `SimpleResearchRunner` is deleted.
- No production configuration path remains that selects `simple` as a fallback runner.
- Controllers continue to depend only on `ResearchRunner`.
- Graph runner selection and lifecycle tests still pass after the removal.
- Any remaining tests that were only asserting simple fallback behavior are either deleted or converted to Graph-only expectations.
- `mvn test` passes with Java 17.
- The backend can still be started without a Graph override and behaves as Graph by default.
- Real HTTP/SSE validation still covers:
  - direct answer;
  - auto accepted full research;
  - manual pause;
  - accepted resume;
  - rejected resume/replan;
  - stop.
- Real verification must still stop the backend and confirm port `18080` is closed.

## Scope

- Implement in `C:/MainData/code/Codex_project/M-agent`.
- Expected writes are the runner deletion, related test cleanup, and this handoff file if state changes.
- Avoid controller rewrites unless the `ResearchRunner` abstraction proves insufficient.
- Avoid changing Graph lifecycle logic unless a failing test shows the deletion uncovered a real regression.
- Do not touch `.local/model-providers.json` or expose any local API keys.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/runner`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/api`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources/application.yml`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/remove-simple-research-runner.md`

### Read-only Reference Roots

- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/switch-default-runner-to-graph.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/graph-dynamic-orchestration-plan.md`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/deepresearch-main`

### Forbidden Write Roots

- `C:/MainData/code/Codex_project/M-agent/.local`
- `C:/MainData/code/Codex_project/M-agent/target`
- `C:/MainData/code/Codex_project/M-agent/.idea`
- `C:/MainData/code/Codex_project/M-agent/.claude`
- `C:/MainData/code/Codex_project/deepresearch-main`
- Any file containing local API keys, provider credentials, or secrets.

## Current State

- Current branch is `main`.
- Current commit before the final stage-8 commit is `d8d392c9d5c829a5f46aa085241970feb72a9dcf`, with a Chinese message meaning "enable graph orchestration runner by default".
- No upstream branch is configured for `main`.
- Stage 8 implementation is complete locally: `SimpleResearchRunner`, `ResearchRunnerProperties`, and `SimpleResearchRunnerTest` have been deleted.
- `src/main/resources/application.yml` no longer carries `mvp.research.runner`.
- `GraphResearchRunner` is now an unconditional `@Component`, and `GraphResearchRunnerTest` asserts it is the only `ResearchRunner`.
- A real HTTP/SSE accepted-resume check uncovered that blank-message runtime exceptions in `ResearcherNode` / `ProcessorNode` were masked as `NullPointerException` by `Map.of`; the nodes now record a class-name fallback error message, with tests.
- `GraphResearchRunner` now reads latest graph state when completing or pausing plan-gate/resume flows and ignores null terminal graph outputs.
- After the final verification, port `18080` was confirmed closed.

## Completed

- Stage 7 is complete and committed.
- The default runner is Graph.
- Focused runner selection tests, full tests, packaging, and real HTTP/SSE validation already passed for the Graph-default path.
- The backend was already started and stopped successfully during stage 7 verification.
- Stage 8 removed the legacy simple runner and simple-only fallback configuration.
- Stage 8 kept controller injection through `ResearchRunner`.
- Stage 8 verified Graph-only direct answer, auto research, manual pause, accepted resume, rejected resume/replan, stop, report persistence, and session history persistence through real HTTP/SSE against local PostgreSQL and real provider paths.

## Decisions

- Graph is now the only intended runtime path.
- Stage 8 should remove the serial runner rather than reintroducing another fallback.
- Keep controller injection through `ResearchRunner`.
- Continue using real provider paths for validation.

## Evidence / References

- `src/main/resources/application.yml`: Graph is the default runner.
- `src/main/java/top/lanshan/manmu/runner/GraphResearchRunner.java`: current production runner path.
- `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`: legacy fallback runner targeted for deletion.
- `src/main/java/top/lanshan/manmu/runner/ResearchRunnerProperties.java`: still carries a Graph default and should be checked for any lingering simple-only behavior.
- `src/test/java/top/lanshan/manmu/runner/GraphResearchRunnerTest.java`: contains the current runner-selection coverage.
- `src/test/java/top/lanshan/manmu/runner/SimpleResearchRunnerTest.java`: simple-only test coverage to remove or migrate.
- `.codex/tasks/switch-default-runner-to-graph.md`: stage 7 completion record and validation evidence.
- `.codex/graph-dynamic-orchestration-plan.md`: stage 8 goal and sequence.
- `AGENTS.md`: Java 17, Maven, real API, Docker/PostgreSQL, Chinese commit, and real HTTP/SSE validation requirements.

## Files Touched

- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/remove-simple-research-runner.md`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/GraphResearchRunner.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/ResearchRunnerConfiguration.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/ResearchRunnerProperties.java` (deleted)
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java` (deleted)
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/ResearcherNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/ProcessorNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources/application.yml`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/runner/GraphResearchRunnerTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/runner/SimpleResearchRunnerTest.java` (deleted)
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/node/ResearcherNodeTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/node/ProcessorNodeTest.java`

## Commands Run

- `git status --short`
- `git branch --show-current`
- `git rev-parse HEAD`
- `Get-Content -Raw .codex/tasks/switch-default-runner-to-graph.md`
- `Get-Content -Raw .codex/graph-dynamic-orchestration-plan.md`
- `Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz'`
- `Get-ChildItem .codex/tasks -Filter 'remove-simple-research-runner.md'`
- `Get-ChildItem .codex/tasks`

## Verification

- `mvn -Dtest=GraphResearchRunnerTest,ResearcherNodeTest,ProcessorNodeTest test` passed.
- `mvn test` passed with 101 tests.
- `mvn -DskipTests package` passed before HTTP verification.
- Started the repackaged jar on `--server.port=18080`; Flyway connected to `jdbc:postgresql://localhost:5432/manmu` and confirmed schema v2.
- `GET /api/model/current` initially returned DashScope; `POST /api/model/switch` switched to `deepseek` / `deepseek-chat` because DashScope was rate-limited during earlier validation.
- `POST /chat/stream` direct-answer request returned coordinator `DIRECT_ANSWER` and `done`.
- `POST /chat/stream` auto-accepted research returned coordinator, rewrite, background investigator, planner, plan validator, information, research team, researcher, processor, reporter, and `done`.
- `GET /api/reports/stage8-auto-v2` returned the persisted report.
- `POST /chat/stream` manual request paused at `human_feedback`.
- `POST /chat/resume` accepted resume completed through information, processor/reporter, and `done`.
- A second manual request followed by rejected `POST /chat/resume` replanned and paused again at `human_feedback`.
- `POST /chat/stop` for the rejected thread returned success, and `GET /api/sessions/stage8-reject-v2/history` showed `STOPPED`.
- `GET /api/sessions/stage8-manual-v2/threads/stage8-manual-v2` showed `COMPLETED`.
- `rg 'SimpleResearchRunner|ResearchRunnerProperties|RunnerType|mvp\.research\.runner|havingValue' src/main src/test` returned no matches.
- Logs for the final HTTP run had no `NullPointerException`, `event:error`, `ERROR`, `Exception`, `ReadTimeoutException`, or `RateQuota` matches.
- The backend was stopped and `Get-NetTCPConnection -LocalPort 18080` returned no listener.

## Known Failures / Blockers

- `main` has no configured upstream branch.
- No stage 8 blocker remains.

## Next Actions

1. Commit the completed stage 8 code changes with a Chinese commit message.
2. Leave unrelated pre-existing untracked `.codex` task files untouched unless the user asks to clean them up.

## Open Questions

- None for the completed stage.

## Avoid / Do Not Redo

- Do not redo stage 7 default-runner changes; they are already committed.
- Do not reintroduce a simple fallback path after removal.
- Do not switch to mocks or disable real provider paths to make tests pass.
- Do not edit `.local/model-providers.json` or expose API keys.
- Do not change controllers unless the existing `ResearchRunner` abstraction is demonstrably insufficient.
- Do not leave the backend process running after HTTP/SSE validation.

## Resume Prompt

Resume task remove-simple-research-runner. Read .codex/tasks/remove-simple-research-runner.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
