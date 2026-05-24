# Task Handoff: pre-graph-core-nodes
Updated: 2026-05-22 18:46:20 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 4073657681ffc9431bb21a19a09431b644fd49d2
Current Commit: d61bf0cf1c3f8f2a7783332c5ff016e555ac1833

## Project Mainline

- This repository is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under package `top.lanshan.manmu`.
- The long-running direction is to build a simplified but real production-path DeepResearch workflow before introducing Graph: real LLM calls, real search provider calls, WebFlux SSE progress, PostgreSQL-backed persistence, and no mock fallback in the runtime path.
- The project intentionally avoids pulling in full upstream complexity too early. RAG, MCP, Redis, Graph execution, front-end work, professional knowledge base routing, coder agents, and full parallel execution remain out of scope until the serial node foundation is stable.
- The upstream reference project is `C:/MainData/code/Codex_project/deepresearch-main`, which should be inspected as read-only and used for semantic alignment.

## Stage Role in Mainline

- This stage prepared the current serial runner for a later Graph migration by making foundational control-flow decisions look like explicit nodes.
- The stage completed three pre-Graph foundations in strict order: `coordinator`, `plan_validator` / information routing semantics, and node-shaped `human_feedback`.
- Each block was implemented, tested, end-to-end verified through the real backend, and committed before the next block started.

## Mainline Progression

- Earlier stages established SSE chat/research flow, session history, PostgreSQL report persistence, stop/cancellation behavior, multi-query rewrite, Bocha-backed information search, research team looping, processor search context, human feedback plan gate behavior, and pre-planner background investigation.
- This stage bridged from "serial runner with embedded control-flow logic" to "serial runner with Graph-ready node semantics" without introducing the Graph runtime.
- The following likely stage can introduce Graph on top of explicit serial semantics for coordinator, plan validation, and human feedback.

## Related Stage Handoffs

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

- Implement and verify the remaining pre-Graph foundational nodes, aligned with `deepresearch-main` semantics but reduced to the current M-agent architecture.
- Completed order:
  1. Coordinator / lightweight intent router.
  2. Plan validator / information routing semantics.
  3. Node-shaped human feedback.

## Task Theme / User Intent

- The user wanted the core serial node implementation completed and proven stable before Graph is introduced.
- The implementation follows the spirit and routing semantics of `deepresearch-main`, while staying deliberately small and avoiding RAG, MCP, Redis, Graph, and other full upstream modules for now.
- The user explicitly required immediate testing after each block, including real end-to-end backend testing before starting the next module.

## Acceptance Criteria

- The implementation aligns with relevant upstream `deepresearch-main` control-flow semantics, but remains a simplified M-agent version.
- No RAG, MCP, Redis, Graph runtime, front-end, professional KB, coder node, or full parallel executor was introduced in this stage.
- Coordinator block:
  - Added a lightweight `coordinator` / intent router before the research workflow.
  - Preserved a real model-backed path and did not add mock routing in production.
  - Supports deep research versus direct/non-research answer behavior in the reduced architecture.
- Plan validator block:
  - Added explicit post-planner plan parsing and routing semantics similar to upstream `InformationNode`.
  - Routes valid auto-accepted plans to `research_team`.
  - Routes non-auto-accepted plans to `human_feedback`.
  - Routes invalid plans back to `planner` while iterations remain, and fails deterministically when the retry limit is reached.
  - Preserved current Bocha-backed web information search behavior by keeping `InformationNode` as the search node and adding `PlanValidatorNode` separately.
- Human feedback block:
  - Moved pause/resume/replan decision logic into `HumanFeedbackNode`.
  - Preserved `/chat/stream`, `/chat/resume`, `/chat/stop`, accepted resume, rejected feedback, and replan behavior.
  - Kept plan iteration and max-retry semantics compatible with future Graph routing.
