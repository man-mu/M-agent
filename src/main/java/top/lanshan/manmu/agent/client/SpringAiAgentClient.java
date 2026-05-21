package top.lanshan.manmu.agent.client;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import top.lanshan.manmu.modelprovider.RoutingChatModel;

@Component
public class SpringAiAgentClient implements AgentClient {

	private final RoutingChatModel routingChatModel;

	public SpringAiAgentClient(RoutingChatModel routingChatModel) {
		this.routingChatModel = routingChatModel;
	}

	@Override
	public String call(String systemPrompt, String userPrompt) {
		ChatClient chatClient = ChatClient.builder(routingChatModel).build();
		return chatClient.prompt().system(systemPrompt).user(userPrompt).call().content();
	}

}
