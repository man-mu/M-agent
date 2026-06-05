package top.lanshan.manmu.agent.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.lanshan.manmu.mcp.McpToolProvider;
import top.lanshan.manmu.modelprovider.RoutingChatModel;
import top.lanshan.manmu.skill.service.SkillDefinition;
import top.lanshan.manmu.skill.service.SkillService;
import top.lanshan.manmu.skill.service.SkillToolProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SpringAiAgentClient implements AgentClient {

	private static final Logger logger = LoggerFactory.getLogger(SpringAiAgentClient.class);
	private static final Pattern SKILL_AT_PREFIX = Pattern.compile("^\\s*@(\\S+)\\s*(.*)", Pattern.DOTALL);
	private static final Pattern USER_QUESTION_BLOCK = Pattern
		.compile("(?s)(?:^|\\R)\\s*User question:\\s*\\R(.*?)(?:\\R\\s*Deep research is enabled:|\\z)");
	private static final Pattern KEY_VALUE_PAIR = Pattern.compile("--(\\w+)=(\"[^\"]*\"|\\S+)");
	private static final Pattern DIRECT_QWEATHER_LOCATION = Pattern
		.compile("^\\d{6,12}$|^-?\\d+(\\.\\d+)?\\s*,\\s*-?\\d+(\\.\\d+)?$");

	private final RoutingChatModel routingChatModel;

	@Autowired(required = false)
	private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@Autowired(required = false)
	private McpToolProvider mcpToolProvider;

	@Autowired(required = false)
	private SkillToolProvider skillToolProvider;

	@Autowired(required = false)
	private SkillService skillService;

	public SpringAiAgentClient(RoutingChatModel routingChatModel) {
		this.routingChatModel = routingChatModel;
	}

	@Override
	public String call(String systemPrompt, String userPrompt) {
		String effectiveSystem = resolveExplicitSkillCall(systemPrompt, userPrompt);
		effectiveSystem = appendSkillSummary(effectiveSystem);

		ChatClient chatClient = ChatClient.builder(routingChatModel).build();
		ChatClient.ChatClientRequestSpec request = chatClient.prompt()
				.system(effectiveSystem)
				.user(userPrompt);

		List<ToolCallback> allCallbacks = new ArrayList<>();

		if (mcpToolProvider != null) {
			ToolCallback[] mcpCallbacks = mcpToolProvider.getToolCallbacks();
			if (mcpCallbacks.length > 0) {
				for (ToolCallback cb : mcpCallbacks) {
					allCallbacks.add(cb);
				}
				logger.debug("MCP tools attached: {} tool(s)", mcpCallbacks.length);
			}
		}

		if (skillToolProvider != null) {
			ToolCallback[] skillCallbacks = skillToolProvider.getToolCallbacks();
			if (skillCallbacks.length > 0) {
				for (ToolCallback cb : skillCallbacks) {
					allCallbacks.add(cb);
				}
				logger.debug("Skill tools attached: {} tool(s)", skillCallbacks.length);
			}
		}

		if (!allCallbacks.isEmpty()) {
			request = request.toolCallbacks(allCallbacks.toArray(new ToolCallback[0]));
		}

		try {
			return request.call().content();
		}
		catch (RuntimeException ex) {
			if (isReadTimeout(ex)) {
				logger.warn("Model provider request timed out", ex);
				throw new IllegalStateException("模型服务响应超时，请稍后重试，或在设置中切换到其他模型。", ex);
			}
			throw ex;
		}
	}

	private boolean isReadTimeout(Throwable throwable) {
		Throwable current = throwable;
		while (current != null) {
			String simpleName = current.getClass().getSimpleName();
			if ("ReadTimeoutException".equals(simpleName)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	/**
	 * Appends a skill availability summary to the system prompt so the LLM
	 * knows when to use skill tools (auto-trigger discoverability).
	 */
	private String appendSkillSummary(String systemPrompt) {
		if (skillToolProvider == null) {
			return systemPrompt;
		}
		String summary = skillToolProvider.getSkillSummary();
		if (summary.isEmpty()) {
			return systemPrompt;
		}
		return systemPrompt + "\n\n" + summary;
	}

	/**
	 * Detects {@code @skill-name} prefix in the user message and renders the
	 * skill template directly into the system prompt. LLM follows the skill
	 * instructions immediately — no tool-call round-trip.
	 */
	private String resolveExplicitSkillCall(String systemPrompt, String userPrompt) {
		if (skillService == null) {
			return systemPrompt;
		}

		Optional<String> explicitText = explicitSkillText(userPrompt);
		if (explicitText.isEmpty()) {
			return systemPrompt;
		}

		Matcher m = SKILL_AT_PREFIX.matcher(explicitText.get());
		if (!m.find()) {
			return systemPrompt;
		}

		String skillName = m.group(1);
		String remainingText = m.group(2);

		SkillDefinition def = skillService.getDefinition(skillName).orElse(null);
		if (def == null || !def.isEnabled()) {
			logger.debug("@{}: skill not found or disabled, treating as normal message", skillName);
			return systemPrompt;
		}

		Map<String, Object> params = extractSkillParams(def, remainingText);
		String rendered = skillService.renderSkill(skillName, params);
		if (rendered == null) {
			return systemPrompt;
		}
		String toolContext = explicitMcpToolContext(skillName, params);
		if (!toolContext.isBlank()) {
			rendered = rendered + "\n\n" + toolContext;
		}

		logger.info("@{} explicitly invoked, template rendered ({} chars)", skillName, rendered.length());
		return rendered + "\n\n---\n\n" + systemPrompt;
	}

	private String explicitMcpToolContext(String skillName, Map<String, Object> params) {
		if (!"weather-now".equals(skillName) || mcpToolProvider == null) {
			return "";
		}
		ToolCallback weatherTool = findToolCallback("weather_now");
		if (weatherTool == null) {
			return """
					## weather_now MCP 工具状态

					未找到 `weather_now` MCP 工具。请提示用户检查本地和风天气 MCP 服务是否已启动并连接。
					""";
		}
		try {
			Map<String, Object> toolInput = weatherToolInput(params);
			String result = weatherTool.call(objectMapper.writeValueAsString(toolInput));
			return """
					## weather_now MCP 工具真实返回

					以下内容来自已连接的 `weather_now` MCP 工具。请基于这份真实返回回答用户，不要说“请稍等”，不要编造或使用 mock 数据。

					```text
					%s
					```
					""".formatted(result);
		}
		catch (JsonProcessingException ex) {
			logger.warn("Failed to serialize weather_now input: {}", safeMessage(ex));
			return "";
		}
		catch (RuntimeException ex) {
			logger.warn("weather_now MCP tool call failed: {}", safeMessage(ex));
			return """
					## weather_now MCP 工具调用失败

					调用 `weather_now` MCP 工具失败：%s。请提示用户检查本地 MCP 服务和 QWEATHER_API_KEY / QWEATHER_API_HOST 配置。
					""".formatted(safeMessage(ex));
		}
	}

	private ToolCallback findToolCallback(String toolName) {
		for (ToolCallback callback : mcpToolProvider.getToolCallbacks()) {
			if (callback != null && callback.getToolDefinition() != null
					&& matchesToolName(toolName, callback.getToolDefinition().name())) {
				return callback;
			}
		}
		return null;
	}

	private boolean matchesToolName(String requestedToolName, String callbackToolName) {
		if (callbackToolName == null || callbackToolName.isBlank()) {
			return false;
		}
		String normalizedCallbackName = callbackToolName.strip();
		return requestedToolName.equals(normalizedCallbackName)
				|| normalizedCallbackName.endsWith("_" + requestedToolName);
	}

	private Map<String, Object> weatherToolInput(Map<String, Object> params) {
		Map<String, Object> input = new LinkedHashMap<>(params);
		Object location = input.get("location");
		if (location instanceof String text) {
			input.put("location", normalizeWeatherLocation(text));
		}
		return input;
	}

	private String normalizeWeatherLocation(String text) {
		String normalized = text == null ? "" : text.strip();
		if (DIRECT_QWEATHER_LOCATION.matcher(normalized).matches()) {
			return normalized;
		}
		normalized = normalized.replaceAll("[，,。！？?；;：:]", " ");
		normalized = normalized.replaceAll("\\s+", " ").strip();
		normalized = normalized.replaceAll("^(请|帮我|麻烦)?\\s*(查询|查一下|查|看看|获取|告诉我)\\s*", "");
		normalized = normalized.replaceAll("\\s*(今天|今日|现在|当前|实时|目前|此刻|的)+\\s*(天气情况|天气|气温|温度|情况|怎么样|如何)?\\s*$", "");
		normalized = normalized.replaceAll("\\s*(今天|今日|现在|当前|实时|的)?\\s*(天气情况|天气|气温|温度|情况|怎么样|如何)\\s*$", "");
		normalized = normalized.strip();
		return normalized.isBlank() ? text.strip() : normalized;
	}

	private String safeMessage(Throwable ex) {
		if (ex == null) {
			return "Unknown error";
		}
		return ex.getMessage() == null || ex.getMessage().isBlank() ? ex.getClass().getSimpleName() : ex.getMessage();
	}

	private Optional<String> explicitSkillText(String userPrompt) {
		if (userPrompt == null || userPrompt.isBlank()) {
			return Optional.empty();
		}
		String direct = userPrompt.stripLeading();
		if (direct.startsWith("@")) {
			return Optional.of(direct);
		}
		Matcher question = USER_QUESTION_BLOCK.matcher(userPrompt);
		if (!question.find()) {
			return Optional.empty();
		}
		String extracted = question.group(1).strip();
		return extracted.startsWith("@") ? Optional.of(extracted) : Optional.empty();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> extractSkillParams(SkillDefinition def, String text) {
		Map<String, Object> params = new LinkedHashMap<>();
		String remaining = text;

		Matcher kv = KEY_VALUE_PAIR.matcher(text);
		while (kv.find()) {
			String key = kv.group(1);
			String value = kv.group(2);
			if (value.startsWith("\"") && value.endsWith("\"")) {
				value = value.substring(1, value.length() - 1);
			}
			params.put(key, value);
			remaining = remaining.replace(kv.group(), "").trim();
		}

		List<String> required = getRequiredParams(def);
		for (String req : required) {
			if (!params.containsKey(req)) {
				params.put(req, remaining);
				break;
			}
		}

		applyParamDefaults(def, params);
		return params;
	}

	@SuppressWarnings("unchecked")
	private List<String> getRequiredParams(SkillDefinition def) {
		Map<String, Object> parameters = def.getParameters();
		if (parameters == null) {
			return List.of();
		}
		Object required = parameters.get("required");
		if (required instanceof List) {
			return (List<String>) required;
		}
		return List.of();
	}

	@SuppressWarnings("unchecked")
	private void applyParamDefaults(SkillDefinition def, Map<String, Object> params) {
		Map<String, Object> parameters = def.getParameters();
		if (parameters == null) {
			return;
		}
		Object properties = parameters.get("properties");
		if (!(properties instanceof Map)) {
			return;
		}
		Map<String, Object> props = (Map<String, Object>) properties;
		for (var entry : props.entrySet()) {
			if (!params.containsKey(entry.getKey()) && entry.getValue() instanceof Map) {
				Map<String, Object> propDef = (Map<String, Object>) entry.getValue();
				Object defaultValue = propDef.get("default");
				if (defaultValue != null) {
					params.put(entry.getKey(), defaultValue);
				}
			}
		}
	}

}
