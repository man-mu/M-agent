package top.lanshan.manmu.eventhistory;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ResearchEventHistoryRepository extends ReactiveCrudRepository<ResearchEventHistoryEntity, UUID> {

	Flux<ResearchEventHistoryEntity> findByThreadIdOrderBySequenceAsc(String threadId);

	Flux<ResearchEventHistoryEntity> findBySessionIdAndThreadIdOrderBySequenceAsc(String sessionId, String threadId);

	@Modifying
	@Query("DELETE FROM research_events WHERE session_id = $1")
	Mono<Integer> deleteBySessionId(String sessionId);

}
