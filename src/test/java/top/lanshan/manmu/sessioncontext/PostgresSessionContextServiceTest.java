package top.lanshan.manmu.sessioncontext;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.lanshan.manmu.report.ReportService;
import top.lanshan.manmu.report.ResearchReport;
import top.lanshan.manmu.sessionhistory.ResearchSessionHistory;
import top.lanshan.manmu.sessionhistory.SessionHistoryService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresSessionContextServiceTest {

	@Test
	void formatsRecentCompletedReportsAndExcludesCurrentThread() {
		RecordingSessionHistoryService historyService = new RecordingSessionHistoryService();
		RecordingReportService reportService = new RecordingReportService();
		Instant now = Instant.parse("2026-05-22T06:00:00Z");
		historyService.history(new ResearchSessionHistory("current-thread", "session-1", "current query",
				"COMPLETED", "current-thread", null, now, now, now, null));
		historyService.history(new ResearchSessionHistory("old-thread-1", "session-1", "first query", "COMPLETED",
				"old-thread-1", null, now, now, now, null));
		historyService.history(new ResearchSessionHistory("old-thread-2", "session-1", "second query", "COMPLETED",
				"report-thread-2", null, now, now, now, null));
		historyService.history(new ResearchSessionHistory("paused-thread", "session-1", "paused query", "PAUSED",
				null, null, now, now, null, null));
		reportService.report("old-thread-1", "First report body");
		reportService.report("report-thread-2", "Second report body");
		PostgresSessionContextService service = new PostgresSessionContextService(historyService, reportService, 5,
				1000);

		String context = service.formatRecentCompletedReports("session-1", "current-thread").block();

		assertThat(context).isNotNull();
		assertThat(context).contains("first query")
			.contains("First report body")
			.contains("second query")
			.contains("Second report body")
			.doesNotContain("current query")
			.doesNotContain("paused query");
	}

	@Test
	void returnsEmptyContextForNewSession() {
		PostgresSessionContextService service = new PostgresSessionContextService(new RecordingSessionHistoryService(),
				new RecordingReportService(), 5, 1000);

		String context = service.formatRecentCompletedReports("new-session", "thread-1").block();

		assertThat(context).isEqualTo("");
	}

	@Test
	void limitsReportCountAndReportExcerptLength() {
		RecordingSessionHistoryService historyService = new RecordingSessionHistoryService();
		RecordingReportService reportService = new RecordingReportService();
		Instant now = Instant.parse("2026-05-22T06:00:00Z");
		historyService.history(new ResearchSessionHistory("thread-1", "session-1", "first query", "COMPLETED",
				"thread-1", null, now, now, now, null));
		historyService.history(new ResearchSessionHistory("thread-2", "session-1", "second query", "COMPLETED",
				"thread-2", null, now, now, now, null));
		reportService.report("thread-1", "A".repeat(260));
		reportService.report("thread-2", "Second report body");
		PostgresSessionContextService service = new PostgresSessionContextService(historyService, reportService, 1,
				200);

		String context = service.formatRecentCompletedReports("session-1", "current-thread").block();

		assertThat(context).isNotNull();
		assertThat(context).contains("first query").contains("[truncated]");
		assertThat(context).doesNotContain("second query");
	}

	private static class RecordingSessionHistoryService implements SessionHistoryService {

		private final List<ResearchSessionHistory> histories = new ArrayList<>();

		void history(ResearchSessionHistory history) {
			histories.add(history);
		}

		@Override
		public Mono<ResearchSessionHistory> start(String threadId, String sessionId, String query) {
			return Mono.empty();
		}

		@Override
		public Mono<ResearchSessionHistory> markRunning(String threadId) {
			return Mono.empty();
		}

		@Override
		public Mono<ResearchSessionHistory> markPaused(String threadId) {
			return Mono.empty();
		}

		@Override
		public Mono<ResearchSessionHistory> markCompleted(String threadId, String reportThreadId) {
			return Mono.empty();
		}

		@Override
		public Mono<ResearchSessionHistory> markStopped(String threadId) {
			return Mono.empty();
		}

		@Override
		public Mono<ResearchSessionHistory> markFailed(String threadId, String errorMessage) {
			return Mono.empty();
		}

		@Override
		public Flux<ResearchSessionHistory> findBySessionId(String sessionId) {
			return Flux.fromIterable(histories).filter(history -> sessionId.equals(history.sessionId()));
		}

		@Override
		public Mono<ResearchSessionHistory> findBySessionIdAndThreadId(String sessionId, String threadId) {
			return findBySessionId(sessionId).filter(history -> threadId.equals(history.threadId())).singleOrEmpty();
		}

		@Override
		public Flux<ResearchSessionHistory> findRecentBySessionId(String sessionId, int count) {
			return findBySessionId(sessionId).take(count);
		}

	}

	private static class RecordingReportService implements ReportService {

		private final Map<String, String> reports = new LinkedHashMap<>();

		void report(String threadId, String report) {
			reports.put(threadId, report);
		}

		@Override
		public Mono<ResearchReport> saveCompletedReport(String threadId, String sessionId, String query, String report) {
			return Mono.empty();
		}

		@Override
		public Mono<String> getReport(String threadId) {
			return Mono.justOrEmpty(reports.get(threadId));
		}

		@Override
		public Mono<Boolean> existsReport(String threadId) {
			return Mono.just(reports.containsKey(threadId));
		}

		@Override
		public Mono<Void> deleteReport(String threadId) {
			reports.remove(threadId);
			return Mono.empty();
		}

		@Override
		public Flux<ResearchReport> findBySessionId(String sessionId) {
			return Flux.empty();
		}

	}

}
