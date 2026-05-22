# Task Handoff: add-background-investigator-pre-planner-search
Updated: 2026-05-22 16:52:46 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: c65cf2c2fd3f2350df1e196f70a49256d040f585
Current Commit: c65cf2c2fd3f2350df1e196f70a49256d040f585 before final stage commit

## Project Mainline

- This project is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under `C:/MainData/code/Codex_project/M-agent`.
- The long-term direction is to align with `C:/MainData/code/Codex_project/deepresearch-main` in a deliberately reduced, runnable backend-first form.
- The project evolves DeepResearch workflow semantics in `SimpleResearchRunner` before adopting the full Spring AI Alibaba Graph stack.
- Completed stages now provide real model calls, Bocha-only real web search, planner/researcher/processor/reporter execution, a lightweight `research_team` loop, human feedback plan pause/resume/replan, running `/chat/stop` cancellation, PostgreSQL-backed completed report persistence, PostgreSQL-backed session history persistence, lightweight session/report context injection, `rewrite_multi_query`, and now a lightweight `background_investigator` stage before planning.
- The reduced pre-planner mainline is now `rewrite_multi_query -> background_investigator -> load previous-session context -> planner`.
- Every completed stage must be validated with both `mvn test` and real curl end-to-end testing through the local backend, real Docker-backed middleware, and real model-provider paths.

## Stage Role in Mainline

- This stage adds the M-agent-native `background_investigator` stage immediately after `rewrite_multi_query` and before planner execution.
- It closes the pre-planner gap identified from `deepresearch-main`: optimized queries now drive current-question web background search before the planner creates steps.
- It keeps the implementation backend-only and reduced: no Graph migration, no RAG, no MCP, no Redis, no Elasticsearch, no frontend, and no smart-agent search platform selector.

## Mainline Progression

- `add-research-team` introduced the controlled research loop.
- `add-information-node-bocha-search` added real Bocha information gathering.
- `add-processor-node-search-context` added the processor path for PROCESSING steps.
- `add-human-feedback-plan-gate` added planner pause/resume/replan.
- `add-chat-stop-session-lifecycle` and `add-running-chat-stop-cancellation` made paused and running chat sessions stoppable.
- `add-postgres-report-persistence` added durable completed reports.
- `add-session-history-persistence` added durable session/task lifecycle history and recent-history APIs.
- `add-background-investigation-session-context` uses durable session history and report storage as planner context.
- `add-query-rewrite-multi-query` added optimized-query generation before background context and planner execution.
- `add-background-investigator-pre-planner-search` now adds current-query background investigation using real Bocha search and feeds it separately into planner.
- A future stage can consider whether background investigation sources should also be reused by downstream researcher/processor/reporting, but this stage intentionally keeps them as planner context plus SSE payload.

## Related Stage Handoffs

- `add-query-rewrite-multi-query`: immediate upstream; completed optimized-query generation, planner propagation, search propagation, SSE observability, tests, and curl verification. Commit: `c65cf2c2fd3f2350df1e196f70a49256d040f585`.
- `add-background-investigation-session-context`: upstream context stage; provides durable previous report/session context to planner.
- `add-information-node-bocha-search`: upstream real Bocha search path reused by background investigation.
- Existing handoffs under `.codex/tasks/` preserve the broader project story and validation discipline.

## Goal

- Add a lightweight `background_investigator` stage to M-agent that uses optimized queries from `rewrite_multi_query`, performs real Bocha search, produces compact current-question background investigation context, feeds it into planner, emits observable SSE events, and preserves runner, stop, human feedback, report persistence, and session history behavior.

## Task Theme / User Intent

- The user wanted to continue in a new session by building `background_investigator` and aligning M-agent with `C:/MainData/code/Codex_project/deepresearch-main`.
- The alignment is semantic and incremental: copy useful pre-planner behavior, not the entire Graph/RAG/MCP/smart-agent stack.
- Production behavior remains real: no mock search, no fake background reports, no fake model output, and no hidden fallback that masks provider failures.

## Acceptance Criteria

- Add state/model support for background investigation results and current background context.
- Add a `BackgroundInvestigatorNode` with node name `background_investigator` and order between `rewrite_multi_query` and `planner`.
- Execute `background_investigator` after `rewrite_multi_query` and before `loadBackgroundContext`/`planner` in `SimpleResearchRunner`.
- Use real `WebSearchClient`/Bocha search for up to 3 optimized queries, falling back to the original query when optimized queries are empty.
- Produce compact planner-ready background text from real search results without inventing facts.
- Feed both current background investigation context and previous-session context into planner as distinguishable prompt sections.
- Emit visible SSE events for `background_investigator`: `started`, per-query `search_completed` or `search_degraded`, and final `completed` or `degraded`.
- Preserve `/api/research/stream`, `/chat/stream`, human feedback accepted resume, rejected replan, running stop, completed report persistence, and session history compatibility.
- Add focused tests for ordering, optimized-query consumption, planner propagation, SSE payload shape, no-optimized-query fallback, search failure/degraded behavior, and existing pause/resume/stop behavior.
- Run Java 17 `mvn test`.
- Start the local backend and use `curl` against real HTTP APIs, Docker-backed PostgreSQL, real Bocha search, and a real configured model provider; confirm the stream exposes `rewrite_multi_query`, `background_investigator`, `planner`, and completion; verify report/session persistence; stop the backend afterward.
- Commit the completed stage with a Chinese commit message.

