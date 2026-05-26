package top.lanshan.manmu.agent.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.lanshan.manmu.mcp.McpToolProvider;
import top.lanshan.manmu.modelprovider.RoutingChatModel;
import top.lanshan.manmu.skill.service.SkillToolProvider;

import java.util.ArrayList;
import java.util.List;

@Component
public class SpringAiAgentClient implements AgentClient {

	private static final Logger logger = LoggerFactory.getLogger(SpringAiAgentClient.class);

	private final RoutingChatModel routingChatModel;

	@Autowired(required = false)
	private McpToolProvider mcpToolProvider;

	@Autowired(required = false)
	private SkillToolProvider skillToolProvider;

	public SpringAiAgentClient(RoutingChatModel routingChatModel) {
		this.routingChatModel = routingChatModel;
	}

	@Override
	public String call(String systemPrompt, String userPrompt) {
		ChatClient chatClient = ChatClient.builder(routingChatModel).build();
		ChatClient.ChatClientRequestSpec request = chatClient.prompt()
				.system(systemPrompt)
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

		return request.call().content();
	}

}
