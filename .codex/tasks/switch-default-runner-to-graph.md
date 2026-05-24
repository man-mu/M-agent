# Task Handoff: switch-default-runner-to-graph
Updated: 2026-05-23 13:09:21 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 4015156f3d0fae33aefd8776c95b0e30ecadb917
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

## Stage Role in Mainline

- This is stage 7 of the Graph migration: `switch-default-runner-to-graph`.
- This stage exists because the Graph runner has passed focused lifecycle tests and real HTTP/SSE verification for direct answer, auto research, manual pause, accepted resume, rejected replan, max-iteration continuation, and stop.
- The stage made Graph the normal runtime path without deleting the serial runner.
- The next stage is expected to remove `SimpleResearchRunner`, after reviewing whether any fallback-only tests or operational rollback notes still need to be preserved.

## Mainline Progression

- Before this stage, Graph was opt-in via `mvp.research.runner=graph` and default configuration remained `simple`.
- This stage changed default configuration and conditional runner wiring so normal application startup uses Graph, including when the property is missing.
- Tests still cover both Graph default behavior and the explicit simple fallback.
- Real HTTP/SSE validation used the default configuration with only `--server.port=18080`; it did not use `--mvp.research.runner=graph`.
- The mainline should still avoid RAG, MCP, Redis, front-end work, professional KB, coder agents, and full parallel execution.

## Related Stage Handoffs

- `.codex/tasks/add-graph-feedback-replan-stop.md`
- `.codex/tasks/add-graph-human-feedback-accept-resume.md`
- `.codex/tasks/add-graph-auto-research-runner.md`
- `.codex/tasks/add-graph-node-adapter.md`
- `.codex/tasks/add-graph-state-adapter.md`
- `.codex/tasks/add-research-runner-interface.md`
- `.codex/tasks/pre-graph-core-nodes.md`
- `.codex/graph-dynamic-orchestration-plan.md`

## Goal

- Done: switch the default research runner to Graph.
- Done: preserve `SimpleResearchRunner` as an explicit fallback for this stage.
- Done: update focused configuration tests so they assert Graph is the default and simple remains selectable.
- Done: run focused and full validation, including real HTTP/SSE smoke tests through the default Graph runner.
- Done: commit the stage with a Chinese message meaning "enable graph orchestration runner by default".

## Task Theme / User Intent

- The user wants the migration to move from opt-in Graph to default Graph only after the lifecycle parity work is complete.
- The user prefers small independently verified stages with Chinese commits, real PostgreSQL, real model-provider paths, and no mock runtime fallback.
- The user asked for this handoff so a fresh session can begin the next stage directly.

## Acceptance Criteria

- Done: `src/main/resources/application.yml` defaults to `mvp.research.runner: graph`.
- Done: `ResearchRunnerProperties` now defaults to `GRAPH` when the property is missing or null.
- Done: `GraphResearchRunner` uses `matchIfMissing = true`.
- Done: `SimpleResearchRunner` remains available only when explicitly configured with `mvp.research.runner=simple`.
- Done: Controllers still depend only on `ResearchRunner`, not concrete runner classes.
- Done: focused tests assert default Graph, explicit Graph, and explicit simple fallback.
- Done: existing Graph lifecycle focused tests continue to pass.
- Done: existing simple fallback tests continue to pass.
- Done: full `mvn test` with Java 17 passed. DashScope was not rate-limited in this run.
- Done: the backend was started locally without a graph runner override, using the packaged app/default config.
- Done: real HTTP/SSE default Graph validation covered direct answer, auto accepted full research, manual pause, accepted resume, rejected resume/replan, and paused stop.
- Done: real HTTP/SSE validation used DeepSeek `deepseek-chat` as the active model provider.
- Done: the backend was stopped after verification and port `18080` was confirmed closed.

## Scope

- Implemented in `C:/MainData/code/Codex_project/M-agent`.
- Writes were limited to runner default configuration, runner conditional wiring, focused runner tests, and this handoff file.
- No controller changes were needed because controllers already depend on `ResearchRunner`.
- No node business logic changed.
- `SimpleResearchRunner` was not removed in this stage.
- `.local/model-providers.json` was not changed and no API keys were exposed.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/src/main/resources/application.yml`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/GraphResearchRunner.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/ResearchRunnerProperties.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/runner`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/api`
- `C:/MainData/code/Codex_project/M-agent/src/test/resources`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/switch-default-runner-to-graph.md`

