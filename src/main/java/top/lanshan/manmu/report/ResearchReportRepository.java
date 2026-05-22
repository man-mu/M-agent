package top.lanshan.manmu.report;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface ResearchReportRepository extends ReactiveCrudRepository<ResearchReportEntity, UUID> {

	Mono<ResearchReportEntity> findByThreadId(String threadId);

	Flux<ResearchReportEntity> findBySessionIdOrderByUpdatedAtDesc(String sessionId);

	Mono<Boolean> existsByThreadId(String threadId);

	Mono<Void> deleteByThreadId(String threadId);

}
