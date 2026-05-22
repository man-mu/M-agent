package top.lanshan.manmu.agent;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import top.lanshan.manmu.agent.client.AgentClient;
import top.lanshan.manmu.prompt.PromptService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmQueryRewriteAgentTest {

	@Test
	void promptsModelAndParsesOptimizedQueries() {
		RecordingAgentClient agentClient = new RecordingAgentClient();
		LlmQueryRewriteAgent agent = new LlmQueryRewriteAgent(agentClient,
				new PromptService(new DefaultResourceLoader()), new QueryRewriteOutputMapper());

		List<String> queries = agent.rewrite("Compare Spring AI and LangChain.", 2);

		assertThat(agentClient.systemPrompt).contains("optimize_queries");
		assertThat(agentClient.userPrompt).contains("Compare Spring AI and LangChain.")
			.contains("Number of optimized queries to return: 2");
		assertThat(queries).containsExactly("Compare Spring AI and LangChain.",
				"Spring AI vs LangChain Java agent workflow comparison");
	}

	private static class RecordingAgentClient implements AgentClient {

		private String systemPrompt;

		private String userPrompt;

		@Override
		public String call(String systemPrompt, String userPrompt) {
			this.systemPrompt = systemPrompt;
			this.userPrompt = userPrompt;
			return """
					{
					  "optimize_queries": [
					    "Compare Spring AI and LangChain.",
					    "Spring AI vs LangChain Java agent workflow comparison"
					  ]
					}
					""";
		}

	}

}
