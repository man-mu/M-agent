package top.lanshan.manmu.agent;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import top.lanshan.manmu.agent.client.AgentClient;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.prompt.PromptService;

@Component
public class LlmPlannerAgent implements PlannerAgent {

	private final AgentClient agentClient;

	private final PromptService promptService;

	private final PlannerOutputMapper outputMapper;

	private final BeanOutputConverter<PlannerResponse> outputConverter;

	public LlmPlannerAgent(AgentClient agentClient, PromptService promptService, PlannerOutputMapper outputMapper) {
		this.agentClient = agentClient;
		this.promptService = promptService;
		this.outputMapper = outputMapper;
		this.outputConverter = new BeanOutputConverter<>(PlannerResponse.class);
	}

	@Override
	public ResearchPlan plan(String query, int maxSteps) {
		String userPrompt = """
				User question:
				%s

				Maximum number of steps: %d
				""".formatted(query, maxSteps);

		String modelOutput = agentClient.call(promptService.load("planner") + "\n\n" + outputConverter.getFormat(),
				userPrompt);
		PlannerResponse response = outputConverter.convert(modelOutput);

		return outputMapper.toResearchPlan(response, maxSteps);
	}

}
