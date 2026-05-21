# Task Handoff: add-information-node-bocha-search
Updated: 2026-05-21 22:17:25 +08:00
Workspace: C:/MainData/code/Codex_project/M-agent
Branch: main
Base Commit: 6132ed1c18819f8fc6797afb3015119eb8ce6e33
Current Commit: 6d9e812b818067b860ae43a4d74b297c23e1e219

## Project Mainline

- This project is a Java 17 / Maven / Spring Boot 3.4.x DeepResearch-lite backend under `C:/MainData/code/Codex_project/M-agent`.
- The long-term direction is to imitate `C:/MainData/code/Codex_project/deepresearch-main` in a deliberately reduced, runnable backend-first form.
- The project is growing DeepResearch workflow semantics before migrating to Spring AI Alibaba Graph.
- Prior stages removed mock runtime paths, added DeepResearch-compatible SSE envelopes, upgraded Plan/Step state, added a lightweight `research_team` control node, changed the default backend port to `8080`, and required closing manually started backend services after tests.
- This stage completed the missing planner-to-information bridge with a Bocha-only web search path, while preserving the real-provider-only constraint.
- New feature work must not introduce mock agents, mock search, fabricated search results, or local secret leaks.

## Stage Role in Mainline

- This stage added the missing information/search capability between planning and research execution.
- It imitates the reference project's `InformationNode` direction without importing the full Graph, RAG, MCP, Redis, frontend, or parallel executor stack.
- It turns `ResearchStep.needWebSearch` from dormant planner metadata into real workflow behavior using only Bocha AI Web Search API.

## Mainline Progression

- `add-research-team` made Plan/Step execution status drive the controlled loop: planner -> research_team -> researcher -> research_team -> reporter.
- This stage extended the loop to: planner -> information -> research_team -> researcher -> research_team -> reporter.
- `InformationNode` now searches only steps with `needWebSearch=true`, stores step-level `StepSearchContext`, accumulates workflow-level `siteInformation`, and emits visible `information` events.
- Future stages can add human feedback, `/chat/stop`, `/chat/resume`, a dedicated PROCESSING/coder node, richer frontend rendering of `siteInformation`, and eventually Spring AI Alibaba Graph once the simplified workflow semantics are stable.

## Related Stage Handoffs

- `add-research-team`: completed lightweight `ResearchTeamNode` and runner loop.

## Goal

- Add a lightweight `InformationNode` plus real Bocha-only web search integration so the backend can run DeepResearch-style information gathering with live search results before reporter generation.
- Status: implemented, tested, and committed.

## Task Theme / User Intent

- The user wanted the next implementation stage to follow `C:/MainData/code/Codex_project/deepresearch-main` toward "InformationNode + real search integration".
- The user explicitly constrained the search provider: use only Bocha AI Search API for web search.
- The project is a personal DeepResearch-lite learning implementation, so the architecture should stay clear, minimal, and grounded in real production-required data paths.

## Acceptance Criteria

- Done: added real, non-mock `InformationNode` in `top.lanshan.manmu.node`.
- Done: integrated `InformationNode` into `SimpleResearchRunner` after planner and before `research_team`.
- Done: added a Bocha-only search client boundary under `top.lanshan.manmu.search`.
- Done: configured the official Bocha Web Search endpoint `POST https://api.bochaai.com/v1/web-search` with bearer auth and JSON fields `query`, `freshness`, `summary`, and `count`.
- Done: reads the Bocha API key from `mvp.search.bocha.api-key` / `BOCHA_API_KEY`, then from ignored `.local/model-providers.json` provider id `bocha`; no key was committed.
- Done: planner prompt now allows `need_web_search=true` when fresh/external web information is needed.
- Done: added `SiteInformation`, `StepSearchContext`, and `InformationPayload` so `ResearcherNode` can use search context and `/chat/stream` can expose `siteInformation`.
- Done: preserved `/api/research/stream` and `/chat/stream` compatibility while adding `information` events.
- Done: added focused tests for Bocha request/response mapping, `InformationNode`, and runner routing.
- Done: ran Java 17 `mvn test` successfully.
- Done: committed implementation with Chinese commit message `添加Bocha信息检索节点`.

## Scope

- Work only in `C:/MainData/code/Codex_project/M-agent`.
- Inspect `C:/MainData/code/Codex_project/deepresearch-main` as read-only guidance.
- Keep this stage backend-focused and minimal.
- Do not import the reference project's Vue frontend, Redis, RAG modules, MCP modules, full Spring AI Alibaba Graph, or multi-provider search abstraction.

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
- Implementation commit: `6d9e812b818067b860ae43a4d74b297c23e1e219`.
- Working tree was clean immediately after the implementation commit.
- No upstream is configured for `main`.
- Backend default port is `8080`.
- Project instruction says any manually started backend service must be closed after testing.
- Current runner has named nodes for planner, information, research_team, researcher, and reporter.
- Current runner loop is planner -> information -> research_team -> researcher -> research_team -> reporter.
- `ResearchStep.needWebSearch` now triggers Bocha search during `InformationNode`.
- If a planned step requires web search but no Bocha key is configured, the workflow fails clearly instead of using mock results.

