# Task Handoff: add-query-rewrite-multi-query
Updated: 2026-05-22 16:16:34 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: ebdb55fdb1f2f7a1561e8f5406e601ac06990305
Current Commit: ebdb55fdb1f2f7a1561e8f5406e601ac06990305

## Project Mainline

- This project is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under `C:/MainData/code/Codex_project/M-agent`.
- The long-term direction is to align with `C:/MainData/code/Codex_project/deepresearch-main` in a deliberately reduced, runnable backend-first form.
- The project evolves DeepResearch workflow semantics in `SimpleResearchRunner` before adopting the full Spring AI Alibaba Graph stack.
- Completed stages now provide real model calls, Bocha-only real web search, planner/researcher/processor/reporter execution, a lightweight `research_team` loop, human feedback plan pause/resume/replan, running `/chat/stop` cancellation, PostgreSQL-backed completed report persistence, PostgreSQL-backed session history persistence, lightweight background/session context injection, and now a lightweight `rewrite_multi_query` stage before background context and planning.
- Every completed stage must be validated with both `mvn test` and real curl end-to-end testing through the local backend, real Docker-backed middleware, and real model-provider paths. This stage kept that discipline and found one external DashScope timeout before completing with DeepSeek.

## Stage Role in Mainline

- This stage implemented the next missing pre-planner module from `deepresearch-main`: query rewriting plus multi-query optimization.
- It exists because M-agent already had durable session/report context feeding the planner, but it still sent only the raw user query into planning and downstream search.
- In `deepresearch-main`, the relevant path is `coordinator -> rewrite_multi_query -> background_investigator -> planner`. M-agent now has the same reduced semantic step in the current runner/node architecture without importing the full Graph/RAG/MCP stack.
- This stage improves downstream planning and search recall by turning one user query into a small, bounded set of optimized research queries through the real LLM path.

## Mainline Progression

- `add-research-team` introduced the controlled research loop.
- `add-information-node-bocha-search` added real Bocha information gathering.
- `add-processor-node-search-context` added the processor path for PROCESSING steps.
- `add-human-feedback-plan-gate` added planner pause/resume/replan.
- `add-chat-stop-session-lifecycle` and `add-running-chat-stop-cancellation` made paused and running chat sessions stoppable.
- `add-postgres-report-persistence` added durable completed reports.
- `add-session-history-persistence` added durable session/task lifecycle history and recent-history APIs.
- `add-background-investigation-session-context` uses durable session history and report storage as planner context.
- `add-query-rewrite-multi-query` now adds optimized-query generation before background context and planner execution, creating a clearer bridge toward the reference project's Graph flow while staying backend-only.

## Related Stage Handoffs

- `add-background-investigation-session-context`: immediate upstream; completed background/session context and proved the need for curl E2E testing after a runtime thread-switch issue.
- `add-session-history-persistence`: upstream durable session lifecycle source.
- `add-postgres-report-persistence`: upstream durable report storage source.
- `add-information-node-bocha-search`: downstream real web search path that now receives optimized queries in the search prompt.
- Earlier task handoffs under `.codex/tasks/` preserve the cross-stage project story.

## Goal

- Add a lightweight `rewrite_multi_query` stage to M-agent that rewrites the user's research query and produces a bounded list of optimized queries through a real model-provider path, then feeds those optimized queries into planner/search context while preserving the existing runner, SSE, persistence, stop, and human-feedback behavior.

## Task Theme / User Intent

- Continue aligning `C:/MainData/code/Codex_project/M-agent` with `C:/MainData/code/Codex_project/deepresearch-main`.
- Implement only the small, useful module corresponding to `RewriteAndMultiQueryNode`, not the whole Graph/RAG/MCP stack.
- Preserve the purpose of this module: improve planner and search recall by turning a raw user question into better search/planning formulations, including the original query and a few related optimized queries.
- Keep production behavior real: no mock rewrite agent, no fake optimized queries, no fake search, and no hidden fallback that masks provider failures.
- Finish with both unit/integration tests and curl-based production-shape verification.

## Acceptance Criteria

