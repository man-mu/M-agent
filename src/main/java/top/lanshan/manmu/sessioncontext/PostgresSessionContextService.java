package top.lanshan.manmu.sessioncontext;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.lanshan.manmu.report.ReportService;
import top.lanshan.manmu.sessionhistory.ResearchSessionHistory;
import top.lanshan.manmu.sessionhistory.SessionHistoryService;
import top.lanshan.manmu.sessionhistory.SessionHistoryStatus;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class PostgresSessionContextService implements SessionContextService {

	private static final int FETCH_MULTIPLIER = 4;

	private final SessionHistoryService sessionHistoryService;

	private final ReportService reportService;

	private final int maxReports;

	private final int maxReportCharacters;

	public PostgresSessionContextService(SessionHistoryService sessionHistoryService, ReportService reportService,
			@Value("${manmu.session-context.max-reports:5}") int maxReports,
			@Value("${manmu.session-context.max-report-characters:1200}") int maxReportCharacters) {
		this.sessionHistoryService = sessionHistoryService;
		this.reportService = reportService;
		this.maxReports = Math.max(1, maxReports);
		this.maxReportCharacters = Math.max(200, maxReportCharacters);
	}

	@Override
	public Flux<SessionContextReport> findRecentCompletedReports(String sessionId, String currentThreadId) {
		if (sessionId == null || sessionId.isBlank()) {
			return Flux.empty();
		}
		return sessionHistoryService.findRecentBySessionId(sessionId, maxReports * FETCH_MULTIPLIER)
			.filter(history -> isCompletedReport(history, currentThreadId))
			.concatMap(history -> reportService.getReport(history.reportThreadId())
				.filter(report -> !report.isBlank())
				.map(report -> toContextReport(history, report)))
			.take(maxReports);
	}

	@Override
	public Mono<String> formatRecentCompletedReports(String sessionId, String currentThreadId) {
		return findRecentCompletedReports(sessionId, currentThreadId).collectList().map(this::formatReports);
	}

	private boolean isCompletedReport(ResearchSessionHistory history, String currentThreadId) {
		return history != null && SessionHistoryStatus.COMPLETED.name().equals(history.status())
				&& hasText(history.reportThreadId()) && !sameThread(history, currentThreadId);
	}

	private boolean sameThread(ResearchSessionHistory history, String currentThreadId) {
		if (!hasText(currentThreadId)) {
			return false;
		}
		return currentThreadId.equals(history.threadId()) || currentThreadId.equals(history.reportThreadId());
	}

	private SessionContextReport toContextReport(ResearchSessionHistory history, String report) {
		return new SessionContextReport(history.threadId(), history.reportThreadId(), nullToEmpty(history.query()),
				truncate(report.strip()), history.completedAt());
	}

	private String formatReports(List<SessionContextReport> reports) {
		if (reports.isEmpty()) {
			return "";
		}
		return reports.stream().map(this::formatReport).collect(Collectors.joining("\n\n"));
	}

	private String formatReport(SessionContextReport report) {
		String completedAt = report.completedAt() == null ? "unknown" : report.completedAt().toString();
		return """
				Previous report
				Thread: %s
				Report thread: %s
				Completed at: %s
				Query: %s
				Report excerpt:
				%s
				""".formatted(report.threadId(), report.reportThreadId(), completedAt, report.query(),
				report.reportExcerpt()).strip();
	}

	private String truncate(String value) {
		if (value.length() <= maxReportCharacters) {
			return value;
		}
		return value.substring(0, maxReportCharacters).stripTrailing() + "\n[truncated]";
	}

	private boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private String nullToEmpty(String value) {
		return Objects.toString(value, "");
	}

}
