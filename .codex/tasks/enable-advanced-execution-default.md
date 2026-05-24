# Task Handoff: enable-advanced-execution-default
Updated: 2026-05-24T14:14:02+08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 2f477a0a129e39aee096b2e6e984ee34e64d8a45
Current Commit: pending stage commit; run `git rev-parse HEAD` after commit

## Project Mainline

- M-agent is a Java 17 / Maven / Spring Boot 3.4.x simplified DeepResearch-style backend under package `top.lanshan.manmu`.
- The runtime direction is a Graph-based backend using Spring AI Alibaba Graph, WebFlux SSE, PostgreSQL persistence, Docker PostgreSQL, session history, session context, and real model providers.
- The project supports direct answer, automatic research, manual plan gate, accepted resume, rejected replan, stop, report persistence, session history, session context, stable stream events, step execution metadata, assignment-only `parallel_executor`, dynamic `researcher_n`, minimal dynamic `coder_n`, and default advanced Graph routing.
- The active long-running direction remains a minimal Graph advanced execution skeleton before adding RAG, Redis, MCP, Docker coder execution, or frontend work.

## Stage Role in Mainline

- This is stage 7 of `.codex/graph-advanced-execution-plan.md`: `enable-advanced-execution-default`.
- It inherits directly from stage 6, `.codex/tasks/wire-advanced-execution-graph.md`, where the advanced route was wired behind `mvp.research.advanced-execution.enabled=true`.
- This stage promotes the advanced route to the default path while preserving the legacy linear route as an explicit compatibility fallback through `enabled=false`.

## Mainline Progression

- Stage 1 stabilized SSE event fields and sequence metadata.
- Stage 2 introduced stable step ids and execution state metadata.
- Stage 3 added disabled-by-default assignment through `ParallelExecutorNode`.
- Stage 4 added dynamic `researcher_n` execution behavior.
- Stage 5 added dynamic `coder_n` execution behavior.
- Stage 6 connected `research_team -> parallel_executor -> researcher_n/coder_n -> research_team -> reporter` under an explicit switch, using a sequential executor selection strategy while keeping advanced node names, statuses, and SSE contract.
- This stage makes that advanced route the default, keeps the stage 6 sequential executor strategy, and leaves a clear fallback switch for the old `researcher` / `processor` linear graph path.
- The next stage should review advanced execution readiness and decide whether to keep or later remove the explicit linear fallback after more production-shaped runs.

## Related Stage Handoffs

- Immediate upstream completed stage: `.codex/tasks/wire-advanced-execution-graph.md`.
- Earlier advanced execution stages: `.codex/tasks/add-minimal-coder-node.md`, `.codex/tasks/add-multi-researcher-executors.md`, `.codex/tasks/add-parallel-executor-assignment.md`, `.codex/tasks/add-step-execution-state-model.md`, `.codex/tasks/add-stable-stream-event-contract.md`.
- Planning reference: `.codex/graph-advanced-execution-plan.md`.
- Likely next stage: `review-advanced-execution-readiness`.

## Goal

- Enable the advanced execution Graph route by default for production-shaped runtime and tests.
- Preserve the legacy linear route as a controlled compatibility path with explicit `mvp.research.advanced-execution.enabled=false`.
- Verify default advanced behavior through focused tests, full Maven tests, and real HTTP/SSE E2E without passing an explicit advanced-enabled override.

## Task Theme / User Intent

- Resume from the stage 6 handoff and continue the staged advanced execution work.
- Inherit the stage 6 sequential executor strategy rather than exploring true Graph fan-out now.
- Keep the implementation minimal, production-shaped, and reversible through configuration.
- Do not introduce RAG, Redis, MCP, Docker coder execution, frontend behavior, or mock agent/search fallbacks.

## Acceptance Criteria

- Read `AGENTS.md`, `.codex/tasks/wire-advanced-execution-graph.md`, and `.codex/graph-advanced-execution-plan.md` before editing.
- Create/update this handoff and verify scope safety before code changes.
- Default configuration enables advanced execution:
  - `mvp.research.advanced-execution.enabled=true`
  - researcher count remains `2`
  - coder count remains `1`
