package top.lanshan.manmu.agent;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import top.lanshan.manmu.agent.client.AgentClient;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.StepType;
import top.lanshan.manmu.prompt.PromptService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmReporterAgentTest {

	@Test
	void injectsUserProfileContextWithEvidenceGuardrail() {
		RecordingAgentClient agentClient = new RecordingAgentClient("report");
		LlmReporterAgent agent = new LlmReporterAgent(agentClient, new PromptService(new DefaultResourceLoader()));
		ResearchState state = ResearchState.from(new ResearchRequest("Explain workflow.", "thread-1", 2));
		ResearchStep step = new ResearchStep("Step", "Do work", false, StepType.RESEARCH, "Findings",
				ResearchStep.STATUS_COMPLETED);
		state.plan(new ResearchPlan("Plan", true, "Think", List.of(step)));
		state.addObservation("Evidence from research.");

		String report = agent.report(state, "summary: backend engineer; detail: concise");

		assertThat(report).isEqualTo("report");
		assertThat(agentClient.userPrompt)
			.contains("User profile context: summary: backend engineer; detail: concise")
			.contains("Use this only to adapt explanation depth and style")
			.contains("Do not infer facts not present in research evidence");
	}

	private static class RecordingAgentClient implements AgentClient {

		private final String response;

		private String userPrompt;

		RecordingAgentClient(String response) {
			this.response = response;
		}

		@Override
		public String call(String systemPrompt, String userPrompt) {
			this.userPrompt = userPrompt;
			return response;
		}

	}

}
