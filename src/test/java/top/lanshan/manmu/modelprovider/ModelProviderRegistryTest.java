package top.lanshan.manmu.modelprovider;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ModelProviderRegistryTest {

	@Autowired
	ModelProviderRegistry registry;

	@Autowired
	RoutingChatModel routingChatModel;

	@Test
	void rejectsUnknownProviderAndUnsupportedModel() {
		assertThatThrownBy(() -> registry.switchModel("missing", "deepseek-chat"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Unknown model provider");
		assertThatThrownBy(() -> registry.switchModel("deepseek", "not-supported"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Unsupported model");
	}

	@Test
	void routingChatModelUsesRealProvidersAfterSwitch() {
		registry.switchModel("dashscope", "qwen-turbo-2025-04-28");
		ChatResponse dashScopeResponse = routingChatModel.call(new Prompt("Say ok."));
		assertThat(dashScopeResponse.getResult().getOutput().getText()).isNotBlank();

		registry.switchModel("deepseek", "deepseek-chat");
		ChatResponse deepSeekResponse = routingChatModel.call(new Prompt("Say ok."));
		assertThat(deepSeekResponse.getResult().getOutput().getText()).isNotBlank();
	}

}
