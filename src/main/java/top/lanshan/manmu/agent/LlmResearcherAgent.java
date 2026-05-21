package top.lanshan.manmu.agent;

import org.springframework.stereotype.Component;
import top.lanshan.manmu.agent.client.AgentClient;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.prompt.PromptService;

@Component
public class LlmResearcherAgent implements ResearcherAgent {

	private final AgentClient agentClient;

	private final PromptService promptService;

	public LlmResearcherAgent(AgentClient agentClient, PromptService promptService) {
		this.agentClient = agentClient;
		this.promptService = promptService;
	}

	@Override
	public String research(String query, ResearchStep step) {
		String userPrompt = """
				Query:
				%s

				Current research step:
				- Title: %s
				- Type: %s
				- Need web search: %s
				- Description: %s

				Write a compact Markdown observation for this single step. Use only the provided query and step context,
				avoid inventing precise external facts, and include concrete takeaways for the final report.
				""".formatted(query, step.title(), step.stepType(), step.needWebSearch(), step.description());
		return agentClient.call(promptService.load("researcher"), userPrompt);
	}

}
