package top.lanshan.manmu.agent;

import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import top.lanshan.manmu.agent.client.AgentClient;
import top.lanshan.manmu.model.CoordinatorDecision;
import top.lanshan.manmu.model.CoordinatorRoute;
import top.lanshan.manmu.prompt.PromptService;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LlmCoordinatorAgent implements CoordinatorAgent {

	private static final Pattern FENCED_JSON = Pattern.compile("(?s)```(?:json)?\\s*(\\{.*?})\\s*```");

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
	public CoordinatorDecision coordinate(String query, boolean deepResearchEnabled, String userProfileContext) {
		return coordinate(query, deepResearchEnabled, userProfileContext, null);
	}

	@Override
	public CoordinatorDecision coordinate(String query, boolean deepResearchEnabled, String userProfileContext,
			String conversationHistoryContext) {
		if (deepResearchEnabled && outputMapper.isSubstantiveResearchRequest(query)) {
			return new CoordinatorDecision(top.lanshan.manmu.model.CoordinatorRoute.DEEP_RESEARCH, true, null,
					"Substantive request routed to the research workflow.");
		}

		String profileSection = (userProfileContext != null && !userProfileContext.isBlank())
				? """

				User profile context: %s
				Use this only to adapt explanation depth and style. Do not infer facts not present in research evidence.
				""".formatted(userProfileContext)
				: "";
		String memorySection = conversationHistoryPrompt(conversationHistoryContext);

		String userPrompt = """
				User question:
				%s

				Deep research is enabled: %s%s%s
				""".formatted(query, deepResearchEnabled, profileSection, memorySection);
		String modelOutput = agentClient.call(promptService.load("coordinator") + "\n\n" + outputConverter.getFormat(),
				userPrompt);
		CoordinatorResponse response = parseResponse(modelOutput, query, deepResearchEnabled);
		return outputMapper.toDecision(response, query, deepResearchEnabled);
	}

	private String conversationHistoryPrompt(String conversationHistoryContext) {
		if (conversationHistoryContext == null || conversationHistoryContext.isBlank()) {
			return "";
		}
		return """

				Short-term conversation memory:
				%s
				Use this only to understand session context, follow-up references, and user preferences. Do not treat prior conversation content as external factual evidence.
				""".formatted(conversationHistoryContext.strip());
	}

	private CoordinatorResponse parseResponse(String modelOutput, String query, boolean deepResearchEnabled) {
		String candidate = extractJsonObject(modelOutput);
		if (allowsDirectAnswerFallback(query, deepResearchEnabled) && !looksLikeJsonObject(candidate)) {
			return directAnswerResponse(modelOutput);
		}
		try {
			return outputConverter.convert(candidate);
		}
		catch (RuntimeException ex) {
			if (!allowsDirectAnswerFallback(query, deepResearchEnabled)) {
				throw ex;
			}
			return extractDirectAnswerFromJson(candidate)
				.or(() -> extractDirectAnswerFromJson(modelOutput))
				.map(answer -> new CoordinatorResponse(CoordinatorRoute.DIRECT_ANSWER, answer,
						"BeanOutputConverter failed, extracted direct_answer from JSON fallback."))
				.orElse(directAnswerResponse(modelOutput));
		}
	}

	private java.util.Optional<String> extractDirectAnswerFromJson(String text) {
		if (text == null || text.isBlank()) {
			return java.util.Optional.empty();
		}
		try {
			com.fasterxml.jackson.databind.JsonNode node =
					new com.fasterxml.jackson.databind.ObjectMapper().readTree(text);
			com.fasterxml.jackson.databind.JsonNode answer = node.get("direct_answer");
			if (answer != null && answer.isTextual() && !answer.asText().isBlank()) {
				return java.util.Optional.of(answer.asText());
			}
		}
		catch (Exception ignored) {
		}
		return java.util.Optional.empty();
	}

	private boolean allowsDirectAnswerFallback(String query, boolean deepResearchEnabled) {
		return !deepResearchEnabled || (query != null && query.stripLeading().startsWith("@"));
	}

	private boolean looksLikeJsonObject(String text) {
		return text != null && text.stripLeading().startsWith("{");
	}

	private CoordinatorResponse directAnswerResponse(String modelOutput) {
		String answer = modelOutput == null ? "" : modelOutput.strip();
		return new CoordinatorResponse(CoordinatorRoute.DIRECT_ANSWER, answer,
				"Model returned direct answer text instead of structured coordinator JSON.");
	}

	private String extractJsonObject(String modelOutput) {
		if (modelOutput == null) {
			return null;
		}
		String trimmed = modelOutput.strip();
		Matcher fenced = FENCED_JSON.matcher(trimmed);
		if (fenced.find()) {
			return sanitizeJson(fenced.group(1).strip());
		}
		int start = trimmed.indexOf('{');
		int end = trimmed.lastIndexOf('}');
		if (start >= 0 && end > start) {
			return sanitizeJson(trimmed.substring(start, end + 1));
		}
		return trimmed;
	}

	private String sanitizeJson(String json) {
		// LLMs commonly emit trailing commas before } or ] — Jackson rejects them
		return json.replaceAll(",\\s*([}\\]])", "$1");
	}

}
