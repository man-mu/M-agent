package top.lanshan.manmu.modelprovider;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ModelProviderRegistry {

	private final Map<String, ModelProvider> providers;

	private final ModelProviderKeyStore keyStore;

	private final AtomicReference<SelectedModel> current;

	@Autowired
	public ModelProviderRegistry(ModelProviderProperties properties, ModelProviderKeyStore keyStore) {
		this.keyStore = keyStore;
		this.providers = buildProviders(properties);
		this.current = new AtomicReference<>(validateSelection(
				new SelectedModel(properties.getCurrent().getProviderId(), properties.getCurrent().getModelName())));
	}

	public List<ProviderSummary> providers() {
		return providers.values()
			.stream()
			.map(provider -> new ProviderSummary(provider.providerId(), provider.displayName(),
					provider.supportedModels(), hasConfiguredApiKey(provider.providerId())))
			.toList();
	}

	public CurrentModelSelection current() {
		SelectedModel selectedModel = current.get();
		ModelProvider provider = providerOrThrow(selectedModel.providerId());
		return new CurrentModelSelection(provider.providerId(), provider.displayName(), selectedModel.modelName(),
				hasConfiguredApiKey(provider.providerId()));
	}

	public void saveApiKey(String providerId, String apiKey) {
		providerOrThrow(providerId);
		if (!StringUtils.hasText(apiKey)) {
			throw new IllegalArgumentException("apiKey must not be blank");
		}
		keyStore.saveApiKey(providerId, apiKey.strip());
	}

	public CurrentModelSelection switchModel(String providerId, String modelName) {
		SelectedModel selectedModel = validateSelection(new SelectedModel(providerId, modelName));
		current.set(selectedModel);
		return current();
	}

	public ChatModel getChatModel() {
		SelectedModel selectedModel = current.get();
		ModelProvider provider = providerOrThrow(selectedModel.providerId());
		String apiKey = configuredApiKey(provider.providerId())
			.orElseThrow(() -> new IllegalStateException("API key is not configured for " + provider.providerId()));
		return provider.createChatModel(selectedModel.modelName(), apiKey);
	}

	public ChatClient.Builder getChatClientBuilder() {
		return ChatClient.builder(getChatModel());
	}

	public void testCurrentModel(String prompt) {
		getChatClientBuilder().build().prompt(prompt).call().content();
	}

	private Map<String, ModelProvider> buildProviders(ModelProviderProperties properties) {
		Map<String, ModelProvider> result = new LinkedHashMap<>();
		for (ModelProviderProperties.Provider providerProperties : properties.getProviders()) {
			ConfiguredModelProvider config = new ConfiguredModelProvider(providerProperties.getProviderId(),
					providerProperties.getDisplayName(), providerProperties.getBaseUrl(), providerProperties.getModels());
			ModelProvider provider = "dashscope".equals(config.providerId()) ? new DashScopeModelProvider(config)
					: new OpenAiCompatibleModelProvider(config);
			result.put(provider.providerId(), provider);
		}
		if (result.isEmpty()) {
			throw new IllegalStateException("At least one model provider must be configured");
		}
		return Collections.unmodifiableMap(result);
	}

	private SelectedModel validateSelection(SelectedModel selectedModel) {
		ModelProvider provider = providerOrThrow(selectedModel.providerId());
		if (!provider.supportedModels().contains(selectedModel.modelName())) {
			throw new IllegalArgumentException("Unsupported model '%s' for provider '%s'. Supported models: %s"
				.formatted(selectedModel.modelName(), selectedModel.providerId(), new ArrayList<>(provider.supportedModels())));
		}
		return selectedModel;
	}

	private ModelProvider providerOrThrow(String providerId) {
		ModelProvider provider = providers.get(providerId);
		if (provider == null) {
			throw new IllegalArgumentException("Unknown model provider: " + providerId);
		}
		return provider;
	}

	private boolean hasConfiguredApiKey(String providerId) {
		return configuredApiKey(providerId).isPresent();
	}

	private Optional<String> configuredApiKey(String providerId) {
		return keyStore.getApiKey(providerId)
			.filter(StringUtils::hasText)
			.or(() -> dashScopeEnvironmentApiKey(providerId));
	}

	private Optional<String> dashScopeEnvironmentApiKey(String providerId) {
		if (!"dashscope".equals(providerId)) {
			return Optional.empty();
		}
		return Optional.ofNullable(System.getenv("AI_DASHSCOPE_API_KEY")).filter(StringUtils::hasText);
	}

	private record SelectedModel(String providerId, String modelName) {
	}

}