- A no-override/default Graph route emits `parallel_executor` and configured executor node events.
- The old linear `research_team -> researcher/processor/reporter` route remains available with explicit `enabled=false`.
- Focused tests cover default advanced routing and explicit disabled fallback.
- `mvn test` passes with Java 17.
- Real HTTP/SSE E2E uses default advanced configuration, Docker PostgreSQL, and a real model provider; it verifies direct answer, auto accepted full research, manual pause, accepted resume, rejected resume/replan, stop paused thread, and `/api/research/stream` smoke.
- E2E checks report readability and session history for `COMPLETED` and `STOPPED`.
- The backend service started for E2E is stopped and port `18080` is released.

## Scope

- Work only inside `C:/MainData/code/Codex_project/M-agent`.
- This stage modified advanced execution config defaults, Graph/runner/node/controller workflow tests, and this handoff.
- No relevant runtime-path README existed, so no project README was updated.
- This stage did not add RAG, Redis, MCP, Docker coder execution, frontend behavior, or copy full `deepresearch-main` infrastructure.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/config`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/graph`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources/application.yml`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/test/resources`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/enable-advanced-execution-default.md`
- `C:/MainData/code/Codex_project/M-agent/target/http-check`
- `C:/MainData/code/Codex_project/M-agent/target/enable-advanced-execution-default*.log`
- `C:/MainData/code/Codex_project/M-agent/target/enable-advanced-execution-default*.pid`

### Read-only Reference Roots

- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/graph-advanced-execution-plan.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/wire-advanced-execution-graph.md`
- `C:/MainData/code/Codex_project/deepresearch-main`
- `C:/Users/20232/.codex/skills/task-handoff/SKILL.md`

### Forbidden Write Roots

- `C:/MainData/code/Codex_project/deepresearch-main`
- `C:/MainData/code/Codex_project/M-agent/.local`
- `C:/MainData/code/Codex_project/M-agent/target`, except stage-local verification scratch files listed under Allowed Write Roots.
- `C:/MainData/code/Codex_project/M-agent/.idea`
- `C:/MainData/code/Codex_project/M-agent/.claude`
- Existing unrelated untracked older `.codex` planning/task files unless the user explicitly asks to curate or commit them.

## Current State

- Stage 7 is implemented and verified.
- `AdvancedExecutionProperties.enabled` now defaults to `true`.
- `application.yml` now defaults `mvp.research.advanced-execution.enabled: true`.
- Default Graph tests now assert the advanced route through `parallel_executor` and named executors.
- Legacy linear route tests now pass explicit disabled properties to verify controlled fallback behavior.
- Spring Boot LLM workflow tests now expect `parallel_executor`, `researcher_0`, and `coder_0` for default advanced execution.
- `main` has no upstream configured.
- Pre-existing unrelated untracked `.codex` files remain in the workspace and should be left alone.

## Completed

- Read the task-handoff skill, `AGENTS.md`, upstream stage 6 handoff, and the advanced execution plan.
- Inspected git branch, status, diff summary, current commit, and upstream availability.
- Confirmed scope safety and created this stage 7 handoff before code changes.
- Changed default advanced execution configuration to enabled in code and YAML.
- Updated focused and Spring Boot workflow tests for default advanced behavior plus explicit disabled fallback.
- Ran focused tests and full Maven tests with Java 17.
- Ran real HTTP/SSE E2E against Docker PostgreSQL and DeepSeek using default advanced execution configuration, with no explicit `advanced-execution.enabled=true` override.
- Stopped the local backend service and confirmed port `18080` was released.

## Decisions

- Preserve the old linear route behind explicit `mvp.research.advanced-execution.enabled=false`.
- Keep the stage 6 sequential executor selection strategy as the default advanced behavior for now.
- Do not explore true Spring AI Alibaba Graph fan-out in this stage.
- Use short real E2E prompts for the final clean verification pass to reduce provider timeout risk while still exercising default advanced routing, persistence, human feedback, resume, replan, and stop paths.

