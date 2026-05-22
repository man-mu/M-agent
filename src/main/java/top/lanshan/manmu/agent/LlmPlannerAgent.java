package top.lanshan.manmu.agent;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import top.lanshan.manmu.agent.client.AgentClient;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.prompt.PromptService;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
		return plan(query, maxSteps, null);
	}

	@Override
	public ResearchPlan plan(String query, int maxSteps, String feedbackContent) {
		return plan(query, maxSteps, feedbackContent, null);
	}

	@Override
	public ResearchPlan plan(String query, int maxSteps, String feedbackContent, String backgroundContext) {
		return plan(query, maxSteps, feedbackContent, backgroundContext, List.of());
	}

	@Override
	public ResearchPlan plan(String query, int maxSteps, String feedbackContent, String backgroundContext,
			List<String> optimizedQueries) {
		String userPrompt = """
				User question:
				%s

				Maximum number of steps: %d
				%s
				%s
				%s
				""".formatted(query, maxSteps, optimizedQueriesPrompt(optimizedQueries),
				backgroundContextPrompt(backgroundContext), feedbackPrompt(feedbackContent));

		String modelOutput = agentClient.call(promptService.load("planner") + "\n\n" + outputConverter.getFormat(),
				userPrompt);
		PlannerResponse response = outputConverter.convert(modelOutput);

		return outputMapper.toResearchPlan(response, query, maxSteps);
	}

	private String optimizedQueriesPrompt(List<String> optimizedQueries) {
		if (optimizedQueries == null || optimizedQueries.isEmpty()) {
			return "";
		}
		String queries = IntStream.range(0, optimizedQueries.size())
			.mapToObj(index -> "%d. %s".formatted(index + 1, optimizedQueries.get(index)))
			.collect(Collectors.joining("\n"));
		return """

				Optimized research queries:
				%s
				Use these query variants to understand the user's intent and improve the coverage of the plan. Keep the original user question as the source of truth.
				""".formatted(queries);
	}

	private String backgroundContextPrompt(String backgroundContext) {
		if (backgroundContext == null || backgroundContext.isBlank()) {
			return "";
		}
		return """

				Recent completed reports from the same session:
				%s
				Use this prior session context to avoid repeating work and to plan the next research run with continuity.
				""".formatted(backgroundContext.strip());
	}

	private String feedbackPrompt(String feedbackContent) {
		if (feedbackContent == null || feedbackContent.isBlank()) {
			return "";
		}
		return """

				Human feedback for replanning:
				%s
				Revise the research plan to address this feedback before execution.
				""".formatted(feedbackContent.strip());
	}

}
