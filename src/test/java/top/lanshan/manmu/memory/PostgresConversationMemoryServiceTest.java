package top.lanshan.manmu.memory;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import top.lanshan.manmu.config.MemoryProperties;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresConversationMemoryServiceTest {

	private final ConversationMessageRepository repository = mock(ConversationMessageRepository.class);

	private final MemoryProperties properties = new MemoryProperties();

	private final PostgresConversationMemoryService service =
			new PostgresConversationMemoryService(repository, properties);

	@Test
	void saveMessageSkipsBlankContent() {
		StepVerifier.create(service.saveMessage("session", "thread", "USER", "  ")).verifyComplete();

		verify(repository, never()).save(any());
	}

	@Test
	void saveMessageSkipsWhenDisabled() {
		properties.setEnabled(false);

		StepVerifier.create(service.saveMessage("session", "thread", "USER", "hello")).verifyComplete();

		verify(repository, never()).save(any());
	}

	@Test
	void saveMessageTrimsValuesBeforePersisting() {
		when(repository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

		StepVerifier.create(service.saveMessage(" session ", " thread ", " USER ", " hello "))
			.assertNext(message -> {
				assertThat(message.sessionId()).isEqualTo("session");
				assertThat(message.threadId()).isEqualTo("thread");
				assertThat(message.role()).isEqualTo("USER");
				assertThat(message.content()).isEqualTo("hello");
			})
			.verifyComplete();

		verify(repository).save(any(ConversationMessageEntity.class));
	}

	@Test
	void formatConversationHistoryTruncatesAndOrdersMessages() {
		properties.setMaxMessages(2);
		properties.setMaxMessageCharacters(5);
		when(repository.findBySessionIdOrderByCreatedAtAsc("session"))
			.thenReturn(Flux.just(entity("session", "thread-1", "USER", "ignored older message"),
					entity("session", "thread-2", "USER", "123456789"),
					entity("session", "thread-2", "ASSISTANT", "answer")));

		StepVerifier.create(service.formatConversationHistory("session"))
			.assertNext(history -> {
				assertThat(history).startsWith("Previous conversation in this session:");
				assertThat(history).doesNotContain("ignored older message");
				assertThat(history).contains("USER: 12345...[truncated]");
				assertThat(history).contains("ASSISTANT: answe...[truncated]");
			})
			.verifyComplete();
	}

	private ConversationMessageEntity entity(String sessionId, String threadId, String role, String content) {
		ConversationMessageEntity entity = new ConversationMessageEntity();
		entity.setId(UUID.randomUUID());
		entity.setSessionId(sessionId);
		entity.setThreadId(threadId);
		entity.setRole(role);
		entity.setContent(content);
		entity.setCreatedAt(Instant.now());
		return entity;
	}

}
