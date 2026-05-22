package top.lanshan.manmu.sessioncontext;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SessionContextService {

	Flux<SessionContextReport> findRecentCompletedReports(String sessionId, String currentThreadId);

	Mono<String> formatRecentCompletedReports(String sessionId, String currentThreadId);

}