- Done: Added state/model support for optimized queries while preserving the original query.
- Done: Added `QueryRewriteAgent`, `LlmQueryRewriteAgent`, `QueryRewriteOutputMapper`, `QueryRewriteResponse`, `QueryRewriteNode`, `QueryRewritePayload`, and `prompts/query-rewrite.md`.
- Done: Bounded optimized-query count to `0..5`, defaulting to `3` in `ResearchRequest` and `ChatRequest`.
- Done: Includes the original query in optimized queries unless count is `0`.
- Done: `SimpleResearchRunner` executes `rewrite_multi_query` before background context loading and before `planner`.
- Done: Emits an SSE event with node `rewrite_multi_query`, phase `completed` or `degraded`, and payload field `optimize_queries`.
- Done: Planner prompt receives optimized queries.
- Done: `InformationNode` search query text receives optimized queries when web search is needed.
- Done: Existing `/api/research/stream`, `/chat/stream`, human feedback accepted resume, rejected replan, running stop, report persistence, and session history behavior remain test-covered.
- Done: Rewrite failures degrade explicitly to original-query-only continuation, or empty optimized queries when count is `0`; tests document this behavior.
- Done: Focused tests cover count clamping, output parsing/deduplication, planner propagation, runner ordering, SSE payload/event behavior, search propagation, and degraded fallback.
- Done: Java 17 `mvn test` succeeded.
- Done: Local backend was started and curl E2E was run through real HTTP APIs, Docker-backed PostgreSQL, Bocha search, and real configured model providers; backend was stopped afterward.
- Pending until final closeout: commit this stage with a Chinese commit message.

## Scope

- Work only in `C:/MainData/code/Codex_project/M-agent`.
- Inspect `C:/MainData/code/Codex_project/deepresearch-main` as read-only guidance.
- Backend-only scope: model/state additions, query rewrite agent/service, runner/node integration, prompt input changes, SSE event payloads, tests, and curl verification.
- Do not edit the read-only reference project.
- Do not add frontend, RAG, MCP integration features, Redis, Elasticsearch, export/download/PDF, short-term memory, user-file RAG, or full Spring AI Alibaba Graph migration in this stage.
- Do not add a new database table; optimized queries live in in-memory state and stream/prompt context.
- Preserve real provider paths and do not introduce mock production behavior.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/pom.xml`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/test/resources`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-query-rewrite-multi-query.md`

### Read-only Reference Roots

- `C:/MainData/code/Codex_project/deepresearch-main`
- Existing Git history in `C:/MainData/code/Codex_project/M-agent`
- Existing task handoffs under `C:/MainData/code/Codex_project/M-agent/.codex/tasks`
- Ignored manual verification artifacts under `C:/MainData/code/Codex_project/M-agent/target`

### Forbidden Write Roots

- `C:/MainData/code/Codex_project/deepresearch-main`
- `C:/MainData/code/Codex_project/M-agent/.local`
- `C:/MainData/code/Codex_project/M-agent/target`
- `C:/MainData/code/Codex_project/M-agent/.idea`
- `C:/MainData/code/Codex_project/M-agent/.claude`
- Any file containing API keys or local secrets

## Current State

- Current branch: `main`.
- Current commit before stage commit: `ebdb55fdb1f2f7a1561e8f5406e601ac06990305`.
- No upstream Git branch is configured for `main`.
- Working tree includes this stage's source/test/handoff changes plus unrelated untracked `.claude/settings.local.json`; do not edit, delete, stage, or commit `.claude`.
- Implementation is complete and verified.
- Backend service started for curl verification was stopped; port 8080 was confirmed released.

## Completed

- Added `optimize_query_num` support to `/api/research/stream` via `ResearchRequest` and `/chat/stream` via `ChatRequest`, clamped to `0..5` with default `3`.
- Added `ResearchState.optimizedQueries`, `ResearchState.optimizeQueryNum`, and `ResearchState.queryRewriteCompleted`.
- Added real LLM-backed query rewrite implementation using the existing `AgentClient` path and a new `query-rewrite` prompt.
- Added explicit degraded fallback in `QueryRewriteNode`: when rewrite fails, continue with the original query, or no optimized queries if count is `0`.
- Inserted `rewrite_multi_query` before background context loading and planner execution in `SimpleResearchRunner`.
- Passed optimized queries to `PlannerNode`/`PlannerAgent` and into `LlmPlannerAgent` prompts.
- Included optimized queries in `InformationNode` search query text.
- Added tests for rewrite mapper, LLM prompt/parse behavior, node success/degraded behavior, planner propagation, information search propagation, and runner ordering.
- Ran `mvn test`: 69 tests, 0 failures, 0 errors.
- Ran curl E2E:
  - DashScope run reached `rewrite_multi_query`, `planner`, `information`, and `researcher`, then failed during a later DashScope model call with a provider read timeout; session history recorded `FAILED`.
  - DeepSeek run completed through `rewrite_multi_query -> planner -> processor -> reporter -> __END__`, returned `done=true`, and report/session history were retrievable from PostgreSQL-backed APIs.

## Decisions

- Use a lightweight M-agent-native implementation instead of importing Spring AI Alibaba Graph, RAG pre-retrieval classes, or reference project infrastructure wholesale.
- Keep `deepresearch-main` read-only and use it only as semantic guidance.
- Treat optimized-query count as both API request input and defaulted/clamped state.
- Total optimized-query list size is capped by the configured count and includes the original query as the first item when count is greater than zero.
- Degrade on rewrite failure rather than stop the workflow, because the original query is legitimate user input and the SSE event explicitly reports degraded behavior.
- Do not persist optimized queries in a new table for this stage.

## Evidence / References

- Project instructions: `C:/MainData/code/Codex_project/M-agent/AGENTS.md`.
- Upstream handoff: `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-background-investigation-session-context.md`.
- Current M-agent state and runner:
  - `src/main/java/top/lanshan/manmu/model/ResearchState.java`
  - `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
  - `src/main/java/top/lanshan/manmu/node/QueryRewriteNode.java`
  - `src/main/java/top/lanshan/manmu/agent/LlmQueryRewriteAgent.java`
  - `src/main/java/top/lanshan/manmu/agent/QueryRewriteOutputMapper.java`
  - `src/main/resources/prompts/query-rewrite.md`
