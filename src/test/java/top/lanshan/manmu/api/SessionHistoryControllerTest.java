package top.lanshan.manmu.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.lanshan.manmu.eventhistory.ResearchEventHistoryService;
import top.lanshan.manmu.eventhistory.ResearchEventRecord;
import top.lanshan.manmu.model.ChatStreamResponse;
import top.lanshan.manmu.model.GraphId;
import top.lanshan.manmu.model.ResearchStreamEventType;
import top.lanshan.manmu.sessionhistory.ResearchSessionHistory;
import top.lanshan.manmu.sessionhistory.SessionHistoryService;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionHistoryControllerTest {

	private final ResearchEventHistoryService eventHistoryService = mock(ResearchEventHistoryService.class);

	private SessionHistoryController controller(SessionHistoryService sessionHistoryService) {
		return new SessionHistoryController(sessionHistoryService, eventHistoryService);
	}

	@Test
	void sessionHistoryReturnsDeepResearchCompatibleThreadList() {
		Instant now = Instant.parse("2026-05-22T00:00:00Z");
		SessionHistoryService sessionHistoryService = mock(SessionHistoryService.class);
		when(sessionHistoryService.findBySessionId("session-1")).thenReturn(Flux.just(new ResearchSessionHistory(
				"thread-1", "session-1", "query", "COMPLETED", "thread-1", null, now, now, now, null)));
		WebTestClient client = WebTestClient.bindToController(controller(sessionHistoryService)).build();

		client.get()
			.uri("/api/sessions/session-1/history")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
			.expectBody()
			.jsonPath("$.session_id")
			.isEqualTo("session-1")
			.jsonPath("$.status")
			.isEqualTo("success")
			.jsonPath("$.session_history[0].thread_id")
			.isEqualTo("thread-1")
			.jsonPath("$.session_history[0].status")
			.isEqualTo("COMPLETED")
			.jsonPath("$.session_history[0].report_thread_id")
			.isEqualTo("thread-1");

		verify(sessionHistoryService).findBySessionId("session-1");
	}

	@Test
	void threadHistoryReturnsSingleThread() {
		Instant now = Instant.parse("2026-05-22T00:00:00Z");
		SessionHistoryService sessionHistoryService = mock(SessionHistoryService.class);
		when(sessionHistoryService.findBySessionIdAndThreadId("session-1", "thread-1"))
			.thenReturn(Mono.just(new ResearchSessionHistory("thread-1", "session-1", "query", "PAUSED", null, null,
					now, now, null, null)));
		WebTestClient client = WebTestClient.bindToController(controller(sessionHistoryService)).build();

		client.get()
			.uri("/api/sessions/session-1/threads/thread-1")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.thread_id")
			.isEqualTo("thread-1")
			.jsonPath("$.session_history.status")
			.isEqualTo("PAUSED");

		verify(sessionHistoryService).findBySessionIdAndThreadId("session-1", "thread-1");
	}

	@Test
	void threadHistoryReturnsNotFoundWhenMissing() {
		SessionHistoryService sessionHistoryService = mock(SessionHistoryService.class);
		when(sessionHistoryService.findBySessionIdAndThreadId("session-1", "missing-thread")).thenReturn(Mono.empty());
		WebTestClient client = WebTestClient.bindToController(controller(sessionHistoryService)).build();

		client.get()
			.uri("/api/sessions/session-1/threads/missing-thread")
			.exchange()
			.expectStatus()
			.isNotFound()
			.expectBody()
			.jsonPath("$.status")
			.isEqualTo("notfound");
	}

	@Test
	void recentHistoryPassesCountToService() {
		SessionHistoryService sessionHistoryService = mock(SessionHistoryService.class);
		when(sessionHistoryService.findRecentBySessionId("session-1", 2)).thenReturn(Flux.empty());
		WebTestClient client = WebTestClient.bindToController(controller(sessionHistoryService)).build();

		client.get().uri("/api/sessions/session-1/recent?count=2").exchange().expectStatus().isOk();

		verify(sessionHistoryService).findRecentBySessionId("session-1", 2);
	}

	@Test
	void threadEventsReturnsPersistedChatEvents() {
		Instant now = Instant.parse("2026-05-22T00:00:00Z");
		SessionHistoryService sessionHistoryService = mock(SessionHistoryService.class);
		when(sessionHistoryService.findBySessionIdAndThreadId("session-1", "thread-1"))
			.thenReturn(Mono.just(new ResearchSessionHistory("thread-1", "session-1", "query", "COMPLETED",
					"thread-1", null, now, now, now, null)));
		ChatStreamResponse event = new ChatStreamResponse("planner", new GraphId("session-1", "thread-1"),
				"制定研究计划", "content", List.of(), 2L, ResearchStreamEventType.PLAN_GENERATED, "planner", "planner",
				null, null, "completed", "completed", "制定研究计划", "payload", List.of(), false, now,
				new GraphId("session-1", "thread-1"));
		when(eventHistoryService.findBySessionIdAndThreadId("session-1", "thread-1"))
			.thenReturn(Flux.just(new ResearchEventRecord("session-1", "thread-1", 2L, "plan.generated", "planner",
					"planner", null, null, "completed", "completed", "制定研究计划", false, now, now, event)));
		WebTestClient client = WebTestClient.bindToController(controller(sessionHistoryService)).build();

		client.get()
			.uri("/api/sessions/session-1/threads/thread-1/events")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.thread_id")
			.isEqualTo("thread-1")
			.jsonPath("$.session_history[0].sequence")
			.isEqualTo(2)
			.jsonPath("$.session_history[0].event_type")
			.isEqualTo("plan.generated")
			.jsonPath("$.session_history[0].node_name")
			.isEqualTo("planner");

		verify(sessionHistoryService).findBySessionIdAndThreadId("session-1", "thread-1");
		verify(eventHistoryService).findBySessionIdAndThreadId("session-1", "thread-1");
	}

}
