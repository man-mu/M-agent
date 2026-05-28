package top.lanshan.manmu.eventhistory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.lanshan.manmu.model.ChatStreamResponse;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class PostgresResearchEventHistoryService implements ResearchEventHistoryService {

	private static final Logger logger = LoggerFactory.getLogger(PostgresResearchEventHistoryService.class);

	private final ResearchEventHistoryRepository repository;

	private final ObjectMapper objectMapper;

	public PostgresResearchEventHistoryService(ResearchEventHistoryRepository repository, ObjectMapper objectMapper) {
		this.repository = Objects.requireNonNull(repository, "repository must not be null");
		this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
	}

	@Override
	public Mono<ResearchEventRecord> save(String sessionId, String threadId, ChatStreamResponse event) {
		if (isBlank(sessionId) || isBlank(threadId) || event == null || event.sequence() == null) {
			return Mono.empty();
		}
		ResearchEventHistoryEntity entity;
		try {
			entity = toEntity(sessionId.strip(), threadId.strip(), event);
		}
		catch (JsonProcessingException ex) {
			logger.warn("Failed to serialize research event {} for thread {}: {}", event.sequence(), threadId,
					errorMessage(ex));
			return Mono.empty();
		}
		return repository.save(entity)
			.map(saved -> saved.toRecord(objectMapper))
			.onErrorResume(error -> {
				logger.warn("Failed to save research event {} for thread {}: {}", event.sequence(), threadId,
						errorMessage(error));
				return Mono.empty();
			});
	}

	@Override
	public Flux<ResearchEventRecord> findByThreadId(String threadId) {
		if (isBlank(threadId)) {
			return Flux.empty();
		}
		return repository.findByThreadIdOrderBySequenceAsc(threadId.strip()).map(entity -> entity.toRecord(objectMapper));
	}

	@Override
	public Flux<ResearchEventRecord> findBySessionIdAndThreadId(String sessionId, String threadId) {
		if (isBlank(sessionId) || isBlank(threadId)) {
			return Flux.empty();
		}
		return repository.findBySessionIdAndThreadIdOrderBySequenceAsc(sessionId.strip(), threadId.strip())
			.map(entity -> entity.toRecord(objectMapper));
	}

	@Override
	public Mono<Integer> deleteBySessionId(String sessionId) {
		if (isBlank(sessionId)) {
			return Mono.just(0);
		}
		return repository.deleteBySessionId(sessionId.strip()).defaultIfEmpty(0);
	}

	private ResearchEventHistoryEntity toEntity(String sessionId, String threadId, ChatStreamResponse event)
			throws JsonProcessingException {
		Instant now = Instant.now();
		ResearchEventHistoryEntity entity = new ResearchEventHistoryEntity();
		entity.setNewEvent(true);
		entity.setId(UUID.randomUUID());
		entity.setSessionId(sessionId);
		entity.setThreadId(threadId);
		entity.setSequence(event.sequence());
		entity.setEventType(event.eventType() == null ? "" : event.eventType().value());
		entity.setNodeName(event.stableNodeName() == null ? event.nodeName() : event.stableNodeName());
		entity.setNodeType(event.nodeType());
		entity.setExecutorId(event.executorId());
		entity.setStepId(event.stepId());
		entity.setPhase(event.phase());
		entity.setStatus(event.status());
		entity.setDisplayTitle(event.stableDisplayTitle() == null ? event.displayTitle() : event.stableDisplayTitle());
		entity.setDone(event.done());
		entity.setEventTimestamp(event.timestamp() == null ? now : event.timestamp());
		entity.setEventJson(objectMapper.writeValueAsString(event));
		entity.setCreatedAt(now);
		return entity;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private String errorMessage(Throwable error) {
		if (error == null) {
			return "Unknown error";
		}
		if (error.getMessage() != null && !error.getMessage().isBlank()) {
			return error.getMessage();
		}
		return error.getClass().getSimpleName();
	}

}
