package top.lanshan.manmu.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import top.lanshan.manmu.sessionhistory.ResearchSessionHistory;
import top.lanshan.manmu.sessionhistory.SessionHistoryResponse;
import top.lanshan.manmu.sessionhistory.SessionHistoryService;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class SessionHistoryController {

	private final SessionHistoryService sessionHistoryService;

	public SessionHistoryController(SessionHistoryService sessionHistoryService) {
		this.sessionHistoryService = sessionHistoryService;
	}

	@GetMapping("/{sessionId}/history")
	public Mono<ResponseEntity<SessionHistoryResponse<List<ResearchSessionHistory>>>> getHistory(
			@PathVariable String sessionId) {
		return sessionHistoryService.findBySessionId(sessionId)
			.collectList()
			.map(histories -> ResponseEntity
				.ok(SessionHistoryResponse.success(sessionId, null, "Session history retrieved successfully", histories)))
			.onErrorResume(error -> Mono.just(ResponseEntity.internalServerError()
				.body(SessionHistoryResponse.error(sessionId, null, "Failed to get history: " + error.getMessage()))));
	}

	@GetMapping("/{sessionId}/threads/{threadId}")
	public Mono<ResponseEntity<SessionHistoryResponse<ResearchSessionHistory>>> getThreadHistory(
			@PathVariable String sessionId, @PathVariable String threadId) {
		return sessionHistoryService.findBySessionIdAndThreadId(sessionId, threadId)
			.map(history -> ResponseEntity
				.ok(SessionHistoryResponse.success(sessionId, threadId, "Thread history retrieved successfully", history)))
			.defaultIfEmpty(ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(SessionHistoryResponse.notFound(sessionId, threadId, "Thread history not found")))
			.onErrorResume(error -> Mono.just(ResponseEntity.internalServerError()
				.body(SessionHistoryResponse.error(sessionId, threadId, "Failed to get history: " + error.getMessage()))));
	}

	@GetMapping("/{sessionId}/recent")
	public Mono<ResponseEntity<SessionHistoryResponse<List<ResearchSessionHistory>>>> getRecentHistory(
			@PathVariable String sessionId, @RequestParam(defaultValue = "5") int count) {
		return sessionHistoryService.findRecentBySessionId(sessionId, count)
			.collectList()
			.map(histories -> ResponseEntity
				.ok(SessionHistoryResponse.success(sessionId, null, "Recent history retrieved successfully", histories)))
			.onErrorResume(error -> Mono.just(ResponseEntity.internalServerError()
				.body(SessionHistoryResponse.error(sessionId, null, "Failed to get history: " + error.getMessage()))));
	}

}