### Read-only Reference Roots

- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/graph-dynamic-orchestration-plan.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-graph-feedback-replan-stop.md`
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
- Current commit is `d8d392c9d5c829a5f46aa085241970feb72a9dcf`, with a Chinese message meaning "enable graph orchestration runner by default".
- No upstream branch is configured for `main`.
- After the stage commit, `git status --short` showed only untracked `.codex` plan and handoff files.
- `src/main/resources/application.yml` currently has `mvp.research.runner: graph`.
- `GraphResearchRunnerTest.graphRunnerIsDefaultAndSimpleRemainsExplicitFallback` asserts Graph default, explicit Graph, and explicit simple fallback.
- `SimpleResearchRunnerTest` still exists and protects the fallback path.
- `ResearchGraphBuilder` still contains old exception message strings about unsupported human feedback in auto-complete mode and rejected feedback. They were not surfaced during stage 7 verification.

## Completed

- Stage 6 was completed and committed at `4015156f3d0fae33aefd8776c95b0e30ecadb917`.
- Stage 7 was completed and committed at `d8d392c9d5c829a5f46aa085241970feb72a9dcf`.
- Stage 7 changed:
  - `application.yml` default runner from `simple` to `graph`;
  - `ResearchRunnerProperties` missing/null default from `SIMPLE` to `GRAPH`;
  - `GraphResearchRunner` conditional wiring to `matchIfMissing = true`;
  - `SimpleResearchRunner` conditional wiring to explicit `simple` only;
  - focused runner selection test expectations.
- Stage 7 focused tests, full tests, packaging, and real HTTP/SSE verification passed.
- Stage 7 backend service was stopped and port `18080` was confirmed closed.

## Decisions

- Graph is now the default runner.
- Simple remains an explicit short-term fallback until the next stage removes it.
- Keep controller injection through `ResearchRunner`.
- Do not introduce mocks to bypass model provider failures.
- Use Docker/PostgreSQL-oriented troubleshooting if local persistence or service startup fails.
- Use real model-provider paths for E2E. Stage 7 used DeepSeek for deterministic runtime validation, and full tests also exercised the real provider tests successfully.

## Evidence / References

- `src/main/resources/application.yml`: current default runner configuration is `graph`.
- `src/main/java/top/lanshan/manmu/runner/ResearchRunner.java`: controller-facing runner contract.
- `src/main/java/top/lanshan/manmu/runner/GraphResearchRunner.java`: default Graph runtime path.
- `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`: explicit fallback runner to remove later.
- `src/main/java/top/lanshan/manmu/runner/ResearchRunnerProperties.java`: property model now defaults to `GRAPH`.
- `src/test/java/top/lanshan/manmu/runner/GraphResearchRunnerTest.java`: focused Graph lifecycle and runner-selection tests, including default Graph and explicit simple fallback.
- `src/test/java/top/lanshan/manmu/runner/SimpleResearchRunnerTest.java`: focused simple fallback behavior tests.
- `target/e2e/stage7-*.sse`: ignored local verification artifacts from real HTTP/SSE checks.
- `.codex/graph-dynamic-orchestration-plan.md`: stage sequence and stage 7 acceptance criteria.
- `AGENTS.md`: Java 17, Maven, real API, Docker/PostgreSQL, Chinese commit, and real HTTP/SSE validation requirements.

## Files Touched

- `C:/MainData/code/Codex_project/M-agent/src/main/resources/application.yml`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/GraphResearchRunner.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/ResearchRunnerProperties.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/runner/GraphResearchRunnerTest.java`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/switch-default-runner-to-graph.md`

## Commands Run

- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'`
- `git merge-base HEAD '@{upstream}'`
- `Get-Content -Raw .codex/tasks/switch-default-runner-to-graph.md`
- `Get-Content -Raw AGENTS.md`
- `rg -n "mvp:|runner:|GraphResearchRunner|SimpleResearchRunner|ResearchRunner" ...`
- `mvn "-Dtest=GraphResearchRunnerTest,SimpleResearchRunnerTest" test`
- `mvn test`
- `mvn -DskipTests package`
- `java -jar target/deepresearch-mvp-0.1.0-SNAPSHOT.jar --server.port=18080`
- `Invoke-RestMethod` / `Invoke-WebRequest` calls against:
  - `/api/model/providers/deepseek/key`
  - `/api/model/switch`
  - `/api/research/stream`
  - `/chat/stream`
  - `/chat/resume`
  - `/chat/stop`
  - `/api/reports/{threadId}/exists`
  - `/api/sessions/{sessionId}/threads/{threadId}`