## Evidence / References

- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/wire-advanced-execution-graph.md`
- `C:/MainData/code/Codex_project/M-agent/.codex/graph-advanced-execution-plan.md`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources/application.yml`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/config/AdvancedExecutionProperties.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/api/ResearchControllerLlmWorkflowTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/runner/GraphResearchRunnerTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/node/ResearchTeamNodeTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/config/AdvancedExecutionPropertiesTest.java`
- Clean E2E `/api/research/stream` thread: `enable-clean-api-20260524141113`.
- Clean E2E direct chat session/thread: `enable-clean-direct-20260524141141` / `enable-clean-direct-20260524141141-thread`.
- Clean E2E accepted resume session/thread: `enable-clean-accept-20260524141202` / `enable-clean-accept-20260524141202-thread`.
- Clean E2E rejected replan session/thread: `enable-clean-reject-20260524141234` / `enable-clean-reject-20260524141234-thread`.
- Clean E2E stop paused session/thread: `enable-clean-stop-20260524141259` / `enable-clean-stop-20260524141259-thread`.

## Files Touched

- `.codex/tasks/enable-advanced-execution-default.md`
- `src/main/java/top/lanshan/manmu/config/AdvancedExecutionProperties.java`
- `src/main/resources/application.yml`
- `src/test/java/top/lanshan/manmu/api/ResearchControllerLlmWorkflowTest.java`
- `src/test/java/top/lanshan/manmu/config/AdvancedExecutionPropertiesTest.java`
- `src/test/java/top/lanshan/manmu/node/ResearchTeamNodeTest.java`
- `src/test/java/top/lanshan/manmu/runner/GraphResearchRunnerTest.java`

## Commands Run

- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'`
- `git merge-base HEAD '@{upstream}'`
- `Get-Content -Raw AGENTS.md`
- `Get-Content -Raw C:\Users\20232\.codex\skills\task-handoff\SKILL.md`
- `Get-Content -Raw .codex\tasks\wire-advanced-execution-graph.md`
- `Get-Content -Raw .codex\graph-advanced-execution-plan.md`
- `rg -n "advanced-execution|AdvancedExecution|parallel-node-count|ResearchGraphBuilder|GraphResearchRunner|ResearchRunnerConfiguration|ResearchTeamNode|mvp\.research" src pom.xml README* .codex -g "*.java" -g "*.yml" -g "*.yaml" -g "*.properties" -g "*.md"`
- `rg --files src/main src/test src/main/resources src/test/resources | sort`
- `git log --oneline -8`
- `mvn '-Dtest=GraphResearchRunnerTest,ResearchTeamNodeTest,AdvancedExecutionPropertiesTest,ParallelExecutorNodeTest,ResearcherNodeTest,CoderNodeTest' test`
- `mvn '-Dtest=ResearchControllerLlmWorkflowTest' test`
- `mvn test`
- Docker MCP `docker_container_list` confirmed `manmu-postgres` was running and healthy.
- Started backend twice on port `18080` with `mvn spring-boot:run -Dspring-boot.run.profiles=real-model -Dspring-boot.run.arguments=--server.port=18080`; the final clean pass used `target/enable-advanced-execution-default-clean.log`.
- `curl.exe -X POST http://localhost:18080/api/model/switch ...` switched to DeepSeek without printing keys.
- `curl.exe -N -X POST http://localhost:18080/api/research/stream ...` verified default advanced API path.
- `curl.exe -N -X POST http://localhost:18080/chat/stream ...` verified direct answer and manual plan gate.
- `curl.exe -N -X POST http://localhost:18080/chat/resume ...` verified accepted resume and rejected replan.
- `curl.exe -X POST http://localhost:18080/chat/stop ...` verified stopping a paused thread.
- `curl.exe http://localhost:18080/api/reports/<threadId>` verified report readability.
- `curl.exe http://localhost:18080/api/sessions/<sessionId>/threads/<threadId>` verified session history.
- `Stop-Process` stopped local backend Java processes and `Get-NetTCPConnection -LocalPort 18080` confirmed release.

