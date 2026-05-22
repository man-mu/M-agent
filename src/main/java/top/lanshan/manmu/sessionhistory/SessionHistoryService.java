package top.lanshan.manmu.sessionhistory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SessionHistoryService {

	Mono<ResearchSessionHistory> start(String threadId, String sessionId, String query);

	Mono<ResearchSessionHistory> markRunning(String threadId);

	Mono<ResearchSessionHistory> markPaused(String threadId);

	Mono<ResearchSessionHistory> markCompleted(String threadId, String reportThreadId);

	Mono<ResearchSessionHistory> markStopped(String threadId);

	Mono<ResearchSessionHistory> markFailed(String threadId, String errorMessage);

	Flux<ResearchSessionHistory> findBySessionId(String sessionId);

	Mono<ResearchSessionHistory> findBySessionIdAndThreadId(String sessionId, String threadId);

	Flux<ResearchSessionHistory> findRecentBySessionId(String sessionId, int count);

}
