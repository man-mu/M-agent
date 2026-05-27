package top.lanshan.manmu.agent.client;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SpringAiAgentClient implements AgentClient {

	private static final Logger logger = LoggerFactory.getLogger(SpringAiAgentClient.class);
	private static final Pattern SKILL_AT_PREFIX = Pattern.compile("^@(\\S+)\\s*(.*)", Pattern.DOTALL);
	private static final Pattern KEY_VALUE_PAIR = Pattern.compile("--(\\w+)=(\"[^\"]*\"|\\S+)");

	private final RoutingChatModel routingChatModel;

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

		Matcher m = SKILL_AT_PREFIX.matcher(userPrompt);
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

		logger.info("@{} explicitly invoked, template rendered ({} chars)", skillName, rendered.length());
		return rendered + "\n\n---\n\n" + systemPrompt;
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
