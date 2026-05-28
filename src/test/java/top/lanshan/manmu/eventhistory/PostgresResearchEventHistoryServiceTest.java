package top.lanshan.manmu.eventhistory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import top.lanshan.manmu.model.ChatStreamResponse;
import top.lanshan.manmu.model.GraphId;
import top.lanshan.manmu.model.ResearchStreamEventType;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresResearchEventHistoryServiceTest {

	private final ResearchEventHistoryRepository repository = mock(ResearchEventHistoryRepository.class);

	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	private final PostgresResearchEventHistoryService service =
			new PostgresResearchEventHistoryService(repository, objectMapper);

	@BeforeEach
	void resetRepository() {
		org.mockito.Mockito.reset(repository);
	}

	@Test
	void savePersistsSerializableChatEvent() {
		when(repository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
		ChatStreamResponse event = event(3L, ResearchStreamEventType.PLAN_GENERATED);

		StepVerifier.create(service.save(" session ", " thread ", event))
			.assertNext(record -> {
				assertThat(record.sessionId()).isEqualTo("session");
				assertThat(record.threadId()).isEqualTo("thread");
				assertThat(record.sequence()).isEqualTo(3L);
				assertThat(record.eventType()).isEqualTo("plan.generated");
				assertThat(record.event().eventType()).isEqualTo(ResearchStreamEventType.PLAN_GENERATED);
				assertThat(record.event().sequence()).isEqualTo(3L);
			})
			.verifyComplete();

		verify(repository).save(any(ResearchEventHistoryEntity.class));
	}

	@Test
	void saveSkipsEventsWithoutSequence() {
		StepVerifier.create(service.save("session", "thread", event(null, ResearchStreamEventType.NODE_DELTA)))
			.verifyComplete();

		verify(repository, never()).save(any());
	}

	@Test
	void findByThreadReturnsStoredEventsInRepositoryOrder() throws Exception {
		ResearchEventHistoryEntity entity = entity("session", "thread", event(1L, ResearchStreamEventType.NODE_DELTA));
		when(repository.findByThreadIdOrderBySequenceAsc("thread")).thenReturn(Flux.just(entity));

		StepVerifier.create(service.findByThreadId(" thread "))
			.assertNext(record -> {
				assertThat(record.threadId()).isEqualTo("thread");
				assertThat(record.event().nodeName()).isEqualTo("planner");
			})
			.verifyComplete();
	}

	private ChatStreamResponse event(Long sequence, ResearchStreamEventType eventType) {
		Instant now = Instant.parse("2026-05-28T00:00:00Z");
		return new ChatStreamResponse("planner", new GraphId("session", "thread"), "制定研究计划", "content",
				List.of(), sequence, eventType, "planner", "planner", null, null, "completed", "completed",
				"制定研究计划", "payload", List.of(), false, now, new GraphId("session", "thread"));
	}

	private ResearchEventHistoryEntity entity(String sessionId, String threadId, ChatStreamResponse event)
			throws Exception {
		ResearchEventHistoryEntity entity = new ResearchEventHistoryEntity();
		entity.setSessionId(sessionId);
		entity.setThreadId(threadId);
		entity.setSequence(event.sequence());
		entity.setEventType(event.eventType().value());
		entity.setNodeName(event.stableNodeName());
		entity.setNodeType(event.nodeType());
		entity.setPhase(event.phase());
		entity.setStatus(event.status());
		entity.setDisplayTitle(event.stableDisplayTitle());
		entity.setDone(event.done());
		entity.setEventTimestamp(event.timestamp());
		entity.setEventJson(objectMapper.writeValueAsString(event));
		entity.setCreatedAt(event.timestamp());
		return entity;
	}

}
