package top.lanshan.manmu.modelprovider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;

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
	@EnabledIfEnvironmentVariable(named = "MANMU_RUN_REAL_MODEL_TESTS", matches = "true")
	void routingChatModelUsesRealProvidersAfterSwitch() {
		registry.switchModel("dashscope", "qwen-turbo-2025-04-28");
		ChatResponse dashScopeResponse = routingChatModel.call(new Prompt("Say ok."));
		assertThat(dashScopeResponse.getResult().getOutput().getText()).isNotBlank();

		registry.switchModel("deepseek", "deepseek-chat");
		ChatResponse deepSeekResponse = routingChatModel.call(new Prompt("Say ok."));
		assertThat(deepSeekResponse.getResult().getOutput().getText()).isNotBlank();
	}

	@Test
	void switchModelPersistsSelectionForRestart() throws Exception {
		ModelProviderProperties properties = new ModelProviderProperties();
		ModelProviderProperties.Provider dashscope = provider("dashscope", "Alibaba DashScope", null,
				"qwen-turbo-2025-04-28");
		ModelProviderProperties.Provider deepseek = provider("deepseek", "DeepSeek", "https://api.deepseek.com",
				"deepseek-chat", "deepseek-reasoner");
		properties.setProviders(java.util.List.of(dashscope, deepseek));

		java.nio.file.Path dir = Files.createTempDirectory("model-selection");
		ModelProviderKeyStore keyStore = new ModelProviderKeyStore(dir.resolve("keys.json"),
				new com.fasterxml.jackson.databind.ObjectMapper());
		ModelSelectionStore selectionStore = new ModelSelectionStore(dir.resolve("current-model.json"),
				new com.fasterxml.jackson.databind.ObjectMapper());
		ModelProviderRegistry first = new ModelProviderRegistry(properties, keyStore, selectionStore);

		first.switchModel("deepseek", "deepseek-chat");

		ModelProviderRegistry second = new ModelProviderRegistry(properties, keyStore, selectionStore);
		assertThat(second.current().providerId()).isEqualTo("deepseek");
		assertThat(second.current().modelName()).isEqualTo("deepseek-chat");
	}

	private ModelProviderProperties.Provider provider(String providerId, String displayName, String baseUrl,
			String... models) {
		ModelProviderProperties.Provider provider = new ModelProviderProperties.Provider();
		provider.setProviderId(providerId);
		provider.setDisplayName(displayName);
		provider.setBaseUrl(baseUrl);
		provider.setModels(java.util.List.of(models));
		return provider;
	}

}
