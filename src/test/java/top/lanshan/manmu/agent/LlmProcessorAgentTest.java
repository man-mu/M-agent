package top.lanshan.manmu.agent;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import top.lanshan.manmu.agent.client.AgentClient;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.SiteInformation;
import top.lanshan.manmu.model.StepSearchContext;
import top.lanshan.manmu.model.StepType;
import top.lanshan.manmu.prompt.PromptService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmProcessorAgentTest {

	@Test
	void promptIncludesPriorObservationsAndSiteInformation() {
		RecordingAgentClient agentClient = new RecordingAgentClient();
		LlmProcessorAgent processorAgent = new LlmProcessorAgent(agentClient,
				new PromptService(new DefaultResourceLoader()));
		ResearchStep processingStep = new ResearchStep("Synthesize", "Summarize the evidence.", false,
				StepType.PROCESSING, null, ResearchStep.STATUS_PENDING);
		ResearchState state = ResearchState.from(new ResearchRequest("Explain agent workflows.", "thread-1", 2));
		state.plan(new ResearchPlan("Agent plan", true, "Collect then synthesize.",
				List.of(new ResearchStep("Research", "Find sources.", true, StepType.RESEARCH, "Observation",
						ResearchStep.STATUS_COMPLETED), processingStep)));
		state.addObservation("Planner and reporter should stay separated.");
		state.addSearchContext(new StepSearchContext("Research", "agent workflow search",
				List.of(new SiteInformation("DeepResearch source", "https://example.test/deepresearch", "Snippet",
						"Source summary", "Example", "", ""))));

		String result = processorAgent.process(state, processingStep);

		assertThat(result).isEqualTo("processed");
		assertThat(agentClient.systemPrompt).contains("processor");
		assertThat(agentClient.userPrompt).contains("Explain agent workflows.")
			.contains("Synthesize")
			.contains("Planner and reporter should stay separated.")
			.contains("DeepResearch source")
			.contains("https://example.test/deepresearch");
	}

	private static class RecordingAgentClient implements AgentClient {

		private String systemPrompt;

		private String userPrompt;

		@Override
		public String call(String systemPrompt, String userPrompt) {
			this.systemPrompt = systemPrompt;
			this.userPrompt = userPrompt;
			return "processed";
		}

	}

}
