package top.lanshan.manmu.agent;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import top.lanshan.manmu.agent.client.AgentClient;
import top.lanshan.manmu.prompt.PromptService;

import static org.assertj.core.api.Assertions.assertThat;

class LlmPlannerAgentTest {

	@Test
	void promptIncludesBackgroundContextWhenPresent() {
		RecordingAgentClient agentClient = new RecordingAgentClient();
		LlmPlannerAgent plannerAgent = new LlmPlannerAgent(agentClient, new PromptService(new DefaultResourceLoader()),
				new PlannerOutputMapper());

		plannerAgent.plan("Continue the topic.", 2, "Focus on risks.", "Prior report context",
				java.util.List.of("Continue the topic.", "Continue the topic with Java backend risks"),
				"Current web background context");

		assertThat(agentClient.userPrompt).contains("Continue the topic.")
			.contains("Optimized research queries")
			.contains("Continue the topic with Java backend risks")
			.contains("Current-question background investigation from web search")
			.contains("Current web background context")
			.contains("Recent completed reports from the same session")
			.contains("Prior report context")
			.contains("Human feedback for replanning")
			.contains("Focus on risks.");
	}

	private static class RecordingAgentClient implements AgentClient {

		private String userPrompt;

		@Override
		public String call(String systemPrompt, String userPrompt) {
			this.userPrompt = userPrompt;
			return """
					{
					  "title": "Context aware plan",
					  "has_enough_context": true,
					  "thought": "Use prior reports.",
					  "steps": [
					    {
					      "title": "Review continuity",
					      "description": "Use prior findings to plan the next step.",
					      "need_web_search": false,
					      "step_type": "RESEARCH"
					    },
					    {
					      "title": "Synthesize",
					      "description": "Prepare the final structure.",
					      "need_web_search": false,
					      "step_type": "PROCESSING"
					    }
					  ]
					}
					""";
		}

	}

}
