package top.lanshan.manmu.eventhistory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.lanshan.manmu.model.ChatStreamResponse;

public interface ResearchEventHistoryService {

	Mono<ResearchEventRecord> save(String sessionId, String threadId, ChatStreamResponse event);

	Flux<ResearchEventRecord> findByThreadId(String threadId);

	Flux<ResearchEventRecord> findBySessionIdAndThreadId(String sessionId, String threadId);

	Mono<Integer> deleteBySessionId(String sessionId);

}
