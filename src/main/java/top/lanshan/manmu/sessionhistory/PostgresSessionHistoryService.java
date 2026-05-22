package top.lanshan.manmu.sessionhistory;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
public class PostgresSessionHistoryService implements SessionHistoryService {

	private static final int MAX_RECENT_COUNT = 50;

	private final ResearchSessionHistoryRepository repository;

	public PostgresSessionHistoryService(ResearchSessionHistoryRepository repository) {
		this.repository = repository;
	}

	@Override
	public Mono<ResearchSessionHistory> start(String threadId, String sessionId, String query) {
		Instant now = Instant.now();
		return repository.findByThreadId(threadId)
			.defaultIfEmpty(newEntity(threadId, sessionId, query, now))
			.flatMap(entity -> {
				entity.setSessionId(sessionId);
				entity.setQuery(query);
				entity.setStatus(SessionHistoryStatus.RUNNING.name());
				entity.setReportThreadId(null);
				entity.setErrorMessage(null);
				entity.setCompletedAt(null);
				entity.setStoppedAt(null);
				entity.setUpdatedAt(now);
				return repository.save(entity);
			})
			.map(ResearchSessionHistoryEntity::toHistory);
	}

	@Override
	public Mono<ResearchSessionHistory> markRunning(String threadId) {
		return updateStatus(threadId, SessionHistoryStatus.RUNNING, null, null);
	}

	@Override
	public Mono<ResearchSessionHistory> markPaused(String threadId) {
		return updateStatus(threadId, SessionHistoryStatus.PAUSED, null, null);
	}

	@Override
	public Mono<ResearchSessionHistory> markCompleted(String threadId, String reportThreadId) {
		return updateStatus(threadId, SessionHistoryStatus.COMPLETED, reportThreadId, null);
	}

	@Override
	public Mono<ResearchSessionHistory> markStopped(String threadId) {
		return updateStatus(threadId, SessionHistoryStatus.STOPPED, null, null);
	}

	@Override
	public Mono<ResearchSessionHistory> markFailed(String threadId, String errorMessage) {
		return updateStatus(threadId, SessionHistoryStatus.FAILED, null, errorMessage);
	}

	@Override
	public Flux<ResearchSessionHistory> findBySessionId(String sessionId) {
		return repository.findBySessionIdOrderByUpdatedAtDesc(sessionId).map(ResearchSessionHistoryEntity::toHistory);
	}

	@Override
	public Mono<ResearchSessionHistory> findBySessionIdAndThreadId(String sessionId, String threadId) {
		return repository.findBySessionIdAndThreadId(sessionId, threadId).map(ResearchSessionHistoryEntity::toHistory);
	}

	@Override
	public Flux<ResearchSessionHistory> findRecentBySessionId(String sessionId, int count) {
		if (count < 1) {
			return Flux.empty();
		}
		return findBySessionId(sessionId).take(Math.min(count, MAX_RECENT_COUNT));
	}

	private Mono<ResearchSessionHistory> updateStatus(String threadId, SessionHistoryStatus status, String reportThreadId,
			String errorMessage) {
		Instant now = Instant.now();
		return repository.findByThreadId(threadId).flatMap(entity -> {
			entity.setStatus(status.name());
			entity.setUpdatedAt(now);
			switch (status) {
				case RUNNING, PAUSED -> {
					entity.setErrorMessage(null);
					entity.setStoppedAt(null);
				}
				case COMPLETED -> {
					entity.setReportThreadId(reportThreadId);
					entity.setErrorMessage(null);
					entity.setCompletedAt(now);
					entity.setStoppedAt(null);
				}
				case STOPPED -> {
					entity.setErrorMessage(null);
					entity.setStoppedAt(now);
				}
				case FAILED -> entity.setErrorMessage(errorMessage);
			}
			return repository.save(entity);
		}).map(ResearchSessionHistoryEntity::toHistory);
	}

	private ResearchSessionHistoryEntity newEntity(String threadId, String sessionId, String query, Instant now) {
		ResearchSessionHistoryEntity entity = new ResearchSessionHistoryEntity();
		entity.setNewHistory(true);
		entity.setId(UUID.randomUUID());
		entity.setThreadId(threadId);
		entity.setSessionId(sessionId);
		entity.setQuery(query);
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		return entity;
	}

}
