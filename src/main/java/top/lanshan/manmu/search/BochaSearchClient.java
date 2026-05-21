package top.lanshan.manmu.search;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import top.lanshan.manmu.model.SiteInformation;
import top.lanshan.manmu.modelprovider.ModelProviderKeyStore;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class BochaSearchClient implements WebSearchClient {

	private static final String LOCAL_KEY_PROVIDER_ID = "bocha";

	private final WebClient webClient;

	private final BochaSearchProperties properties;

	private final ModelProviderKeyStore keyStore;

	public BochaSearchClient(WebClient.Builder webClientBuilder, BochaSearchProperties properties,
			ModelProviderKeyStore keyStore) {
		this.webClient = webClientBuilder.build();
		this.properties = properties;
		this.keyStore = keyStore;
	}

	@Override
	public List<SiteInformation> search(String query) {
		if (!StringUtils.hasText(query)) {
			throw new IllegalArgumentException("Bocha search query must not be blank");
		}
		String apiKey = apiKey().orElseThrow(() -> new IllegalStateException(
				"Bocha API key is not configured. Set BOCHA_API_KEY or store provider id 'bocha' in .local/model-providers.json."));

		JsonNode response = webClient.post()
			.uri(properties.getEndpoint())
			.header("Authorization", "Bearer " + apiKey)
			.header("Content-Type", "application/json")
			.bodyValue(requestBody(query.strip()))
			.retrieve()
			.onStatus(HttpStatusCode::isError,
					clientResponse -> clientResponse.bodyToMono(String.class)
						.defaultIfEmpty("")
						.map(body -> new IllegalStateException(
								"Bocha web search failed with HTTP " + clientResponse.statusCode().value())))
			.bodyToMono(JsonNode.class)
			.block(Duration.ofSeconds(properties.getTimeoutSeconds()));

		return parse(response);
	}

	List<SiteInformation> parse(JsonNode response) {
		JsonNode values = response == null ? null : response.path("data").path("webPages").path("value");
		if (values == null || !values.isArray()) {
			return List.of();
		}
		return java.util.stream.StreamSupport.stream(values.spliterator(), false)
			.map(this::toSiteInformation)
			.toList();
	}

	private SiteInformation toSiteInformation(JsonNode node) {
		return new SiteInformation(text(node, "name"), text(node, "url"), text(node, "snippet"), text(node, "summary"),
				text(node, "siteName"), text(node, "siteIcon"), firstText(node, "datePublished", "dateLastCrawled"));
	}

	Map<String, Object> requestBody(String query) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("query", query);
		body.put("freshness", properties.getFreshness());
		body.put("summary", properties.isSummary());
		body.put("count", properties.getCount());
		return body;
	}

	private Optional<String> apiKey() {
		return Optional.ofNullable(properties.getApiKey())
			.filter(StringUtils::hasText)
			.or(() -> Optional.ofNullable(System.getenv("BOCHA_API_KEY")).filter(StringUtils::hasText))
			.or(() -> keyStore.getApiKey(LOCAL_KEY_PROVIDER_ID).filter(StringUtils::hasText))
			.map(String::strip);
	}

	private String text(JsonNode node, String fieldName) {
		JsonNode value = node.path(fieldName);
		return value.isMissingNode() || value.isNull() ? "" : value.asText("");
	}

	private String firstText(JsonNode node, String... fieldNames) {
		for (String fieldName : fieldNames) {
			String value = text(node, fieldName);
			if (!value.isBlank()) {
				return value;
			}
		}
		return "";
	}

}