- After each block:
  - Added focused automated tests.
  - Ran `mvn test` with Java 17.
  - Started the backend locally.
  - Used `curl` against real HTTP/SSE API paths with real configured providers and Docker PostgreSQL.
  - Verified persistence/session/report behavior where affected.
  - Stopped backend service and confirmed port `18080` was no longer listening.
  - Committed the completed block with a Chinese commit message before moving to the next block.

## Scope

- Implemented in `C:/MainData/code/Codex_project/M-agent`.
- Used `C:/MainData/code/Codex_project/deepresearch-main` only as a read-only semantic reference.
- Changes were scoped to backend workflow nodes, runner orchestration, request/response models, prompts, tests, and this task handoff doc.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/pom.xml`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/test/resources`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/pre-graph-core-nodes.md`

### Read-only Reference Roots

- `C:/MainData/code/Codex_project/deepresearch-main`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks`
- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`
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
- Current commit is `d61bf0cf1c3f8f2a7783332c5ff016e555ac1833`.
- No upstream branch is configured for `main`; base commit is therefore recorded as the pre-stage commit.
- `git status --short` currently shows untracked `.claude/` and this untracked handoff file. `.claude/` must not be modified.
- There are no tracked working-tree diffs at the time this handoff was updated.
- The current serial workflow includes: `coordinator`, `rewrite_multi_query`, `background_investigator`, planner background context loading, `planner`, `plan_validator`, `human_feedback`, Bocha-backed `information` search, `research_team`, `researcher`, `processor`, `reporter`, and terminal completion.

## Completed

- Completed block 1 at commit `b1f6f78` with commit message `添加协调器预路由节点`.
- Completed block 2 at commit `3c26030` with commit message `添加计划验证路由节点`.
- Completed block 3 at commit `d61bf0c` with commit message `抽取人工反馈路由节点`.
- Updated error event handling so null exception messages from external providers no longer cause a secondary SSE wrapper failure.

## Decisions

- Do not introduce Graph until these serial-node foundations are implemented and verified.
- Do not port upstream `ShortUserRoleMemoryNode`, `UserFileRAG`, `ProfessionalKbDecisionNode`, `ProfessionalKbRAG`, `ParallelExecutorNode`, `CoderNode`, Redis, MCP, or RAG in this stage.
- Keep upstream `deepresearch-main` as a semantic reference, not as a source tree to modify.
- Keep the existing Bocha-backed `InformationNode` name for search and use a separate `PlanValidatorNode` for upstream InformationNode-style plan routing semantics.
- Human feedback resume decisions are now emitted as explicit `human_feedback` node events before routing to planner or research execution.

## Evidence / References

- `C:/MainData/code/Codex_project/M-agent/AGENTS.md`: local project constraints, Java 17, Maven test command, real E2E requirement, no mocks, no `.local` secret leakage.
- `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/config/DeepResearchConfiguration.java`: upstream Graph node and conditional-edge structure.
- `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/CoordinatorNode.java`: upstream coordinator semantics; direct answer vs deep research route.
- `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/InformationNode.java`: upstream plan parsing, auto-accept, human-feedback, research-team, planner retry routing.
- `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/HumanFeedbackNode.java`: upstream feedback accept/reject and replan routing.
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`: current serial orchestration.
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node`: current node implementations.
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/api`: current SSE/chat lifecycle APIs.

## Files Touched

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/agent/CoordinatorAgent.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/agent/CoordinatorOutputMapper.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/agent/CoordinatorResponse.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/agent/LlmCoordinatorAgent.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/api/ChatController.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/CoordinatorDecision.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/CoordinatorRoute.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/HumanFeedbackDecision.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/HumanFeedbackRoute.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/PlanValidatorDecision.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/PlanValidatorRoute.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchEvent.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchRequest.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/model/ResearchState.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/CoordinatorNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/HumanFeedbackNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/PlanValidatorNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/node/PlannerNode.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources/prompts/coordinator.st`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/agent/LlmCoordinatorAgentTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/node/CoordinatorNodeTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/node/HumanFeedbackNodeTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/node/PlanValidatorNodeTest.java`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu/runner/SimpleResearchRunnerTest.java`
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/pre-graph-core-nodes.md`

