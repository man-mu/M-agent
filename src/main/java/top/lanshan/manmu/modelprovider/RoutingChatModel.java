package top.lanshan.manmu.modelprovider;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class RoutingChatModel implements ChatModel {

	private final ModelProviderRegistry registry;

	public RoutingChatModel(ModelProviderRegistry registry) {
		this.registry = registry;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		return registry.getChatModel().call(prompt);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return registry.getChatModel().stream(prompt);
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return registry.getChatModel().getDefaultOptions();
	}

}
