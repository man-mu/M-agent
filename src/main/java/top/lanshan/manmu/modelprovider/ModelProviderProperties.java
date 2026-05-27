package top.lanshan.manmu.modelprovider;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "mvp.model")
public class ModelProviderProperties {

	private Current current = new Current();

	private Duration connectTimeout = Duration.ofSeconds(20);

	private Duration readTimeout = Duration.ofSeconds(180);

	private List<Provider> providers = new ArrayList<>();

	public Current getCurrent() {
		return current;
	}

	public void setCurrent(Current current) {
		this.current = current;
	}

	public Duration getConnectTimeout() {
		return connectTimeout;
	}

	public void setConnectTimeout(Duration connectTimeout) {
		this.connectTimeout = positiveOrDefault(connectTimeout, Duration.ofSeconds(20));
	}

	public Duration getReadTimeout() {
		return readTimeout;
	}

	public void setReadTimeout(Duration readTimeout) {
		this.readTimeout = positiveOrDefault(readTimeout, Duration.ofSeconds(180));
	}

	public List<Provider> getProviders() {
		return providers;
	}

	public void setProviders(List<Provider> providers) {
		this.providers = providers;
	}

	private Duration positiveOrDefault(Duration value, Duration defaultValue) {
		return value == null || value.isZero() || value.isNegative() ? defaultValue : value;
	}

	public static class Current {

		private String providerId = "deepseek";

		private String modelName = "deepseek-chat";

		public String getProviderId() {
			return providerId;
		}

		public void setProviderId(String providerId) {
			this.providerId = providerId;
		}

		public String getModelName() {
			return modelName;
		}

		public void setModelName(String modelName) {
			this.modelName = modelName;
		}

	}

	public static class Provider {

		private String providerId;

		private String displayName;

		private String baseUrl;

		private List<String> models = new ArrayList<>();

		public String getProviderId() {
			return providerId;
		}

		public void setProviderId(String providerId) {
			this.providerId = providerId;
		}

		public String getDisplayName() {
			return displayName;
		}

		public void setDisplayName(String displayName) {
			this.displayName = displayName;
		}

		public String getBaseUrl() {
			return baseUrl;
		}

		public void setBaseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		public List<String> getModels() {
			return models;
		}

		public void setModels(List<String> models) {
			this.models = models;
		}

	}

}
