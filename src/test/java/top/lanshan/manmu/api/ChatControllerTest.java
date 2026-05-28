package top.lanshan.manmu.api;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.lanshan.manmu.eventhistory.ResearchEventHistoryService;
import top.lanshan.manmu.model.ApiResponse;
import top.lanshan.manmu.model.ChatStreamResponse;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchStreamEventType;
import top.lanshan.manmu.model.StepType;
import top.lanshan.manmu.runner.ResearchRunner;
import top.lanshan.manmu.runner.ResumeDecision;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatControllerTest {

	private final ResearchEventHistoryService eventHistoryService = mock(ResearchEventHistoryService.class);

	private ChatController controller(ResearchRunner runner) {
		when(eventHistoryService.save(any(), any(), any())).thenReturn(Mono.empty());
		return new ChatController(runner, eventHistoryService);
	}

	@Test
	void streamUsesPlanGateWhenAutoAcceptedPlanIsFalse() {
		ResearchRunner runner = mock(ResearchRunner.class);
		when(runner.runUntilPlanGate(any(), eq("session-a"))).thenReturn(Flux.just(ResearchEvent.message("thread-1",
				"human_feedback", "waiting", "Waiting for feedback", null).withSequence(1)));
		WebTestClient client = WebTestClient.bindToController(controller(runner)).build();

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
			assertThat(event.sequence()).isEqualTo(1);
			assertThat(event.eventType()).isEqualTo(ResearchStreamEventType.HUMAN_FEEDBACK_WAITING);
			assertThat(event.stableNodeName()).isEqualTo("human_feedback");
			assertThat(event.nodeType()).isEqualTo("human_feedback");
			assertThat(event.phase()).isEqualTo("waiting");
			assertThat(event.status()).isEqualTo("waiting");
			assertThat(event.stableDisplayTitle()).isEqualTo(event.displayTitle());
			assertThat(event.stableGraphId()).isEqualTo(event.graphId());
			assertThat(event.done()).isFalse();
			assertThat(event.timestamp()).isNotNull();
		});

		ArgumentCaptor<ResearchRequest> requestCaptor = ArgumentCaptor.forClass(ResearchRequest.class);
		verify(runner).runUntilPlanGate(requestCaptor.capture(), eq("session-a"));
		verify(runner, never()).runChat(any(), any());
		verify(eventHistoryService).save(eq("session-a"), eq("thread-1"), any(ChatStreamResponse.class));
		assertThat(requestCaptor.getValue().threadId()).isEqualTo("thread-1");
		assertThat(requestCaptor.getValue().maxSteps()).isEqualTo(2);
	}

	@Test
	void streamUsesCancellableChatRunWhenAutoAcceptedPlanIsTrue() {
		ResearchRunner runner = mock(ResearchRunner.class);
		when(runner.runChat(any(), eq("session-auto")))
			.thenReturn(Flux.just(ResearchEvent.stopped("thread-auto", "Stopped by user").withSequence(2)));
		WebTestClient client = WebTestClient.bindToController(controller(runner)).build();

		var events = client.post()
			.uri("/chat/stream")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("session_id", "session-auto", "thread_id", "thread-auto", "max_step_num", 2,
					"auto_accepted_plan", true, "query", "Explain the workflow."))
			.exchange()
			.expectStatus()
			.isOk()
			.returnResult(ChatStreamResponse.class)
			.getResponseBody()
			.collectList()
			.block(Duration.ofSeconds(5));

		assertThat(events).isNotNull();
		assertThat(events).singleElement().satisfies(event -> {
			assertThat(event.nodeName()).isEqualTo("__END__");
			assertThat(event.graphId().threadId()).isEqualTo("thread-auto");
			assertThat(event.content()).isEqualTo(Map.of("reason", "Stopped by user", "done", true));
			assertThat(event.sequence()).isEqualTo(2);
			assertThat(event.eventType()).isEqualTo(ResearchStreamEventType.GRAPH_STOPPED);
			assertThat(event.nodeType()).isEqualTo("graph");
			assertThat(event.done()).isTrue();
		});

		verify(runner).runChat(any(), eq("session-auto"));
		verify(runner, never()).run(any());
		verify(runner, never()).runUntilPlanGate(any(), any());
	}

	@Test
	void resumeForwardsFeedbackDecisionAndReturnsChatEnvelope() {
		ResearchRunner runner = mock(ResearchRunner.class);
		when(runner.resume(eq("thread-2"), any())).thenReturn(Flux.just(ResearchEvent.message("thread-2",
				"planner", "completed", "Plan regenerated", null).withSequence(7)));
		WebTestClient client = WebTestClient.bindToController(controller(runner)).build();

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
			assertThat(event.sequence()).isEqualTo(7);
			assertThat(event.eventType()).isEqualTo(ResearchStreamEventType.PLAN_GENERATED);
		});

		ArgumentCaptor<ResumeDecision> decisionCaptor = ArgumentCaptor.forClass(ResumeDecision.class);
		verify(runner).resume(eq("thread-2"), decisionCaptor.capture());
		assertThat(decisionCaptor.getValue().accepted()).isFalse();
		assertThat(decisionCaptor.getValue().feedbackContent()).isEqualTo("Focus on risks.");
	}

	@Test
	void planGateSerializesPlanStepTitlesAndDescriptions() {
		ResearchRunner runner = mock(ResearchRunner.class);
		ResearchPlan plan = new ResearchPlan("Plan", true, "Thought",
				List.of(new ResearchStep("Read docs", "Inspect record semantics.", true, StepType.RESEARCH, null,
						ResearchStep.STATUS_PENDING)));
		when(runner.runUntilPlanGate(any(), eq("session-plan"))).thenReturn(Flux.just(
				ResearchEvent.message("thread-plan", "human_feedback", "waiting", "Waiting for feedback", plan)));
		WebTestClient client = WebTestClient.bindToController(controller(runner)).build();

		var json = client.post()
			.uri("/chat/stream")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(Map.of("session_id", "session-plan", "thread_id", "thread-plan", "max_step_num", 2,
					"auto_accepted_plan", false, "query", "Explain records."))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody(String.class)
			.returnResult()
			.getResponseBody();

		assertThat(json).contains("\"event_type\":\"human_feedback.waiting\"")
			.contains("\"title\":\"Read docs\"")
			.contains("\"description\":\"Inspect record semantics.\"");
	}

	@Test
	void stopReturnsSuccessWhenRunnerStopsThread() {
		ResearchRunner runner = mock(ResearchRunner.class);
		when(runner.stopAndRecord("thread-3")).thenReturn(Mono.just(true));
		WebTestClient client = WebTestClient.bindToController(controller(runner)).build();

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
		verify(runner).stopAndRecord("thread-3");
	}

	@Test
	void stopReturnsFailureWhenThreadIsMissing() {
		ResearchRunner runner = mock(ResearchRunner.class);
		when(runner.stopAndRecord("missing-thread")).thenReturn(Mono.just(false));
		WebTestClient client = WebTestClient.bindToController(controller(runner)).build();

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
		verify(runner).stopAndRecord("missing-thread");
	}

}
