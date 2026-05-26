package top.lanshan.manmu.agent.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.lanshan.manmu.mcp.McpToolProvider;
import top.lanshan.manmu.modelprovider.RoutingChatModel;

@Component
public class SpringAiAgentClient implements AgentClient {

	private static final Logger logger = LoggerFactory.getLogger(SpringAiAgentClient.class);

	private final RoutingChatModel routingChatModel;

	@Autowired(required = false)
	private McpToolProvider mcpToolProvider;

	public SpringAiAgentClient(RoutingChatModel routingChatModel) {
		this.routingChatModel = routingChatModel;
	}

	@Override
	public String call(String systemPrompt, String userPrompt) {
		ChatClient chatClient = ChatClient.builder(routingChatModel).build();
		ChatClient.ChatClientRequestSpec request = chatClient.prompt()
				.system(systemPrompt)
				.user(userPrompt);

		if (mcpToolProvider != null) {
			ToolCallback[] mcpCallbacks = mcpToolProvider.getToolCallbacks();
			if (mcpCallbacks.length > 0) {
				request = request.toolCallbacks(mcpCallbacks);
				logger.debug("MCP tools attached: {} tool(s)", mcpCallbacks.length);
			}
		}

		return request.call().content();
	}

}