## Commands Run

- `Get-Content -Raw "C:\Users\20232\.codex\skills\task-handoff\SKILL.md"`
- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse HEAD`
- `Get-Content -Raw AGENTS.md`
- Multiple `rg` and `Get-Content -Raw` inspections of current M-agent and read-only `deepresearch-main` files.
- Java 17 `mvn test` after coordinator block: passed, 78 tests.
- Java 17 `mvn test` after plan validator block: passed, 84 tests.
- Java 17 `mvn -Dtest=ChatControllerTest,SimpleResearchRunnerTest,HumanFeedbackNodeTest test`: passed, 26 tests.
- Java 17 `mvn test` after human feedback block: passed, 89 tests.
- Started backend on port `18080` with Java 17 and Docker PostgreSQL for each block.
- Switched runtime model provider to `deepseek/deepseek-chat` for real E2E validation.
- Ran real curl SSE/API validation for coordinator deep research path and direct answer path.
- Ran real curl SSE/API validation for plan validator auto-accepted route to `research_team` and manual route to `human_feedback`.
- Ran real curl SSE/API validation for human feedback accepted resume route to completion and rejected resume route back to planner/human feedback.
- Stopped backend service after each block and confirmed port `18080` closed.
- `git commit -m "添加协调器预路由节点"`
- `git commit -m "添加计划验证路由节点"`
- `git commit -m "抽取人工反馈路由节点"`

## Verification

- Coordinator block:
  - Full `mvn test` passed on Java 17.
  - Real `/api/research/stream` deep research path completed with `coordinator`, downstream research nodes, report persistence, and completed session history.
  - Real direct-answer path with `enable_deepresearch=false` completed through `coordinator -> __END__`, saved a report, and marked history completed.
- Plan validator block:
  - Full `mvn test` passed on Java 17 with 84 tests.
  - Real `/api/research/stream` auto-accepted path produced `plan_validator`, entered `research_team`, completed, saved a report, and marked history completed.
  - Real `/chat/stream` manual plan path produced `plan_validator -> human_feedback`, marked history `PAUSED`, and did not save a report.
- Human feedback block:
  - Focused tests passed: `ChatControllerTest`, `SimpleResearchRunnerTest`, `HumanFeedbackNodeTest`.
  - Full `mvn test` passed on Java 17 with 89 tests.
  - Real `/chat/stream` manual plan path paused at `human_feedback`.
  - Real `/chat/resume` accepted path emitted `human_feedback` first, then continued through `information`, `research_team`, `researcher`, `reporter`, and `__END__`; report existed and history status was `COMPLETED`.
  - Real `/chat/resume` rejected path emitted `human_feedback`, replanned through `planner` and `plan_validator`, returned to `human_feedback`, left history `PAUSED`, and did not save a report.
  - Backend service was stopped and port `18080` was closed.

## Known Failures / Blockers

- No upstream branch is configured for `main`; merge-base is unavailable.
- `.claude/` is untracked and must be ignored unless the user explicitly asks otherwise.
- `.codex/tasks/pre-graph-core-nodes.md` remains untracked after this update.
- External model calls can still time out. The final code now avoids null error-message SSE wrapper failures by emitting fallback error text.

## Next Actions

1. None. The three requested pre-Graph core-node blocks are complete and committed.

## Open Questions

- None known for this stage.

## Avoid / Do Not Redo

- Do not redo the completed coordinator, plan validator, or human feedback node work unless tests expose a regression.
- Do not introduce Graph, RAG, MCP, Redis, front-end work, professional KB, coder agents, or full parallel executor as part of this completed stage.
- Do not edit `C:/MainData/code/Codex_project/deepresearch-main`; inspect it only.
- Do not modify `.local`, `.claude`, `target`, `.idea`, or secrets.
- Do not add mock runtime fallbacks for model/search behavior.
- Do not skip real backend curl E2E for future stages.

## Resume Prompt
Resume task pre-graph-core-nodes. Read .codex/tasks/pre-graph-core-nodes.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.