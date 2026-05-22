package top.lanshan.manmu.report;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PostgresReportServiceTest {

	@Autowired
	ReportService reportService;

	@Autowired
	ResearchReportRepository repository;

	@BeforeEach
	void cleanReports() {
		repository.deleteAll().block();
	}

	@Test
	void savesAndReadsCompletedReportByThreadId() {
		reportService.saveCompletedReport("thread-1", "session-1", "What is DeepResearch?", "# Report").block();

		assertThat(reportService.existsReport("thread-1").block()).isTrue();
		assertThat(reportService.getReport("thread-1").block()).isEqualTo("# Report");

		ResearchReport saved = reportService.findBySessionId("session-1").single().block();
		assertThat(saved).isNotNull();
		assertThat(saved.threadId()).isEqualTo("thread-1");
		assertThat(saved.sessionId()).isEqualTo("session-1");
		assertThat(saved.query()).isEqualTo("What is DeepResearch?");
		assertThat(saved.status()).isEqualTo("COMPLETED");
		assertThat(saved.createdAt()).isNotNull();
		assertThat(saved.updatedAt()).isNotNull();
	}

	@Test
	void updatesExistingReportForSameThreadId() {
		reportService.saveCompletedReport("thread-1", "session-1", "old query", "old report").block();
		reportService.saveCompletedReport("thread-1", "session-2", "new query", "new report").block();

		assertThat(repository.count().block()).isEqualTo(1);
		assertThat(reportService.getReport("thread-1").block()).isEqualTo("new report");

		ResearchReport saved = reportService.findBySessionId("session-2").single().block();
		assertThat(saved).isNotNull();
		assertThat(saved.query()).isEqualTo("new query");
	}

	@Test
	void deletesReportByThreadId() {
		reportService.saveCompletedReport("thread-1", "session-1", "query", "report").block();

		reportService.deleteReport("thread-1").block();

		assertThat(reportService.existsReport("thread-1").block()).isFalse();
		assertThat(reportService.getReport("thread-1").block()).isNull();
	}

}
