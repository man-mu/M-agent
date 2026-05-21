package top.lanshan.manmu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchRequest;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "180s")
class ResearchControllerLlmWorkflowTest {

	@Autowired
	WebTestClient webTestClient;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void dashScopeAndDeepSeekCanDriveTheSameResearchStreamWorkflow() throws IOException {
		JsonNode keys = objectMapper.readTree(Path.of(".local", "model-providers.json").toFile());

		assertResearchWorkflow("dashscope", "qwen-turbo-2025-04-28", keys.path("dashscope").asText());
		assertResearchWorkflow("deepseek", "deepseek-chat", keys.path("deepseek").asText());
	}

	private void assertResearchWorkflow(String providerId, String modelName, String apiKey) {
		assertThat(apiKey).as("API key for %s must exist in .local/model-providers.json", providerId).isNotBlank();

		webTestClient.post()
			.uri("/api/model/providers/{providerId}/key", providerId)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("apiKey", apiKey))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.value(body -> assertThat(body).doesNotContain(apiKey));

		webTestClient.post()
			.uri("/api/model/switch")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("providerId", providerId, "modelName", modelName))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.providerId")
			.isEqualTo(providerId)
			.jsonPath("$.modelName")
			.isEqualTo(modelName);

		var events = webTestClient.mutate()
			.responseTimeout(Duration.ofSeconds(180))
			.build()
			.post()
			.uri("/api/research/stream")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(new ResearchRequest(
					"Use three sentences to explain why an agent workflow should separate Planner, Researcher, and Reporter.",
					providerId + "-workflow-test", 1))
			.exchange()
			.expectStatus()
			.isOk()
			.returnResult(ResearchEvent.class)
			.getResponseBody()
			.collectList()
			.block(Duration.ofSeconds(180));

		assertThat(events).isNotNull();
		assertThat(events).extracting(ResearchEvent::node)
			.containsExactly("planner", "planner", "researcher", "researcher", "researcher", "reporter", "reporter",
					"__END__");
		assertThat(events).anySatisfy(event -> {
			assertThat(event.node()).isEqualTo("reporter");
			assertThat(event.phase()).isEqualTo("completed");
			assertThat(String.valueOf(event.payload())).isNotBlank();
		});
		assertThat(events.get(events.size() - 1).done()).isTrue();
	}

}