## Scope

- Work only in `C:/MainData/code/Codex_project/M-agent`.
- Inspect `C:/MainData/code/Codex_project/deepresearch-main` as read-only guidance.
- Backend-only scope: state additions, background investigator node, runner integration, planner prompt changes, SSE payloads, tests, and curl verification.
- Do not edit the read-only reference project.
- Do not add frontend, full Graph migration, RAG, MCP integration, Redis, Elasticsearch, export/download/PDF, short-term memory, user-file RAG, professional KB RAG, smart-agent platform selection, or parallel executor migration in this stage.
- Do not add a new database table. Background investigation lives in in-memory state and stream/planner context.
- Preserve real provider paths and do not introduce mock production behavior.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/pom.xml`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/test/resources`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-background-investigator-pre-planner-search.md`

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

- Implementation is complete and locally verified.
- Current branch before final commit: `main`.
- Current commit before final commit: `c65cf2c2fd3f2350df1e196f70a49256d040f585`.
- No upstream Git branch is configured for `main`.
- Working tree before final staging has the intended source/test/handoff changes plus unrelated untracked `.claude/`.
- `.claude/` was not edited, deleted, staged, or committed.
- Ignored manual E2E output lives under `target/` and was not staged.

## Completed

- Added `BackgroundInvestigatorNode` with node name `background_investigator`, order `7`, and real `WebSearchClient` search.
- Added `BackgroundInvestigationSearchResult` and `BackgroundInvestigationPayload`.
- Added `ResearchState` fields for `backgroundInvestigationContext`, `backgroundInvestigationResults`, and `backgroundInvestigationCompleted`.
- Updated `SimpleResearchRunner` to execute `rewrite_multi_query -> background_investigator -> loadBackgroundContext -> planner`.
- Updated `PlannerNode`, `PlannerAgent`, and `LlmPlannerAgent` so planner receives current web background separately from previous-session context.
- Updated `/chat/stream` envelope handling so `background_investigator` exposes source information in `siteInformation`.
- Added `BackgroundInvestigatorNodeTest`.
- Updated runner/planner/LLM planner tests for new ordering and prompt propagation.
- Ran full Maven tests successfully.
- Started local backend on port `18080`, verified real Docker PostgreSQL, DeepSeek model path, Bocha search path, SSE stages, report persistence, and session history persistence.
- Stopped the local backend after verification.

## Decisions

- Use deterministic background context formatting grounded in real Bocha search results instead of adding a new LLM summarizer agent in this stage.
- Search up to 3 unique optimized queries in insertion order.
- If optimized queries are empty, fall back to the original query so background investigation still runs unless the original query itself is blank.
- If a per-query search fails, emit `search_degraded`, include the failure in final context, and continue. If all searches fail, emit final phase `degraded` and continue to planner with explicit degraded context.
- Keep background investigation sources as planner/SSE context only; do not merge them into downstream `InformationNode` step search contexts in this stage.

## Evidence / References

- Project instructions: `C:/MainData/code/Codex_project/M-agent/AGENTS.md`.
- Upstream handoff: `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-query-rewrite-multi-query.md`.
- Current M-agent files:
  - `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
  - `src/main/java/top/lanshan/manmu/model/ResearchState.java`
  - `src/main/java/top/lanshan/manmu/model/BackgroundInvestigationPayload.java`
  - `src/main/java/top/lanshan/manmu/model/BackgroundInvestigationSearchResult.java`
  - `src/main/java/top/lanshan/manmu/node/BackgroundInvestigatorNode.java`
  - `src/main/java/top/lanshan/manmu/node/PlannerNode.java`
  - `src/main/java/top/lanshan/manmu/agent/PlannerAgent.java`
  - `src/main/java/top/lanshan/manmu/agent/LlmPlannerAgent.java`
  - `src/main/java/top/lanshan/manmu/api/ChatController.java`
  - `src/main/java/top/lanshan/manmu/search/WebSearchClient.java`
  - `src/main/java/top/lanshan/manmu/search/BochaSearchClient.java`
