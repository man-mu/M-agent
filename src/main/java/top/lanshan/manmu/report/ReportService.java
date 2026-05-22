package top.lanshan.manmu.report;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReportService {

	Mono<ResearchReport> saveCompletedReport(String threadId, String sessionId, String query, String report);

	Mono<String> getReport(String threadId);

	Mono<Boolean> existsReport(String threadId);

	Mono<Void> deleteReport(String threadId);

	Flux<ResearchReport> findBySessionId(String sessionId);

}
