package top.lanshan.manmu.modelprovider;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

public class DashScopeModelProvider implements ModelProvider {

	private final ConfiguredModelProvider config;

	public DashScopeModelProvider(ConfiguredModelProvider config) {
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
		DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(apiKey).build();
		DashScopeChatOptions options = DashScopeChatOptions.builder()
			.withModel(modelName)
			.withTemperature(0.7)
			.build();
		return DashScopeChatModel.builder().dashScopeApi(dashScopeApi).defaultOptions(options).build();
	}

}
