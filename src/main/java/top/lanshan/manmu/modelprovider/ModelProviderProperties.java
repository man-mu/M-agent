package top.lanshan.manmu.modelprovider;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "mvp.model")
public class ModelProviderProperties {

	private Current current = new Current();

	private List<Provider> providers = new ArrayList<>();

	public Current getCurrent() {
		return current;
	}

	public void setCurrent(Current current) {
		this.current = current;
	}

	public List<Provider> getProviders() {
		return providers;
	}

	public void setProviders(List<Provider> providers) {
		this.providers = providers;
	}

	public static class Current {

		private String providerId = "dashscope";

		private String modelName = "qwen-turbo-2025-04-28";

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
