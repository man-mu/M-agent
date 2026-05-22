package top.lanshan.manmu.agent;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import top.lanshan.manmu.agent.client.AgentClient;
import top.lanshan.manmu.prompt.PromptService;

import java.util.List;

@Component
public class LlmQueryRewriteAgent implements QueryRewriteAgent {

	private final AgentClient agentClient;

	private final PromptService promptService;

	private final QueryRewriteOutputMapper outputMapper;

	private final BeanOutputConverter<QueryRewriteResponse> outputConverter;

	public LlmQueryRewriteAgent(AgentClient agentClient, PromptService promptService,
			QueryRewriteOutputMapper outputMapper) {
		this.agentClient = agentClient;
		this.promptService = promptService;
		this.outputMapper = outputMapper;
		this.outputConverter = new BeanOutputConverter<>(QueryRewriteResponse.class);
	}

	@Override
	public List<String> rewrite(String query, int optimizeQueryNum) {
		int count = outputMapper.clampCount(optimizeQueryNum);
		if (count == 0) {
			return List.of();
		}

		String userPrompt = """
				Original user question:
				%s

				Number of optimized queries to return: %d
				""".formatted(query, count);
		String modelOutput = agentClient.call(promptService.load("query-rewrite") + "\n\n" + outputConverter.getFormat(),
				userPrompt);
		QueryRewriteResponse response = outputConverter.convert(modelOutput);
		return outputMapper.toOptimizedQueries(response, query, count);
	}

}
