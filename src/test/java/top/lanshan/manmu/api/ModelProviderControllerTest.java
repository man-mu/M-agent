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
		String dashScopeKey = keys.path("dashscope").asText();
		String deepSeekKey = keys.path("deepseek").asText();

		webTestClient.post()
			.uri("/api/model/providers/dashscope/key")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("apiKey", dashScopeKey))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.apiKeyConfigured")
			.isEqualTo(true)
			.jsonPath("$.apiKey")
			.doesNotExist();

		webTestClient.post()
			.uri("/api/model/providers/deepseek/key")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("apiKey", deepSeekKey))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.apiKeyConfigured")
			.isEqualTo(true)
			.jsonPath("$.apiKey")
			.doesNotExist();

		webTestClient.get()
			.uri("/api/model/providers")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.value(body -> assertThat(body).doesNotContain(dashScopeKey, deepSeekKey));

		webTestClient.get()
			.uri("/api/model/current")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.value(body -> assertThat(body).doesNotContain(dashScopeKey, deepSeekKey));
	}

}