## Completed

- Added `SiteInformation`, `StepSearchContext`, and `InformationPayload`.
- Added `WebSearchClient`, `BochaSearchProperties`, `BochaSearchConfiguration`, and `BochaSearchClient`.
- Added Bocha config under `mvp.search.bocha` in `application.yml`.
- Added `InformationNode` and inserted it into `SimpleResearchRunner`.
- Reordered node orders so `information` sits between `planner` and `research_team`.
- Updated `ResearchState` to carry search contexts and de-duplicated site information.
- Updated `ResearchStep` to carry per-step `StepSearchContext`.
- Updated `ResearcherAgent` and `LlmResearcherAgent` so researcher observations can use search context.
- Updated `LlmReporterAgent` to include collected web search sources in the reporter prompt.
- Updated `/chat/stream` to expose site information for information events.
- Updated planner/researcher prompts for real web search context.
- Relaxed `PlannerResponse.title` from required because real model output may omit it and `PlannerOutputMapper` already has a query-title fallback.
- Added tests:
  - `BochaSearchClientTest`
  - `InformationNodeTest`
  - `SimpleResearchRunnerTest`
  - Updated `ResearchControllerLlmWorkflowTest` expected node sequence.

## Decisions

- Use Bocha AI Web Search API only for this search stage.
- Do not use Kimi, Zhipu, Baidu, Tencent, Alibaba web search, Tavily, SerpAPI, or Jina as search providers in this stage.
- Do not migrate to Spring AI Alibaba Graph yet.
- Keep `SimpleResearchRunner` and add a minimal node boundary first.
- Do not fake search results; tests use local in-memory test stubs only around the search interface.
- Keep API keys out of source, tests, assertions, logs, commits, and handoff files.
- Support both `BOCHA_API_KEY` and the existing ignored key-store pattern with provider id `bocha`.

## Evidence / References

- Reference graph configuration: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/config/DeepResearchConfiguration.java`
- Reference `InformationNode`: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/node/InformationNode.java`
- Reference `InformationDispatcher`: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/dispatcher/InformationDispatcher.java`
- Reference search service: `C:/MainData/code/Codex_project/deepresearch-main/src/main/java/com/alibaba/cloud/ai/example/deepresearch/service/SearchInfoService.java`
- Current runner: `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- Current information node: `src/main/java/top/lanshan/manmu/node/InformationNode.java`
- Current Bocha client: `src/main/java/top/lanshan/manmu/search/BochaSearchClient.java`
- Current state/model files: `src/main/java/top/lanshan/manmu/model/ResearchState.java`, `src/main/java/top/lanshan/manmu/model/ResearchStep.java`, `src/main/java/top/lanshan/manmu/model/SiteInformation.java`, `src/main/java/top/lanshan/manmu/model/StepSearchContext.java`
- Current planner prompt: `src/main/resources/prompts/planner.md`
- Current researcher prompt: `src/main/resources/prompts/researcher.md`
- Current app config: `src/main/resources/application.yml`
- Bocha official docs: `https://open.bochaai.com/`

## Files Touched

- `.codex/tasks/add-information-node-bocha-search.md`
- `src/main/java/top/lanshan/manmu/agent/LlmReporterAgent.java`
- `src/main/java/top/lanshan/manmu/agent/LlmResearcherAgent.java`
- `src/main/java/top/lanshan/manmu/agent/PlannerResponse.java`
- `src/main/java/top/lanshan/manmu/agent/ResearcherAgent.java`
- `src/main/java/top/lanshan/manmu/api/ChatController.java`
- `src/main/java/top/lanshan/manmu/model/InformationPayload.java`
- `src/main/java/top/lanshan/manmu/model/ResearchState.java`
- `src/main/java/top/lanshan/manmu/model/ResearchStep.java`
- `src/main/java/top/lanshan/manmu/model/SiteInformation.java`
- `src/main/java/top/lanshan/manmu/model/StepSearchContext.java`
- `src/main/java/top/lanshan/manmu/node/InformationNode.java`
- `src/main/java/top/lanshan/manmu/node/ReporterNode.java`
- `src/main/java/top/lanshan/manmu/node/ResearchTeamNode.java`
- `src/main/java/top/lanshan/manmu/node/ResearcherNode.java`
- `src/main/java/top/lanshan/manmu/runner/SimpleResearchRunner.java`
- `src/main/java/top/lanshan/manmu/search/BochaSearchClient.java`
- `src/main/java/top/lanshan/manmu/search/BochaSearchConfiguration.java`
- `src/main/java/top/lanshan/manmu/search/BochaSearchProperties.java`
- `src/main/java/top/lanshan/manmu/search/WebSearchClient.java`
- `src/main/resources/application.yml`
- `src/main/resources/prompts/planner.md`
- `src/main/resources/prompts/researcher.md`
- `src/test/java/top/lanshan/manmu/api/ResearchControllerLlmWorkflowTest.java`
- `src/test/java/top/lanshan/manmu/node/InformationNodeTest.java`
- `src/test/java/top/lanshan/manmu/runner/SimpleResearchRunnerTest.java`
- `src/test/java/top/lanshan/manmu/search/BochaSearchClientTest.java`