## Verification

- Focused tests passed: `mvn '-Dtest=GraphResearchRunnerTest,ResearchTeamNodeTest,AdvancedExecutionPropertiesTest,ParallelExecutorNodeTest,ResearcherNodeTest,CoderNodeTest' test` with 39 tests, 0 failures, 0 errors.
- Focused Spring Boot workflow test passed: `mvn '-Dtest=ResearchControllerLlmWorkflowTest' test` with 2 tests, 0 failures, 0 errors.
- Full test suite passed: `mvn test` with 130 tests, 0 failures, 0 errors.
- Clean real E2E used default advanced config, Docker PostgreSQL `manmu-postgres`, and DeepSeek on port `18080`.
- Clean `/api/research/stream` for `enable-clean-api-20260524141113` emitted `parallel_executor` and `researcher_0`, `event:done=1`, `event:error=0`, no legacy `researcher` / `processor`, and no `NullPointerException`, quota, or timeout text. Report API returned `success`; session history returned `COMPLETED`.
- Clean direct chat for `enable-clean-direct-20260524141141-thread` emitted coordinator and `__END__`, `event:done=1`, `event:error=0`.
- Clean manual pause for `enable-clean-accept-20260524141202-thread` reached `human_feedback.waiting` with `event:error=0`; accepted resume emitted `parallel_executor` and `researcher_0`, `event:done=1`, `event:error=0`; report API returned `success`; session history returned `COMPLETED`.
- Clean rejected resume for `enable-clean-reject-20260524141234-thread` emitted rejection, planner, and `human_feedback.waiting` again with `event:error=0` and no executor nodes.
- Clean stop paused thread for `enable-clean-stop-20260524141259-thread` returned stop `success`; session history returned `STOPPED`.
- Clean E2E SSE files had `event:error=0` and no `NullPointerException`, `ReadTimeoutException`, timeout, or quota text.
- `target/enable-advanced-execution-default-clean.log` contains Maven `ERROR` lines after the service was forcibly stopped; these are expected shutdown artifacts from `Stop-Process`, not request execution failures.
- Port `18080` was confirmed released after E2E.

## Known Failures / Blockers

- No implementation blocker is known.
- `main` has no upstream branch configured.
- The advanced execution plan renders garbled in the current PowerShell console encoding, but stage ids and acceptance details were identifiable.
- Pre-existing unrelated untracked `.codex` files remain in the workspace and should be left alone.
- One earlier non-clean accepted-resume E2E thread, `enable-default-manual-20260524140526-thread`, failed with `ReadTimeoutException` from the real model provider path and was recorded as `FAILED`. A later clean accepted-resume run passed. Treat this as provider/network flakiness, not an advanced routing regression.
- The advanced route still uses stage 6 sequential executor selection, not true Graph fan-out.

## Next Actions

1. Commit this completed stage with a Chinese commit message, leaving unrelated older untracked `.codex` files alone.
2. Start `review-advanced-execution-readiness` as the next stage when requested.
3. In the next stage, review whether the explicit legacy linear fallback should stay for one more milestone or be scheduled for removal.

## Open Questions

- Should a future readiness stage reduce expected shutdown noise in Maven logs by adding a more graceful local service stop helper?
- Should the next stage explore Spring AI Alibaba Graph fan-out semantics, or keep the sequential advanced route until Docker/MCP/RAG work arrives?

## Avoid / Do Not Redo

- Do not change the stage 6 sequential executor strategy into true fan-out unless the next stage explicitly scopes it.
- Do not remove the explicit legacy linear fallback without a dedicated review/removal stage.
- Do not add RAG, Redis, MCP, Docker coder execution, frontend behavior, or large copies from `deepresearch-main`.
- Do not read, print, or commit API keys from `.local/model-providers.json`.
- Do not modify `deepresearch-main`; it is read-only reference material.
- Do not leave a local backend service running after E2E.

## Resume Prompt
Resume task enable-advanced-execution-default. Read .codex/tasks/enable-advanced-execution-default.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