- `Stop-Process -Id 57900 -Force`
- `Get-NetTCPConnection -LocalPort 18080`
- `git diff --check -- <stage files>`
- `git add <stage files>`
- `git commit -m "<Chinese: enable graph orchestration runner by default>"`

## Verification

- Focused tests passed:
  - `mvn "-Dtest=GraphResearchRunnerTest,SimpleResearchRunnerTest" test`
  - Result: 29 tests, 0 failures, 0 errors.
- Full tests passed:
  - `mvn test`
  - Result: 116 tests, 0 failures, 0 errors.
- Packaging passed:
  - `mvn -DskipTests package`
- Backend startup verification:
  - Started packaged app with `--server.port=18080` only.
  - Did not pass `--mvp.research.runner=graph`.
  - Connected to real PostgreSQL container at `jdbc:postgresql://localhost:5432/manmu`.
- Real HTTP/SSE verification used DeepSeek `deepseek-chat` and real PostgreSQL:
  - Direct answer: `/api/research/stream`, thread `stage7-direct-20260523-3`, nodes `coordinator -> __END__`, report exists, status `COMPLETED`.
  - Auto research: `/chat/stream`, thread `stage7-auto-thread-20260523-1`, nodes included `coordinator`, `rewrite_multi_query`, `background_investigator`, `planner`, `plan_validator`, `information`, `research_team`, `researcher`, `processor`, `reporter`, `__END__`; report exists, status `COMPLETED`.
  - Manual pause: `/chat/stream`, thread `stage7-manual-thread-20260523-1`, ended at `human_feedback`, no report, status `PAUSED`.
  - Accepted resume: `/chat/resume`, same manual thread, nodes `human_feedback -> information -> research_team -> researcher -> research_team -> processor -> research_team -> reporter -> __END__`, report exists, status `COMPLETED`.
  - Rejected resume/replan: `/chat/resume`, thread `stage7-reject-thread-20260523-1`, nodes `human_feedback -> planner -> plan_validator -> human_feedback`, no report, status `PAUSED`.
  - Paused stop: `/chat/stop`, same reject thread, stop returned success, history status `STOPPED`, later resume returned `No paused research state found`.
- Backend was stopped and port `18080` was confirmed closed.

## Known Failures / Blockers

- `main` has no configured upstream branch.
- A first direct-answer curl attempt returned HTTP 400 because a nested PowerShell/curl command malformed the request body. It was discarded and rerun with `Invoke-WebRequest`, which passed.
- Spring AI Alibaba Graph still logs shallow-copy notices for `ResearchState` and `ResearchEvent` during graph execution; this is existing behavior and did not fail tests or E2E.

## Next Actions

1. Start the next stage `remove-simple-research-runner`: inspect `SimpleResearchRunner`, `SimpleResearchRunnerTest`, and fallback configuration usages before deleting anything.
2. Migrate any still-useful fallback assertions into Graph tests, then remove the explicit simple runner configuration path only after another focused test pass.
3. Run full Java 17 validation and real HTTP/SSE E2E again after removal, then stop the backend and confirm port `18080` is closed.

## Open Questions

- Whether stage 8 should remove all `SimpleResearchRunnerTest` coverage or first port selected cases to `GraphResearchRunnerTest`.
- Whether stale exception messages in `ResearchGraphBuilder` should be cleaned up during stage 8 while removing the serial runner.

## Avoid / Do Not Redo

- Do not redo Graph lifecycle implementation from stage 6; it is already committed.
- Do not redo stage 7 default-runner changes; they are already committed.
- Do not switch to mocks or disable real provider paths to make tests pass.
- Do not edit `.local/model-providers.json` or expose API keys.
- Do not change controllers unless the existing `ResearchRunner` abstraction is demonstrably insufficient.
- Do not leave the backend process running after HTTP/SSE validation.

## Resume Prompt

Resume task switch-default-runner-to-graph. Read .codex/tasks/switch-default-runner-to-graph.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
