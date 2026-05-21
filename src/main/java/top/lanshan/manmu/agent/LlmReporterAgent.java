package top.lanshan.manmu.agent;

import org.springframework.stereotype.Component;
import top.lanshan.manmu.agent.client.AgentClient;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.prompt.PromptService;

import java.util.stream.Collectors;

@Component
public class LlmReporterAgent implements ReporterAgent {

	private final AgentClient agentClient;

	private final PromptService promptService;

	public LlmReporterAgent(AgentClient agentClient, PromptService promptService) {
		this.agentClient = agentClient;
		this.promptService = promptService;
	}

	@Override
	public String report(ResearchState state) {
		String steps = state.plan()
			.steps()
			.stream()
			.map(step -> "- [%s] %s: %s".formatted(step.type(), step.title(), step.description()))
			.collect(Collectors.joining("\n"));
		String observations = state.observations()
			.stream()
			.map(item -> "## Observation\n\n" + item)
			.collect(Collectors.joining("\n\n"));

		String userPrompt = """
				Query:
				%s

				Plan title:
				%s

				Plan steps:
				%s

				Research observations:
				%s

				Write the final answer as concise Markdown. Include:
				1. a short conclusion,
				2. key findings grounded in the observations,
				3. next implementation steps.
				""".formatted(state.query(), state.plan().title(), steps, observations);
		return agentClient.call(promptService.load("reporter"), userPrompt);
	}

}
