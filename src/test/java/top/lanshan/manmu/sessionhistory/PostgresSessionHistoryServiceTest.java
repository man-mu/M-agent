package top.lanshan.manmu.sessionhistory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PostgresSessionHistoryServiceTest {

	@Autowired
	SessionHistoryService sessionHistoryService;

	@Autowired
	ResearchSessionHistoryRepository repository;

	@BeforeEach
	void cleanHistories() {
		repository.deleteAll().block();
	}

	@Test
	void createsAndCompletesSessionHistory() {
		sessionHistoryService.start("thread-1", "session-1", "What is DeepResearch?").block();
		sessionHistoryService.markCompleted("thread-1", "thread-1").block();

		ResearchSessionHistory saved = sessionHistoryService.findBySessionIdAndThreadId("session-1", "thread-1")
			.block();

		assertThat(saved).isNotNull();
		assertThat(saved.threadId()).isEqualTo("thread-1");
		assertThat(saved.sessionId()).isEqualTo("session-1");
		assertThat(saved.query()).isEqualTo("What is DeepResearch?");
		assertThat(saved.status()).isEqualTo("COMPLETED");
		assertThat(saved.reportThreadId()).isEqualTo("thread-1");
		assertThat(saved.createdAt()).isNotNull();
		assertThat(saved.updatedAt()).isNotNull();
		assertThat(saved.completedAt()).isNotNull();
		assertThat(saved.stoppedAt()).isNull();
	}

	@Test
	void updatesExistingHistoryForSameThreadId() {
		sessionHistoryService.start("thread-1", "session-1", "old query").block();
		sessionHistoryService.start("thread-1", "session-2", "new query").block();

		assertThat(repository.count().block()).isEqualTo(1);

		ResearchSessionHistory saved = sessionHistoryService.findBySessionIdAndThreadId("session-2", "thread-1")
			.block();
		assertThat(saved).isNotNull();
		assertThat(saved.sessionId()).isEqualTo("session-2");
		assertThat(saved.query()).isEqualTo("new query");
		assertThat(saved.status()).isEqualTo("RUNNING");
	}

	@Test
	void marksPausedStoppedAndFailedStatuses() {
		sessionHistoryService.start("thread-1", "session-1", "query").block();

		ResearchSessionHistory paused = sessionHistoryService.markPaused("thread-1").block();
		ResearchSessionHistory stopped = sessionHistoryService.markStopped("thread-1").block();
		ResearchSessionHistory failed = sessionHistoryService.markFailed("thread-1", "model error").block();

		assertThat(paused).isNotNull();
		assertThat(paused.status()).isEqualTo("PAUSED");
		assertThat(stopped).isNotNull();
		assertThat(stopped.status()).isEqualTo("STOPPED");
		assertThat(stopped.stoppedAt()).isNotNull();
		assertThat(failed).isNotNull();
		assertThat(failed.status()).isEqualTo("FAILED");
		assertThat(failed.errorMessage()).isEqualTo("model error");
	}

	@Test
	void findsSessionHistoryAndRecentItemsByUpdatedTime() {
		sessionHistoryService.start("thread-1", "session-1", "query 1").block();
		sessionHistoryService.start("thread-2", "session-1", "query 2").block();
		sessionHistoryService.start("thread-3", "session-2", "query 3").block();
		sessionHistoryService.markPaused("thread-1").block();

		var sessionHistories = sessionHistoryService.findBySessionId("session-1").collectList().block();
		var recent = sessionHistoryService.findRecentBySessionId("session-1", 1).collectList().block();

		assertThat(sessionHistories).isNotNull();
		assertThat(sessionHistories).extracting(ResearchSessionHistory::threadId)
			.containsExactly("thread-1", "thread-2");
		assertThat(recent).isNotNull();
		assertThat(recent).singleElement().satisfies(history -> assertThat(history.threadId()).isEqualTo("thread-1"));
	}

}
