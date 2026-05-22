package top.lanshan.manmu.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.lanshan.manmu.report.ReportService;
import top.lanshan.manmu.report.ResearchReport;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportControllerTest {

	@Test
	void getReportReturnsDeepResearchCompatibleEnvelope() {
		ReportService reportService = mock(ReportService.class);
		when(reportService.getReport("thread-1")).thenReturn(Mono.just("# Final report"));
		WebTestClient client = WebTestClient.bindToController(new ReportController(reportService)).build();

		client.get()
			.uri("/api/reports/thread-1")
			.exchange()
			.expectStatus()
			.isOk()
			.expectHeader()
			.contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
			.expectBody()
			.jsonPath("$.thread_id")
			.isEqualTo("thread-1")
			.jsonPath("$.status")
			.isEqualTo("success")
			.jsonPath("$.report_information")
			.isEqualTo("# Final report");

		verify(reportService).getReport("thread-1");
	}

	@Test
	void getReportReturnsNotFoundWhenMissing() {
		ReportService reportService = mock(ReportService.class);
		when(reportService.getReport("missing-thread")).thenReturn(Mono.empty());
		WebTestClient client = WebTestClient.bindToController(new ReportController(reportService)).build();

		client.get()
			.uri("/api/reports/missing-thread")
			.exchange()
			.expectStatus()
			.isNotFound()
			.expectBody()
			.jsonPath("$.thread_id")
			.isEqualTo("missing-thread")
			.jsonPath("$.status")
			.isEqualTo("notfound");
	}

	@Test
	void existsReportReturnsBooleanEnvelope() {
		ReportService reportService = mock(ReportService.class);
		when(reportService.existsReport("thread-1")).thenReturn(Mono.just(true));
		WebTestClient client = WebTestClient.bindToController(new ReportController(reportService)).build();

		client.get()
			.uri("/api/reports/thread-1/exists")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.report_information")
			.isEqualTo(true);
	}

	@Test
	void deleteReportDeletesExistingReport() {
		ReportService reportService = mock(ReportService.class);
		when(reportService.existsReport("thread-1")).thenReturn(Mono.just(true));
		when(reportService.deleteReport("thread-1")).thenReturn(Mono.empty());
		WebTestClient client = WebTestClient.bindToController(new ReportController(reportService)).build();

		client.delete()
			.uri("/api/reports/thread-1")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.status")
			.isEqualTo("success");

		verify(reportService).deleteReport("thread-1");
	}

	@Test
	void sessionReportsReturnPersistedReportMetadata() {
		Instant now = Instant.parse("2026-05-22T00:00:00Z");
		ReportService reportService = mock(ReportService.class);
		when(reportService.findBySessionId("session-1")).thenReturn(Flux.just(new ResearchReport("thread-1",
				"session-1", "query", "report", "COMPLETED", null, now, now)));
		WebTestClient client = WebTestClient.bindToController(new ReportController(reportService)).build();

		client.get()
			.uri("/api/reports/session/session-1")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.thread_id")
			.isEqualTo("session-1")
			.jsonPath("$.report_information[0].thread_id")
			.isEqualTo("thread-1")
			.jsonPath("$.report_information[0].session_id")
			.isEqualTo("session-1");
	}

}
