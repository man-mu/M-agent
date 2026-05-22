package top.lanshan.manmu.agent;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import top.lanshan.manmu.agent.client.AgentClient;
import top.lanshan.manmu.model.CoordinatorDecision;
import top.lanshan.manmu.prompt.PromptService;

@Component
public class LlmCoordinatorAgent implements CoordinatorAgent {

	private final AgentClient agentClient;

	private final PromptService promptService;

	private final CoordinatorOutputMapper outputMapper;

	private final BeanOutputConverter<CoordinatorResponse> outputConverter;

	public LlmCoordinatorAgent(AgentClient agentClient, PromptService promptService,
			CoordinatorOutputMapper outputMapper) {
		this.agentClient = agentClient;
		this.promptService = promptService;
		this.outputMapper = outputMapper;
		this.outputConverter = new BeanOutputConverter<>(CoordinatorResponse.class);
	}

	@Override
	public CoordinatorDecision coordinate(String query, boolean deepResearchEnabled) {
		if (deepResearchEnabled && outputMapper.isSubstantiveResearchRequest(query)) {
			return new CoordinatorDecision(top.lanshan.manmu.model.CoordinatorRoute.DEEP_RESEARCH, true, null,
					"Substantive request routed to the research workflow.");
		}

		String userPrompt = """
				User question:
				%s

				Deep research is enabled: %s
				""".formatted(query, deepResearchEnabled);
		String modelOutput = agentClient.call(promptService.load("coordinator") + "\n\n" + outputConverter.getFormat(),
				userPrompt);
		CoordinatorResponse response = outputConverter.convert(modelOutput);
		return outputMapper.toDecision(response, query, deepResearchEnabled);
	}

}