- Reference project files inspected:
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/BackgroundInvestigationNode.java`
  - `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/dispatcher/BackgroundInvestigationDispatcher.java`

## Files Touched

- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-background-investigator-pre-planner-search.md`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/agent/LlmPlannerAgent.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/agent/PlannerAgent.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/api/ChatController.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/BackgroundInvestigationPayload.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/BackgroundInvestigationSearchResult.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchState.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/BackgroundInvestigatorNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/PlannerNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/agent/LlmPlannerAgentTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/node/BackgroundInvestigatorNodeTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/node/PlannerNodeTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/runner/SimpleResearchRunnerTest.java`

## Commands Run

- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name "@{upstream}"` (failed: no upstream configured)
- `git merge-base HEAD "@{upstream}"` (failed: no upstream configured)
- `Get-Content -Raw AGENTS.md`
- Read current runner, state, planner, search, controller, and test files under `src/main/java` and `src/test/java`.
- Read reference `BackgroundInvestigationNode.java` and `BackgroundInvestigationDispatcher.java` from `deepresearch-main`.
- `$env:JAVA_HOME='C:\WorkResources\JDKs\JDK17'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; mvn test`
- Docker MCP `docker_container_list` confirmed `manmu-postgres` was running and healthy on host port `5432`.
- Started backend with Java 17: `mvn.cmd spring-boot:run -Dspring-boot.run.arguments=--server.port=18080`.
- `curl.exe -s http://localhost:18080/api/model/current`
- Posted DeepSeek key and switched current model to `deepseek-chat` via `/api/model/providers/deepseek/key` and `/api/model/switch`.
- Sent real SSE request to `/api/research/stream` using `curl.exe --data-binary '@-'` with thread `manual-bg-investigator-20260522165017`.
- Queried `/api/reports/manual-bg-investigator-20260522165017/exists`.
- Queried `/api/reports/manual-bg-investigator-20260522165017`.
- Queried `/api/sessions/manual-bg-investigator-20260522165017/threads/manual-bg-investigator-20260522165017`.
- Stopped Java/Maven processes for the manual backend and confirmed port `18080` was closed.

## Verification

- `mvn test` passed with Java 17.
- Maven result: 72 tests run, 0 failures, 0 errors, 0 skipped.
- Docker-backed PostgreSQL was verified through the started backend logs: `jdbc:postgresql://localhost:5432/manmu`, PostgreSQL 17.9, Flyway schema version 2 up to date.
- Real HTTP/SSE E2E passed on `http://localhost:18080/api/research/stream`.
- E2E thread: `manual-bg-investigator-20260522165017`.
- E2E HTTP status: 200.
- E2E event nodes included: `rewrite_multi_query`, four `background_investigator` events, `planner`, `information`, `research_team`, `researcher`, `processor`, `reporter`, and `__END__`.
- E2E phases included background `started`, per-query `search_completed`, and final `completed`.
- E2E confirmed background SSE payload had `siteInformation`.
- `/api/reports/{threadId}/exists` returned success with `report_information: true`.
- `/api/reports/{threadId}` returned a non-empty completed report.
- `/api/sessions/{sessionId}/threads/{threadId}` returned status `COMPLETED` with `report_thread_id` equal to the thread id.
- Backend service was stopped after verification; port `18080` was confirmed closed.

## Known Failures / Blockers

- No upstream Git branch is configured for `main`.
- Unrelated untracked `.claude/` exists and must remain uncommitted.
- Existing display titles in `ChatController` contain mojibake from earlier files; this stage only added the ASCII `Background Investigation` title and did not repair unrelated legacy text.
- Manual E2E artifacts under `target/` are ignored and should not be committed.

## Next Actions

- Stage only the intended source, test, and handoff files; do not stage `.claude/`, `.local/`, `target/`, or secrets.
- Commit the completed stage with a Chinese commit message.
- For the next project stage, decide whether background investigation sources should be reused by downstream researcher/processor/reporting or whether the next alignment gap from `deepresearch-main` is higher priority.

## Open Questions

- Should a future stage add an LLM-backed background summarizer agent, or is deterministic grounded formatting sufficient for this reduced backend?
- Should background source summaries be reused by downstream `InformationNode`/reporter, or remain planner-only?
- Should the legacy mojibake display titles in `ChatController` be repaired in a separate cleanup stage?

## Avoid / Do Not Redo

- Do not edit `C:/MainData/code/Codex_project/deepresearch-main`.
- Do not migrate to full Spring AI Alibaba Graph in this stage.
- Do not add RAG, MCP, Redis, Elasticsearch, frontend, user-file RAG, professional KB, smart-agent platform selection, short-term memory, export/download/PDF, parallel executor, or Graph checkpointing.
- Do not reimplement query rewrite, report persistence, session history, stop/cancellation, or human feedback from scratch.
- Do not introduce mock agent output, mock search, fake background results, fake reports, or fake search context in production code.
- Do not commit `.local`, `target`, `.idea`, `.claude`, API keys, generated SSE/curl output, or logs.
- Do not leave manually started backend services running after tests.
- Do not treat `mvn test` alone as sufficient validation for the stage.

## Resume Prompt
Resume task add-background-investigator-pre-planner-search. Read .codex/tasks/add-background-investigator-pre-planner-search.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
