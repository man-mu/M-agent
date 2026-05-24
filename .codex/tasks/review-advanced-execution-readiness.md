# Task Handoff: review-advanced-execution-readiness
Updated: 2026-05-24T18:30:00+08:00
Workspace: C:/MainData/code/Claude_project/M-agent
Branch: main
Base Commit: 41bc11c 默认启用高级图执行路径
Current Commit: pending stage commit

## Project Mainline

- M-agent is a Java 17 / Maven / Spring Boot 3.4.x simplified DeepResearch-style backend under package `top.lanshan.manmu`.
- The runtime direction is a Graph-based backend using Spring AI Alibaba Graph, WebFlux SSE, PostgreSQL persistence, and real model providers.
- Stage 8 completes the advanced execution plan by removing the legacy linear sub-graph, fixing review findings, and verifying full-chain E2E.

## Stage Role in Mainline

- This is stage 8 of `.codex/graph-advanced-execution-plan.md`: `review-advanced-execution-readiness`.
- It inherits from stage 7, `.codex/tasks/enable-advanced-execution-default.md`, where advanced execution became the default.
- This stage removes the old linear sub-graph, simplifies nodes, fixes null-safety, and verifies readiness for RAG/MCP/Docker coder/frontend.

## Completed

- Removed old linear sub-graph from `ResearchGraphBuilder` (3 build methods, old routing, `RESEARCHER`/`PROCESSOR` constants).
- Simplified `ResearchTeamNode` — removed `advancedExecutionEnabled` branch, always routes to `PARALLEL_EXECUTOR`.
- Simplified `ResearcherNode` — removed dual executorNode/traditional mode, always uses prefixed step statuses.
- Deleted `ProcessorNode` (only used by old linear sub-graph).
- Removed `RESEARCHER` and `PROCESSOR` values from `ResearchTeamRoute` enum.
- Removed `enabled` field from `AdvancedExecutionProperties`.
- Cleaned up `ResearchNodeMetadata.KNOWN_NODES` — removed `researcher`, `processor`, `coder` legacy entries.
- Added null-safety for `state.report()` in `GraphResearchRunner.saveCompletedReport()`.
- Removed `enabled: true` from `application.yml`.
- Updated all tests: deleted `ProcessorNodeTest`, removed old-path tests from `GraphResearchRunnerTest`, `ResearchTeamNodeTest`, `ResearcherNodeTest`; updated `CoderNodeTest`, `ResearchGraphStateTest`, `AdvancedExecutionPropertiesTest`.
- Added RAG/MCP/Docker coder/frontend extension point documentation to `ResearchGraphBuilder`.
- `mvn test` passed: 123 tests, 0 failures, 0 errors.
- Real HTTP/SSE E2E verified on port 18080 with Docker PostgreSQL and DeepSeek:
  - `/api/research/stream` auto-complete: `coder_0` → reporter → `graph.completed`.
  - Manual pause: `human_feedback.waiting`.
  - Accepted resume: `human_feedback.accepted` → complete → `graph.completed`.
  - Rejected replan: `human_feedback.rejected` → planner → `human_feedback.waiting`.
  - Stop paused: stop API success, session history `STOPPED`.
  - Report API readable, session history `COMPLETED`.
- Backend stopped, port 18080 released.

## Decisions

- Removed the old linear sub-graph entirely rather than keeping it as a fallback. The advanced execution path is stable and has been the default since stage 7.
- Removed `AdvancedExecutionProperties.enabled` — the advanced path is now the only path.

## Next Actions

1. Commit this completed stage with a Chinese commit message.
2. The project is now ready for RAG, MCP, Docker coder, or frontend work. Entry points are documented in `ResearchGraphBuilder`.

## Avoid / Do Not Redo

- Do not reintroduce the old linear sub-graph or `ProcessorNode`.
- Do not add RAG, MCP, Docker coder, or frontend in this stage.
- Do not read or print API keys from `.local/model-providers.json`.
