package top.lanshan.manmu.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mvp.search.bocha")
public class BochaSearchProperties {

	private String endpoint = "https://api.bochaai.com/v1/web-search";

	private String apiKey = "";

	private String freshness = "noLimit";

	private boolean summary = true;

	private int count = 5;

	private int timeoutSeconds = 30;

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getFreshness() {
		return freshness;
	}

	public void setFreshness(String freshness) {
		this.freshness = freshness;
	}

	public boolean isSummary() {
		return summary;
	}

	public void setSummary(boolean summary) {
		this.summary = summary;
	}

	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = Math.max(1, Math.min(50, count));
	}

	public int getTimeoutSeconds() {
		return timeoutSeconds;
	}

	public void setTimeoutSeconds(int timeoutSeconds) {
		this.timeoutSeconds = Math.max(1, timeoutSeconds);
	}

}
