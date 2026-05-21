package top.lanshan.manmu.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import top.lanshan.manmu.model.ChatRequest;
import top.lanshan.manmu.model.ChatStreamResponse;
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

	@Test
	void chatStreamUsesDeepResearchCompatibleEnvelope() throws IOException {
		JsonNode keys = objectMapper.readTree(Path.of(".local", "model-providers.json").toFile());
		String apiKey = keys.path("deepseek").asText();
		assertThat(apiKey).as("API key for deepseek must exist in .local/model-providers.json").isNotBlank();

		webTestClient.post()
			.uri("/api/model/providers/deepseek/key")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("apiKey", apiKey))
			.exchange()
			.expectStatus()
			.isOk();

		webTestClient.post()
			.uri("/api/model/switch")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("providerId", "deepseek", "modelName", "deepseek-chat"))
			.exchange()
			.expectStatus()
			.isOk();

		var events = webTestClient.mutate()
			.responseTimeout(Duration.ofSeconds(180))
			.build()
			.post()
			.uri("/chat/stream")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(new ChatRequest("test-session", "", 2, true, true,
					"Use one sentence to explain the value of a DeepResearch workflow."))
			.exchange()
			.expectStatus()
			.isOk()
			.returnResult(ChatStreamResponse.class)
			.getResponseBody()
			.collectList()
			.block(Duration.ofSeconds(180));

		assertThat(events).isNotNull();
		assertThat(events).extracting(ChatStreamResponse::nodeName)
			.containsSubsequence("planner", "planner", "information", "research_team", "researcher",
					"researcher", "researcher", "research_team", "processor", "processor", "processor",
					"research_team", "reporter", "reporter", "__END__");
		assertThat(events.get(0).graphId().sessionId()).isEqualTo("test-session");
		assertThat(events.get(0).graphId().threadId()).isEqualTo("test-session-1");
		assertThat(events).anySatisfy(event -> {
			assertThat(event.nodeName()).isEqualTo("planner");
			assertThat(event.displayTitle()).isEqualTo("研究计划");
			assertThat(event.content()).isNotNull();
		});
		assertThat(events).anySatisfy(event -> {
			assertThat(event.nodeName()).isEqualTo("processor");
			assertThat(event.displayTitle()).isEqualTo("信息整理");
		});
		assertThat(events.get(events.size() - 1).displayTitle()).isEqualTo("结束");
		assertThat(String.valueOf(events.get(events.size() - 1).content())).contains("done=true");
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
					providerId + "-workflow-test", 2))
			.exchange()
			.expectStatus()
			.isOk()
			.returnResult(ResearchEvent.class)
			.getResponseBody()
			.collectList()
			.block(Duration.ofSeconds(180));

		assertThat(events).isNotNull();
		assertThat(events).extracting(ResearchEvent::node)
			.containsSubsequence("planner", "planner", "information", "research_team", "researcher",
					"researcher", "researcher", "research_team", "processor", "processor", "processor",
					"research_team", "reporter", "reporter", "__END__");
		assertThat(events).anySatisfy(event -> {
			assertThat(event.node()).isEqualTo("reporter");
			assertThat(event.phase()).isEqualTo("completed");
			assertThat(String.valueOf(event.payload())).isNotBlank();
		});
		assertThat(events.get(events.size() - 1).done()).isTrue();
	}

}