- Reference project files inspected:
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/RewriteAndMultiQueryNode.java`
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/dispatcher/RewriteAndMultiQueryDispatcher.java`
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/config/DeepResearchConfiguration.java`
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/util/StateUtil.java`
- Real curl artifacts were written under `%TEMP%/m-agent-e2e`, outside the repository.

## Files Touched

- `src/main/java/top/lanshan/manmu/agent/LlmPlannerAgent.java`
- `src/main/java/top/lanshan/manmu/agent/LlmQueryRewriteAgent.java`
- `src/main/java/top/lanshan/manmu/agent/PlannerAgent.java`
- `src/main/java/top/lanshan/manmu/agent/QueryRewriteAgent.java`
- `src/main/java/top/lanshan/manmu/agent/QueryRewriteOutputMapper.java`
- `src/main/java/top/lanshan/manmu/agent/QueryRewriteResponse.java`
- `src/main/java/top/lanshan/manmu/api/ChatController.java`
- `src/main/java/top/lanshan/manmu/model/ChatRequest.java`
- `src/main/java/top/lanshan/manmu/model/QueryRewritePayload.java`
- `src/main/java/top/lanshan/manmu/model/ResearchRequest.java`
- `src/main/java/top/lanshan/manmu/model/ResearchState.java`
- `src/main/java/top/lanshan/manmu/node/InformationNode.java`
- `src/main/java/top/lanshan/manmu/node/PlannerNode.java`
- `src/main/java/top/lanshan/manmu/node/QueryRewriteNode.java`
- `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- `src/main/resources/prompts/query-rewrite.md`
- `src/test/java/top/lanshan/manmu/agent/LlmPlannerAgentTest.java`
- `src/test/java/top/lanshan/manmu/agent/LlmQueryRewriteAgentTest.java`
- `src/test/java/top/lanshan/manmu/agent/QueryRewriteOutputMapperTest.java`
- `src/test/java/top/lanshan/manmu/node/InformationNodeTest.java`
- `src/test/java/top/lanshan/manmu/node/PlannerNodeTest.java`
- `src/test/java/top/lanshan/manmu/node/QueryRewriteNodeTest.java`
- `src/test/java/top/lanshan/manmu/runner/SimpleResearchRunnerTest.java`
- `.codex/tasks/add-query-rewrite-multi-query.md`

## Commands Run

- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}'` (failed: no upstream configured)
- `Get-Content -Raw .codex/tasks/add-query-rewrite-multi-query.md`
- `Get-Content -Raw AGENTS.md`
- `rg --files src pom.xml .codex/tasks | sort`
- Multiple `Get-Content -Raw` reads for runner, model, agent, node, prompt, controller, test, and reference files.
- `git diff --check`
- `docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"`
- `mvn test` with command-local `JAVA_HOME=C:\WorkResources\JDKs\JDK17`.
- `mvn spring-boot:run` with command-local `JAVA_HOME=C:\WorkResources\JDKs\JDK17`.
- `curl.exe -s -X POST http://localhost:8080/api/model/switch ...`
- `curl.exe -s http://localhost:8080/api/model/current`
- `curl.exe -s -N --max-time 240 -X POST http://localhost:8080/chat/stream ...`
- `curl.exe -s http://localhost:8080/api/reports/codex-query-rewrite-e2e-2`
- `curl.exe -s http://localhost:8080/api/sessions/codex-query-rewrite-e2e/history`
- `Stop-Process` on the backend process listening on port 8080.

