package top.lanshan.manmu.agent;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import top.lanshan.manmu.agent.client.AgentClient;
import top.lanshan.manmu.model.CoordinatorDecision;
import top.lanshan.manmu.model.CoordinatorRoute;
import top.lanshan.manmu.prompt.PromptService;

import static org.assertj.core.api.Assertions.assertThat;

class LlmCoordinatorAgentTest {

	@Test
	void promptsModelAndParsesDeepResearchRoute() {
		RecordingAgentClient agentClient = new RecordingAgentClient("""
				{
				  "next_route": "DEEP_RESEARCH",
				  "direct_answer": "",
				  "thought": "Needs a plan."
				}
				""");
		LlmCoordinatorAgent agent = new LlmCoordinatorAgent(agentClient, new PromptService(new DefaultResourceLoader()),
				new CoordinatorOutputMapper());

		CoordinatorDecision decision = agent.coordinate("Tell me more.", true, "");

		assertThat(agentClient.systemPrompt).contains("DEEP_RESEARCH").contains("DIRECT_ANSWER");
		assertThat(agentClient.userPrompt).contains("Tell me more.")
			.contains("Deep research is enabled: true");
		assertThat(decision.nextRoute()).isEqualTo(CoordinatorRoute.DEEP_RESEARCH);
		assertThat(decision.deepResearch()).isTrue();
	}

	@Test
	void parsesDirectAnswerRoute() {
		RecordingAgentClient agentClient = new RecordingAgentClient("""
				{
				  "next_route": "DIRECT_ANSWER",
				  "direct_answer": "Hello.",
				  "thought": "Greeting."
				}
				""");
		LlmCoordinatorAgent agent = new LlmCoordinatorAgent(agentClient, new PromptService(new DefaultResourceLoader()),
				new CoordinatorOutputMapper());

		CoordinatorDecision decision = agent.coordinate("Say hi.", false, "");

		assertThat(agentClient.userPrompt).contains("Deep research is enabled: false");
		assertThat(decision.nextRoute()).isEqualTo(CoordinatorRoute.DIRECT_ANSWER);
		assertThat(decision.directAnswer()).isEqualTo("Hello.");
	}

	@Test
	void parsesDirectAnswerFromFencedJsonWithLeadingText() {
		RecordingAgentClient agentClient = new RecordingAgentClient("""
				Based on the tool output, here is the answer:

				```json
				{
				  "next_route": "DIRECT_ANSWER",
				  "direct_answer": "北京当前阴，温度 27°C。",
				  "thought": "Used the weather tool."
				}
				```
				""");
		LlmCoordinatorAgent agent = new LlmCoordinatorAgent(agentClient, new PromptService(new DefaultResourceLoader()),
				new CoordinatorOutputMapper());

		CoordinatorDecision decision = agent.coordinate("@weather-now 查询北京今天实时天气", false, "");

		assertThat(decision.nextRoute()).isEqualTo(CoordinatorRoute.DIRECT_ANSWER);
		assertThat(decision.directAnswer()).contains("北京当前阴");
	}

	@Test
	void treatsMarkdownOutputAsDirectAnswerWhenDeepResearchDisabled() {
		RecordingAgentClient agentClient = new RecordingAgentClient("""
				Based on the real-time weather data from QWeather:

				**Beijing real-time weather**

				- Temperature: 27C
				- Humidity: 56%
				""");
		LlmCoordinatorAgent agent = new LlmCoordinatorAgent(agentClient, new PromptService(new DefaultResourceLoader()),
				new CoordinatorOutputMapper());

		CoordinatorDecision decision = agent.coordinate("@weather-now query Beijing weather", false, "");

		assertThat(decision.nextRoute()).isEqualTo(CoordinatorRoute.DIRECT_ANSWER);
		assertThat(decision.directAnswer()).contains("Beijing real-time weather");
		assertThat(decision.directAnswer()).contains("Temperature");
	}

	@Test
	void injectsUserProfileContextWithGuardrail() {
		RecordingAgentClient agentClient = new RecordingAgentClient("""
				{
				  "next_route": "DIRECT_ANSWER",
				  "direct_answer": "Hello.",
				  "thought": "Greeting."
				}
				""");
		LlmCoordinatorAgent agent = new LlmCoordinatorAgent(agentClient, new PromptService(new DefaultResourceLoader()),
				new CoordinatorOutputMapper());

		agent.coordinate("Say hi.", false, "summary: backend engineer; detail: concise");

		assertThat(agentClient.userPrompt).contains("User profile context: summary: backend engineer; detail: concise")
			.contains("Use this only to adapt explanation depth and style")
			.contains("Do not infer facts not present in research evidence");
	}

	@Test
	void keepsSubstantiveRequestsOnDeepResearchWhenEnabled() {
		RecordingAgentClient agentClient = new RecordingAgentClient(null);
		LlmCoordinatorAgent agent = new LlmCoordinatorAgent(agentClient, new PromptService(new DefaultResourceLoader()),
				new CoordinatorOutputMapper());

		CoordinatorDecision decision = agent.coordinate("Use three sentences to explain agent workflow routing.", true, "");

		assertThat(agentClient.calls).isZero();
		assertThat(decision.nextRoute()).isEqualTo(CoordinatorRoute.DEEP_RESEARCH);
		assertThat(decision.directAnswer()).isNull();
	}

	private static class RecordingAgentClient implements AgentClient {

		private final String response;

		private String systemPrompt;

		private String userPrompt;

		private int calls;

		RecordingAgentClient(String response) {
			this.response = response;
		}

		@Override
		public String call(String systemPrompt, String userPrompt) {
			calls++;
			this.systemPrompt = systemPrompt;
			this.userPrompt = userPrompt;
			return response;
		}

	}

}
