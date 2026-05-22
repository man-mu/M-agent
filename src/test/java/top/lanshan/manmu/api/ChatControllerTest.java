package top.lanshan.manmu.api;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import top.lanshan.manmu.model.ApiResponse;
import top.lanshan.manmu.model.ChatStreamResponse;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.runner.ResumeDecision;
import top.lanshan.manmu.runner.SimpleResearchRunner;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatControllerTest {

	@Test
	void streamUsesPlanGateWhenAutoAcceptedPlanIsFalse() {
		SimpleResearchRunner runner = mock(SimpleResearchRunner.class);
		when(runner.runUntilPlanGate(any())).thenReturn(Flux.just(ResearchEvent.message("thread-1",
				"human_feedback", "waiting", "Waiting for feedback", null)));
		WebTestClient client = WebTestClient.bindToController(new ChatController(runner)).build();

		var events = client.post()
			.uri("/chat/stream")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("session_id", "session-a", "thread_id", "thread-1", "max_step_num", 2,
					"auto_accepted_plan", false, "query", "Explain the workflow."))
			.exchange()
			.expectStatus()
			.isOk()
			.returnResult(ChatStreamResponse.class)
			.getResponseBody()
			.collectList()
			.block(Duration.ofSeconds(5));

		assertThat(events).isNotNull();
		assertThat(events).singleElement().satisfies(event -> {
			assertThat(event.nodeName()).isEqualTo("human_feedback");
			assertThat(event.graphId().sessionId()).isEqualTo("session-a");
			assertThat(event.graphId().threadId()).isEqualTo("thread-1");
		});

		ArgumentCaptor<ResearchRequest> requestCaptor = ArgumentCaptor.forClass(ResearchRequest.class);
		verify(runner).runUntilPlanGate(requestCaptor.capture());
		verify(runner, never()).run(any());
		assertThat(requestCaptor.getValue().threadId()).isEqualTo("thread-1");
		assertThat(requestCaptor.getValue().maxSteps()).isEqualTo(2);
	}

	@Test
	void resumeForwardsFeedbackDecisionAndReturnsChatEnvelope() {
		SimpleResearchRunner runner = mock(SimpleResearchRunner.class);
		when(runner.resume(eq("thread-2"), any())).thenReturn(Flux.just(ResearchEvent.message("thread-2",
				"planner", "completed", "Plan regenerated", null)));
		WebTestClient client = WebTestClient.bindToController(new ChatController(runner)).build();

		var events = client.post()
			.uri("/chat/resume")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("session_id", "session-b", "thread_id", "thread-2", "feedback", false,
					"feedback_content", "Focus on risks."))
			.exchange()
			.expectStatus()
			.isOk()
			.returnResult(ChatStreamResponse.class)
			.getResponseBody()
			.collectList()
			.block(Duration.ofSeconds(5));

		assertThat(events).isNotNull();
		assertThat(events).singleElement().satisfies(event -> {
			assertThat(event.nodeName()).isEqualTo("planner");
			assertThat(event.graphId().sessionId()).isEqualTo("session-b");
			assertThat(event.graphId().threadId()).isEqualTo("thread-2");
		});

		ArgumentCaptor<ResumeDecision> decisionCaptor = ArgumentCaptor.forClass(ResumeDecision.class);
		verify(runner).resume(eq("thread-2"), decisionCaptor.capture());
		assertThat(decisionCaptor.getValue().accepted()).isFalse();
		assertThat(decisionCaptor.getValue().feedbackContent()).isEqualTo("Focus on risks.");
	}

	@Test
	void stopReturnsSuccessWhenRunnerStopsThread() {
		SimpleResearchRunner runner = mock(SimpleResearchRunner.class);
		when(runner.stop("thread-3")).thenReturn(true);
		WebTestClient client = WebTestClient.bindToController(new ChatController(runner)).build();

		var response = client.post()
			.uri("/chat/stop")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("session_id", "session-c", "thread_id", "thread-3"))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(ApiResponse.class)
			.returnResult()
			.getResponseBody();

		assertThat(response).isNotNull();
		assertThat(response.code()).isEqualTo(200);
		assertThat(response.status()).isEqualTo("success");
		assertThat(response.data()).isEqualTo("thread-3");
		verify(runner).stop("thread-3");
	}

	@Test
	void stopReturnsFailureWhenThreadIsMissing() {
		SimpleResearchRunner runner = mock(SimpleResearchRunner.class);
		when(runner.stop("missing-thread")).thenReturn(false);
		WebTestClient client = WebTestClient.bindToController(new ChatController(runner)).build();

		var response = client.post()
			.uri("/chat/stop")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("session_id", "session-c", "thread_id", "missing-thread"))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(ApiResponse.class)
			.returnResult()
			.getResponseBody();

		assertThat(response).isNotNull();
		assertThat(response.code()).isEqualTo(500);
		assertThat(response.status()).isEqualTo("error");
		assertThat(response.message()).isEqualTo("Failure");
		assertThat(response.data()).isEqualTo("missing-thread");
		verify(runner).stop("missing-thread");
	}

}
