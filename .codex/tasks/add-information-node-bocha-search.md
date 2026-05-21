# Task Handoff: add-information-node-bocha-search
Updated: 2026-05-21
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 784033e49fc3125d51e54dcded13c17ae8a49410
Current Commit: 784033e49fc3125d51e54dcded13c17ae8a49410

## Project Mainline

- This project is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under `C:/MainData/code/Codex_project/M-agent`.
- The long-term direction is to imitate `C:/MainData/code/Codex_project/deepresearch-main` in a deliberately reduced, runnable backend-first form.
- The project is growing DeepResearch workflow semantics before migrating to Spring AI Alibaba Graph.
- Prior stages removed mock runtime paths, added DeepResearch-compatible SSE envelopes, upgraded Plan/Step state, added a lightweight `research_team` control node, changed the default backend port to `8080`, and updated project instructions to close backend services after manual startup tests.
- The project must use real model/API paths. New feature work must not introduce mock agents, mock search, fabricated search results, or local secret leaks.

## Stage Role in Mainline

- This stage should add the missing information/search capability between planning and research execution.
- It should imitate the reference project's `InformationNode` direction without importing the full Graph, RAG, MCP, Redis, frontend, or parallel executor stack.
- It should turn `ResearchStep.needWebSearch` from a dormant field into real workflow behavior using only Bocha AI Search API for web search.

## Mainline Progression

- `add-research-team` made Plan/Step execution status drive the current controlled loop: planner -> research_team -> researcher -> research_team -> reporter.
- This stage should extend that loop to: planner -> information -> research_team -> researcher -> research_team -> reporter.
- Future stages can add human feedback, `/chat/stop`, `/chat/resume`, a dedicated PROCESSING/coder node, richer frontend site information, and eventually Spring AI Alibaba Graph once the simplified workflow semantics are stable.

## Related Stage Handoffs

- `add-research-team`: completed lightweight `ResearchTeamNode` and runner loop.

## Goal

- Add a lightweight `InformationNode` plus real Bocha-only web search integration so the backend can run DeepResearch-style information gathering with live search results before reporter generation.

## Task Theme / User Intent

- The user wants the next implementation stage to follow `C:/MainData/code/Codex_project/deepresearch-main` toward "InformationNode + real search integration".
- The user explicitly constrained the search provider: use only Bocha AI Search API for联网搜索.
- The project is a personal DeepResearch-lite learning implementation, so keep the architecture clear, minimal, and grounded in real production-required data paths.

## Acceptance Criteria

- Add a real, non-mock `InformationNode` or equivalent node in `top.lanshan.manmu.node`.
- Integrate `InformationNode` into `SimpleResearchRunner` after planner and before `research_team`.
- Add a Bocha-only search client/service; do not add Tavily, Jina, SerpAPI, Baidu, Zhipu, Kimi, Alibaba web search, or other search providers in this stage.
- Use the official Bocha Web Search API endpoint verified from Bocha docs: `POST https://api.bochaai.com/v1/web-search`, with `Authorization: Bearer <key>` and JSON fields such as `query`, `freshness`, `summary`, and `count`.
- Store or read the Bocha API key through a local secret mechanism that does not commit secrets. Prefer an environment variable such as `BOCHA_API_KEY` or an ignored `.local` file; do not hardcode the key.
- Update planner prompt so `need_web_search` may become true now that a real search provider exists.
- Add state/model support for site/search results so `ResearcherNode` can use real search context and `/chat/stream` can eventually expose `siteInformation`.
- Preserve existing `/api/research/stream` and `/chat/stream` compatibility while adding visible `information` events when useful.
- Add focused tests for `InformationNode`, Bocha request/response mapping, and runner routing without using fake search results in production code.
- Run Java 17 verification. Prefer `mvn test`; if real provider tests fail due external API/network conditions, record the exact failure and run focused non-network tests.
- Commit completed small stages with Chinese commit messages.

## Scope

- Work only in `C:/MainData/code/Codex_project/M-agent`.
- Inspect `C:/MainData/code/Codex_project/deepresearch-main` as read-only guidance.
- Keep this stage backend-focused and minimal.
- Do not import the reference project's Vue frontend, Redis, RAG modules, MCP modules, full Spring AI Alibaba Graph, or multi-provider search abstraction beyond what is needed to keep a clean Bocha-only client boundary.

## Scope Safety

### Allowed Write Roots

- `C:/MainData/code/Codex_project/M-agent/src/main/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/src/main/resources`
- `C:/MainData/code/Codex_project/M-agent/src/test/java/top/lanshan/manmu`
- `C:/MainData/code/Codex_project/M-agent/AGENTS.md` only if project instructions need a minimal Chinese update
- `C:/MainData/code/Codex_project/M-agent/.codex/tasks/add-information-node-bocha-search.md`

### Read-only Reference Roots

- `C:/MainData/code/Codex_project/deepresearch-main`
- Existing Git history in `C:/MainData/code/Codex_project/M-agent`
- Official Bocha docs, especially `https://open.bochaai.com/`

### Forbidden Write Roots

- `C:/MainData/code/Codex_project/deepresearch-main`
- `C:/MainData/code/Codex_project/M-agent/.local`
- `C:/MainData/code/Codex_project/M-agent/target`
- `C:/MainData/code/Codex_project/M-agent/.idea`
- Any file containing API keys or local secrets

## Current State

