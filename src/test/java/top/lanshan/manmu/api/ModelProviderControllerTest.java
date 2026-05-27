package top.lanshan.manmu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ModelProviderControllerTest {

	@Autowired
	WebTestClient webTestClient;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void realProviderResponsesDoNotEchoPlainApiKeys() throws IOException {
		JsonNode keys = objectMapper.readTree(Path.of(".local", "model-providers.json").toFile());
		Map<String, String> configuredKeys = new LinkedHashMap<>();
		addConfiguredKey(configuredKeys, "dashscope", keys.path("dashscope").asText());
		addConfiguredKey(configuredKeys, "deepseek", keys.path("deepseek").asText());

		assertThat(configuredKeys).isNotEmpty();

		configuredKeys.forEach((providerId, apiKey) -> webTestClient.post()
			.uri("/api/model/providers/{providerId}/key", providerId)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("apiKey", apiKey))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.apiKeyConfigured")
			.isEqualTo(true)
			.jsonPath("$.apiKey")
			.doesNotExist());

		webTestClient.get()
			.uri("/api/model/providers")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.value(body -> assertThat(body).doesNotContain(configuredKeys.values()));

		webTestClient.get()
			.uri("/api/model/current")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.value(body -> assertThat(body).doesNotContain(configuredKeys.values()));
	}

	private void addConfiguredKey(Map<String, String> keys, String providerId, String apiKey) {
		if (apiKey != null && !apiKey.isBlank()) {
			keys.put(providerId, apiKey);
		}
	}

}
