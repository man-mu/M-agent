package top.lanshan.manmu.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import top.lanshan.manmu.model.ChatRequest;
import top.lanshan.manmu.model.ChatStreamResponse;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchRequest;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "180s")
@EnabledIfEnvironmentVariable(named = "MANMU_RUN_REAL_MODEL_TESTS", matches = "true")
class ResearchControllerLlmWorkflowTest {

	@Autowired
	WebTestClient webTestClient;

	@Test
	void dashScopeAndDeepSeekCanDriveTheSameResearchStreamWorkflow() {
		assertResearchWorkflow("dashscope", "qwen-turbo-2025-04-28", requiredApiKey("MANMU_TEST_DASHSCOPE_API_KEY"));
		assertResearchWorkflow("deepseek", "deepseek-chat", requiredApiKey("MANMU_TEST_DEEPSEEK_API_KEY"));
	}

	@Test
	void chatStreamUsesDeepResearchCompatibleEnvelope() {
		String apiKey = requiredApiKey("MANMU_TEST_DEEPSEEK_API_KEY");

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
			.containsSubsequence("planner", "planner", "information", "research_team", "parallel_executor",
					"researcher_0", "researcher_0", "researcher_0", "research_team", "parallel_executor",
					"coder_0", "coder_0", "coder_0", "research_team", "reporter", "reporter", "__END__");
		assertThat(events.get(0).graphId().sessionId()).isEqualTo("test-session");
		assertThat(events.get(0).graphId().threadId()).isEqualTo("test-session-1");
		assertThat(events).allSatisfy(event -> {
			assertThat(event.sequence()).isNotNull();
			assertThat(event.eventType()).isNotNull();
			assertThat(event.stableNodeName()).isNotBlank();
			assertThat(event.nodeType()).isNotBlank();
			assertThat(event.phase()).isNotBlank();
			assertThat(event.status()).isNotBlank();
			assertThat(event.stableDisplayTitle()).isEqualTo(event.displayTitle());
			assertThat(event.stableGraphId()).isEqualTo(event.graphId());
			assertThat(event.timestamp()).isNotNull();
		});
		assertThat(events).anySatisfy(event -> {
			assertThat(event.nodeName()).isEqualTo("planner");
			assertThat(event.displayTitle()).isEqualTo("\u5236\u5b9a\u7814\u7a76\u8ba1\u5212");
			assertThat(event.content()).isNotNull();
		});
		assertThat(events).anySatisfy(event -> {
			assertThat(event.nodeName()).isEqualTo("coder_0");
			assertThat(event.displayTitle()).isEqualTo("\u5185\u5bb9\u6574\u7406");
			assertThat(event.nodeType()).isEqualTo("coder");
			assertThat(event.executorId()).isEqualTo(0);
		});
		assertThat(events.get(events.size() - 1).displayTitle()).isEqualTo("\u5b8c\u6210");
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
			.containsSubsequence("planner", "planner", "information", "research_team", "parallel_executor",
					"researcher_0", "researcher_0", "researcher_0", "research_team", "parallel_executor",
					"coder_0", "coder_0", "coder_0", "research_team", "reporter", "reporter", "__END__");
		assertThat(events).extracting(ResearchEvent::node).doesNotContain("researcher", "processor");
		assertThat(events).anySatisfy(event -> {
			assertThat(event.node()).isEqualTo("reporter");
			assertThat(event.phase()).isEqualTo("completed");
			assertThat(String.valueOf(event.payload())).isNotBlank();
		});
		assertThat(events.get(events.size() - 1).done()).isTrue();
	}

	private String requiredApiKey(String envName) {
		String apiKey = System.getenv(envName);
		assertThat(apiKey).as("%s must be set when MANMU_RUN_REAL_MODEL_TESTS=true", envName).isNotBlank();
		return apiKey;
	}

}