- Git branch: `main`.
- Working tree was clean when this handoff was written.
- Current commit: `784033e49fc3125d51e54dcded13c17ae8a49410`.
- No upstream is configured for `main`.
- Backend default port is now `8080`.
- Project instruction says any manually started backend service must be closed after testing.
- Current runner has named nodes for planner, research_team, researcher, and reporter.
- Current runner loop is planner -> research_team -> researcher -> research_team -> reporter.
- `ResearchPlan` has `hasEnoughContext`, `thought`, and `steps`.
- `ResearchStep` has `needWebSearch`, `stepType`, `executionRes`, and `executionStatus`.
- Planner prompt currently forces `need_web_search: false`; this must change in this stage.
- `needWebSearch` is present but real search is not implemented yet.

## Completed

- Git repository initialized in earlier stages.
- Runtime mock agent/search path removed in earlier stages.
- `/chat/stream` added with DeepResearch-compatible envelope fields.
- Plan/Step state model upgraded.
- Lightweight `ResearchTeamNode` added and verified.
- Current code uses real DashScope and DeepSeek paths for LLM tests.
- Real HTTP curl tests were run against both DashScope and DeepSeek after the ResearchTeam stage:
  - DeepSeek completed the full chain.
  - DashScope first hit `429 Throttling.RateQuota`, then completed after retry.
- Default backend port changed from `18080` to `8080`.
- Project `AGENTS.md` now says backend services started for testing must be closed after testing.

## Decisions

- Use Bocha AI Search API only for this search stage.
- Do not use Kimi, Zhipu, Baidu, Tencent, Alibaba web search, Tavily, SerpAPI, or Jina as search providers in this stage.
- Do not migrate to Spring AI Alibaba Graph yet.
- Keep `SimpleResearchRunner` and add a minimal node boundary first.
- Do not fake search results; tests may use local unit-test stubs/mocks around interfaces, but production code must call real Bocha when search is enabled and configured.
- Keep API keys out of source, tests, assertions, logs, commits, and handoff files.

## Evidence / References

- Reference graph configuration: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/config/DeepResearchConfiguration.java`
- Reference `InformationNode`: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/InformationNode.java`
- Reference `InformationDispatcher`: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/dispatcher/InformationDispatcher.java`
- Reference search service: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/service/SearchInfoService.java`
- Current runner: `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- Current nodes: `src/main/java/top/lanshan/manmu/node`
- Current state/model files: `src/main/java/top/lanshan/manmu/model/ResearchState.java`, `src/main/java/top/lanshan/manmu/model/ResearchPlan.java`, `src/main/java/top/lanshan/manmu/model/ResearchStep.java`
- Current planner prompt: `src/main/resources/prompts/planner.md`
- Current app config: `src/main/resources/application.yml`
- Bocha official docs: `https://open.bochaai.com/`
- Bocha official docs show the direct API example: `POST https://api.bochaai.com/v1/web-search` with bearer auth and JSON body containing `query`, `freshness`, `summary`, and `count`.

## Files Touched

- This handoff writes `.codex/tasks/add-information-node-bocha-search.md`.
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
- Read `AGENTS.md`.
- Read `.codex/tasks/add-research-team.md`.
- Read reference `InformationNode`, `InformationDispatcher`, and `SearchInfoService`.
- Read current `SimpleResearchRunner`, `ResearchState`, `ResearchTeamNode`, `planner.md`, and `application.yml`.
- Searched web for current Bocha API details and verified the official Bocha open platform page mentions `https://api.bochaai.com/v1/web-search`.

## Verification

- No code implementation was started for this stage.
- Latest completed commit before this handoff: `784033e49fc3125d51e54dcded13c17ae8a49410`.
- Most recent focused non-network verification after the port/config update passed:
  - `mvn test -Dtest=PlannerOutputMapperTest,ModelProviderControllerTest,ModelProviderKeyStoreTest,ModelProviderRegistryTest,ResearchTeamNodeTest`
  - Result: `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- A full `mvn test` run after the port/config update failed because the real DashScope integration test could not connect to `dashscope.aliyuncs.com:443`; treat that as an external network/provider failure, not an InformationNode implementation result.

## Known Failures / Blockers

- No upstream Git branch configured.
- Real LLM/provider tests can fail from external API rate limits or network timeouts.
- Bocha API key availability is unknown. Next session must check the agreed local secret path or ask the user to provide/configure it without exposing the key.
- Exact Bocha response mapping should be verified from official docs or a live API response before hardcoding field paths.
- Current terminal may show Chinese output as mojibake in some commands; use `Get-Content -Encoding UTF8` when reading Chinese docs.

## Next Actions

- Inspect current model/state and design the minimal Bocha-only search data model and key-loading path, then add `WebSearchClient`/`BochaSearchClient` plus tests for request/response mapping.
- Add `InformationNode`, wire it into `SimpleResearchRunner` after planner, update planner/researcher prompts and `ResearchState` so `needWebSearch` triggers real Bocha search context.
- Run focused tests and then a real HTTP/curl chain with the backend on port `8080`; close any backend service started for testing before stopping.

## Open Questions

- Where should the Bocha API key live locally: environment variable `BOCHA_API_KEY`, an ignored `.local` JSON file, or both?
- Should `/chat/stream` expose Bocha search results immediately via `siteInformation`, or should this stage only feed them to `ResearcherNode` first?
- What default Bocha parameters should be used: `freshness`, `summary`, and `count` values for normal DeepResearch-lite runs?

## Avoid / Do Not Redo

- Do not edit `C:/MainData/code/Codex_project/deepresearch-main`.
- Do not introduce mock search fallback in production code.
- Do not add search providers other than Bocha AI Search API in this stage.
- Do not migrate to Spring AI Alibaba Graph yet.
- Do not commit `.local`, `target`, `.idea`, API keys, or generated curl output.
- Do not leave manually started backend services running after tests.

## Resume Prompt
Resume task add-information-node-bocha-search. Read .codex/tasks/add-information-node-bocha-search.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
