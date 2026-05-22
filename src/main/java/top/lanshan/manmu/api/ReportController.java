package top.lanshan.manmu.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.lanshan.manmu.report.ReportResponse;
import top.lanshan.manmu.report.ReportService;
import top.lanshan.manmu.report.ResearchReport;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

	private final ReportService reportService;

	public ReportController(ReportService reportService) {
		this.reportService = reportService;
	}

	@GetMapping("/{threadId}")
	public Mono<ResponseEntity<ReportResponse<String>>> getReport(@PathVariable String threadId) {
		return reportService.getReport(threadId)
			.map(report -> ResponseEntity.ok(ReportResponse.success(threadId, "Report retrieved successfully", report)))
			.defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ReportResponse.notFound(threadId, "Report not found")))
			.onErrorResume(error -> Mono.just(ResponseEntity.internalServerError()
				.body(ReportResponse.error(threadId, "Failed to get report: " + error.getMessage()))));
	}

	@GetMapping("/{threadId}/exists")
	public Mono<ResponseEntity<ReportResponse<Boolean>>> existsReport(@PathVariable String threadId) {
		return reportService.existsReport(threadId)
			.map(exists -> ResponseEntity.ok(ReportResponse.success(threadId, "whether the report exists", exists)))
			.onErrorResume(error -> Mono.just(ResponseEntity.internalServerError()
				.body(ReportResponse.error(threadId, "Check failed: " + error.getMessage()))));
	}

	@DeleteMapping("/{threadId}")
	public Mono<ResponseEntity<ReportResponse<Void>>> deleteReport(@PathVariable String threadId) {
		return reportService.existsReport(threadId).flatMap(exists -> {
			if (!exists) {
				return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(ReportResponse.<Void>notFound(threadId, "Report not found")));
			}
			return reportService.deleteReport(threadId)
				.thenReturn(ResponseEntity.ok(ReportResponse.<Void>success(threadId, "Report deleted successfully", null)));
		}).onErrorResume(error -> Mono.just(ResponseEntity.internalServerError()
			.body(ReportResponse.error(threadId, "Failed to delete report: " + error.getMessage()))));
	}

	@GetMapping("/session/{sessionId}")
	public Mono<ResponseEntity<ReportResponse<List<ResearchReport>>>> getReportsBySession(@PathVariable String sessionId) {
		return reportService.findBySessionId(sessionId)
			.collectList()
			.map(reports -> ResponseEntity.ok(ReportResponse.success(sessionId, "Reports retrieved successfully", reports)))
			.onErrorResume(error -> Mono.just(ResponseEntity.internalServerError()
				.body(ReportResponse.error(sessionId, "Failed to get reports: " + error.getMessage()))));
	}

}
