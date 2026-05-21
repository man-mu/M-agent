package top.lanshan.manmu.modelprovider;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.List;

public class OpenAiCompatibleModelProvider implements ModelProvider {

	private final ConfiguredModelProvider config;

	public OpenAiCompatibleModelProvider(ConfiguredModelProvider config) {
		this.config = config;
	}

	@Override
	public String providerId() {
		return config.providerId();
	}

	@Override
	public String displayName() {
		return config.displayName();
	}

	@Override
	public List<String> supportedModels() {
		return config.models();
	}

	@Override
	public ChatModel createChatModel(String modelName, String apiKey) {
		OpenAiApi openAiApi = OpenAiApi.builder().baseUrl(config.baseUrl()).apiKey(apiKey).build();
		OpenAiChatOptions options = OpenAiChatOptions.builder().model(modelName).temperature(0.7).build();
		return OpenAiChatModel.builder().openAiApi(openAiApi).defaultOptions(options).build();
	}

}