## Verification

- `mvn test`: success, 69 tests run, 0 failures, 0 errors, 0 skipped.
- `git diff --check`: success; only Git line-ending warnings were printed.
- Docker middleware: `manmu-postgres` was running and healthy on `0.0.0.0:5432->5432/tcp`.
- Backend startup: Spring Boot started on port 8080 with Java 17; Flyway connected to `jdbc:postgresql://localhost:5432/manmu`, validated 2 migrations, and reported schema version 2 up to date.
- Curl E2E, DashScope first run:
  - Request: `/chat/stream`, session `codex-query-rewrite-e2e`, thread `codex-query-rewrite-e2e-1`, `optimize_query_num=3`.
  - Observed `rewrite_multi_query` payload with three optimized queries, then `planner`, `information`, `research_team`, and `researcher`.
  - Bocha search returned real site information.
  - Later model call failed with a DashScope read timeout; `/api/sessions/codex-query-rewrite-e2e/history` recorded this thread as `FAILED`.
- Curl E2E, DeepSeek second run:
  - Switched current model to `deepseek/deepseek-chat` through `/api/model/switch`.
  - Request: `/chat/stream`, session `codex-query-rewrite-e2e`, thread `codex-query-rewrite-e2e-2`, `optimize_query_num=3`.
  - Observed node sequence `rewrite_multi_query -> planner -> planner -> research_team -> processor -> processor -> processor -> research_team -> reporter -> reporter -> __END__`.
  - `rewrite_multi_query` payload included original query plus two optimized variants.
  - Final event was `__END__` with `done=true`.
  - `/api/reports/codex-query-rewrite-e2e-2` returned the completed report.
  - `/api/sessions/codex-query-rewrite-e2e/history` returned `COMPLETED` for `codex-query-rewrite-e2e-2` and the prior expected `FAILED` history for `codex-query-rewrite-e2e-1`.
- Backend service was stopped after curl verification and port 8080 was confirmed released.

## Known Failures / Blockers

- No upstream Git branch is configured for `main`.
- Unrelated untracked `.claude/settings.local.json` exists and must remain uncommitted.
- One real DashScope curl run failed later in the workflow with a provider read timeout. This is external-provider/network behavior rather than a compile or workflow-order failure; the DeepSeek run completed successfully.
- Existing display titles in `ChatController` contain mojibake from earlier files; this stage did not fix unrelated encoding text.

## Next Actions

- Stage only the files listed under `Files Touched`, excluding `.claude`, `.local`, `target`, and temp curl artifacts.
- Commit with a Chinese commit message for this completed stage.
- Future stage candidates: persist optimized queries in report/session metadata if the UI needs to display them later, or add a reduced coordinator/background-investigator parity step before considering full Graph migration.

## Open Questions

- Should optimized queries be persisted in report/session metadata later, or remain stream/state-only?
- Should `QueryRewriteNode` retry with another configured provider after a rewrite failure, or is explicit degraded continuation enough for now?
- Should existing mojibake display titles be corrected in a separate cleanup stage?

## Avoid / Do Not Redo

- Do not edit `C:/MainData/code/Codex_project/deepresearch-main`.
- Do not introduce full Graph migration in this stage.
- Do not add RAG, MCP, Redis, Elasticsearch, frontend, user-file RAG, short-term memory, export/download/PDF, or Graph checkpointing unless the user explicitly changes scope.
- Do not reimplement report persistence, session history, background/session context, stop/cancellation, or human feedback from scratch.
- Do not introduce mock agent output, mock search, fake optimized queries, fake reports, or fake search context in production code.
- Do not commit `.local`, `target`, `.idea`, `.claude`, API keys, generated SSE/curl output, or logs.
- Do not leave manually started backend services running after tests.
- Do not treat `mvn test` alone as sufficient validation for the stage.

## Resume Prompt
Resume task add-query-rewrite-multi-query. Read .codex/tasks/add-query-rewrite-multi-query.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