## Commands Run

- `git rev-parse --show-toplevel`
- `git branch --show-current`
- `git status --short`
- `git diff --stat`
- `git diff --name-only`
- `git rev-parse HEAD`
- `git rev-parse --abbrev-ref --symbolic-full-name @{upstream}` failed because no upstream is configured.
- `git merge-base HEAD @{upstream}` failed because no upstream is configured.
- Read `AGENTS.md` with UTF-8.
- Read `.codex/tasks/add-information-node-bocha-search.md`.
- Read `.codex/tasks/add-research-team.md`.
- Read reference `InformationNode`, `InformationDispatcher`, and `SearchInfoService`.
- Searched official/current web docs for Bocha API details after Context7 was unavailable due quota.
- `mvn test '-Dtest=BochaSearchClientTest,InformationNodeTest,SimpleResearchRunnerTest,PlannerOutputMapperTest,ResearchTeamNodeTest'` with Java 17: passed.
- `mvn test` with Java 17: first failed because real planner output omitted title; fixed `PlannerResponse.title`.
- `mvn test '-Dtest=PlannerOutputMapperTest,ResearchControllerLlmWorkflowTest,BochaSearchClientTest,InformationNodeTest,SimpleResearchRunnerTest,ResearchTeamNodeTest'` with Java 17: passed.
- `mvn test` with Java 17: passed.
- `git commit -m "添加Bocha信息检索节点"` created commit `6d9e812b818067b860ae43a4d74b297c23e1e219`.

## Verification

- Latest full verification:
  - Command: `mvn test` with `JAVA_HOME=C:\WorkResources\JDKs\JDK17`.
  - Result: `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- Focused verification before the final full run:
  - Command: `mvn test '-Dtest=PlannerOutputMapperTest,ResearchControllerLlmWorkflowTest,BochaSearchClientTest,InformationNodeTest,SimpleResearchRunnerTest,ResearchTeamNodeTest'`.
  - Result: `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
- No manual backend server was left running; tests used Spring Boot random ports and exited.
- No live Bocha API call was run in this session because local Bocha API key availability is unknown.

## Known Failures / Blockers

- No upstream Git branch configured.
- Real LLM/provider tests can fail from external API rate limits or network timeouts.
- Bocha API key availability is unknown. Configure `BOCHA_API_KEY` or `.local/model-providers.json` provider id `bocha` before live search tests.
- Bocha response mapping is based on official docs and representative response shape under `data.webPages.value[]`; verify with a live Bocha call once a key is available.
- Terminal may show Chinese output as mojibake in some commands; use `Get-Content -Encoding UTF8` when reading Chinese docs.

## Next Actions

- Configure a local Bocha API key without committing it, then run a real `/api/research/stream` or `/chat/stream` request whose planner step sets `need_web_search=true`.
- Improve frontend/client use of `siteInformation` from `information` events if needed, or add a lightweight endpoint/controller helper for configuring the Bocha key.
- Next feature stage can add human feedback or a dedicated PROCESSING/coder node before any Spring AI Alibaba Graph migration.

## Open Questions

- Should the app expose a controller for saving Bocha keys into the existing `.local/model-providers.json`, or is environment-variable configuration enough for now?
- Should `/chat/stream` also attach final aggregate `siteInformation` to reporter or done events, not only information events?
- What default Bocha parameters should be used after live testing: keep `freshness=noLimit`, `summary=true`, `count=5`, or tune per query type?

## Avoid / Do Not Redo

- Do not edit `C:/MainData/code/Codex_project/deepresearch-main`.
- Do not introduce mock search fallback in production code.
- Do not add search providers other than Bocha AI Web Search API for this stage.
- Do not migrate to Spring AI Alibaba Graph yet.
- Do not commit `.local`, `target`, `.idea`, API keys, or generated curl output.
- Do not leave manually started backend services running after tests.

## Resume Prompt
Resume task add-information-node-bocha-search. Read .codex/tasks/add-information-node-bocha-search.md, inspect git status and diff, read project-level instructions such as AGENTS.md, restore the Project Mainline before the stage task details, verify Scope Safety and allowed write roots before editing, continue from Next Actions, and update the handoff file before stopping.
