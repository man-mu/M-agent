package top.lanshan.manmu.agent;

import org.springframework.stereotype.Component;
import top.lanshan.manmu.agent.client.AgentClient;
import top.lanshan.manmu.mcp.McpToolProvider;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.StepSearchContext;
import top.lanshan.manmu.prompt.PromptService;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class LlmResearcherAgent implements ResearcherAgent {

	private final AgentClient agentClient;

	private final PromptService promptService;

	private final McpToolProvider mcpToolProvider;

	public LlmResearcherAgent(AgentClient agentClient, PromptService promptService,
			McpToolProvider mcpToolProvider) {
		this.agentClient = agentClient;
		this.promptService = promptService;
		this.mcpToolProvider = mcpToolProvider;
	}

	@Override
	public String research(String query, ResearchStep step, StepSearchContext searchContext) {
		String userPrompt = """
				Query:
				%s

				Current research step:
				- Title: %s
				- Type: %s
				- Need web search: %s
				- Description: %s

				Web search context:
				%s

				Write a compact Markdown observation for this single step. Use only the provided query and step context,
				ground external facts in the supplied web search context when present, and include concrete takeaways for the final report.
				""".formatted(query, step.title(), step.stepType(), step.needWebSearch(), step.description(),
				searchContext == null ? "No web search context was provided for this step." : searchContext.promptText());
		String systemPrompt = buildSystemPrompt();
		return agentClient.call(systemPrompt, userPrompt);
	}

	private String buildSystemPrompt() {
		String base = promptService.load("researcher");
		String toolNames = getMcpToolNames();
		if (toolNames.isEmpty()) {
			return base;
		}
		return base + "\n\n## Available MCP Tools\n" + toolNames;
	}

	private String getMcpToolNames() {
		try {
			var callbacks = mcpToolProvider.getToolCallbacks();
			if (callbacks == null || callbacks.length == 0) {
				return "";
			}
			return Arrays.stream(callbacks)
					.map(cb -> "- **" + cb.getToolDefinition().name() + "**: "
							+ cb.getToolDefinition().description())
					.collect(Collectors.joining("\n"));
		} catch (Exception e) {
			return "";
		}
	}

}
