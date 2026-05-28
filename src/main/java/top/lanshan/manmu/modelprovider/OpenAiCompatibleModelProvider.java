package top.lanshan.manmu.modelprovider;

import io.netty.channel.ChannelOption;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;

public class OpenAiCompatibleModelProvider implements ModelProvider {

	private final ConfiguredModelProvider config;

	private final Duration connectTimeout;

	private final Duration readTimeout;

	public OpenAiCompatibleModelProvider(ConfiguredModelProvider config, Duration connectTimeout, Duration readTimeout) {
		this.config = config;
		this.connectTimeout = connectTimeout;
		this.readTimeout = readTimeout;
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
		OpenAiApi openAiApi = OpenAiApi.builder()
			.baseUrl(config.baseUrl())
			.apiKey(apiKey)
			.restClientBuilder(restClientBuilder())
			.webClientBuilder(webClientBuilder())
			.build();
		OpenAiChatOptions options = OpenAiChatOptions.builder().model(modelName).build();
		return OpenAiChatModel.builder().openAiApi(openAiApi).defaultOptions(options).build();
	}

	private RestClient.Builder restClientBuilder() {
		java.net.http.HttpClient httpClient =
				java.net.http.HttpClient.newBuilder().connectTimeout(connectTimeout).build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(readTimeout);
		return RestClient.builder().requestFactory(requestFactory);
	}

	private WebClient.Builder webClientBuilder() {
		HttpClient httpClient = HttpClient.create()
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(connectTimeout.toMillis()))
			.responseTimeout(readTimeout);
		return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient));
	}

}
