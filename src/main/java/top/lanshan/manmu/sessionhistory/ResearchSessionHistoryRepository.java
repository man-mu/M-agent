package top.lanshan.manmu.sessionhistory;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ResearchSessionHistoryRepository extends ReactiveCrudRepository<ResearchSessionHistoryEntity, UUID> {

	Mono<ResearchSessionHistoryEntity> findByThreadId(String threadId);

	Mono<ResearchSessionHistoryEntity> findBySessionIdAndThreadId(String sessionId, String threadId);

	Flux<ResearchSessionHistoryEntity> findBySessionIdOrderByUpdatedAtDesc(String sessionId);

}
