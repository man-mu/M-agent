package top.lanshan.manmu.api;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.lanshan.manmu.model.ApiResponse;
import top.lanshan.manmu.model.BackgroundInvestigationPayload;
import top.lanshan.manmu.model.ChatRequest;
import top.lanshan.manmu.model.ChatStreamResponse;
import top.lanshan.manmu.model.FeedbackRequest;
import top.lanshan.manmu.model.GraphId;
import top.lanshan.manmu.model.InformationPayload;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchStreamEventType;
import top.lanshan.manmu.eventhistory.ResearchEventHistoryService;
import top.lanshan.manmu.runner.ResearchRunner;
import top.lanshan.manmu.runner.ResumeDecision;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/chat")
public class ChatController {

	private final ResearchRunner runner;

	private final ResearchEventHistoryService eventHistoryService;

	private final ConcurrentHashMap<String, Integer> sessionCount = new ConcurrentHashMap<>();

	public ChatController(ResearchRunner runner, ResearchEventHistoryService eventHistoryService) {
		this.runner = runner;
		this.eventHistoryService = eventHistoryService;
	}

	@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<ChatStreamResponse>> stream(@Valid @RequestBody ChatRequest request) {
		GraphId graphId = graphId(request);
		ResearchRequest researchRequest = new ResearchRequest(request.query(), graphId.threadId(), request.maxStepNum(),
				request.optimizeQueryNum(), request.enableDeepResearch(), request.autoAcceptedPlan());

		Flux<ResearchEvent> events = request.autoAcceptedPlan() ? runner.runChat(researchRequest, graphId.sessionId())
				: runner.runUntilPlanGate(researchRequest, graphId.sessionId());
		return events.concatMap(event -> toPersistedResponse(graphId, event)
			.map(response -> ServerSentEvent.<ChatStreamResponse>builder()
				.id(graphId.threadId())
				.event(eventName(event))
				.data(response)
				.build()));
	}

	@PostMapping(value = "/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<ChatStreamResponse>> resume(@Valid @RequestBody FeedbackRequest request) {
		GraphId graphId = new GraphId(request.sessionId(), request.threadId());
		ResumeDecision decision = new ResumeDecision(request.feedback(), request.feedbackContent());
		return runner.resume(request.threadId(), decision)
			.concatMap(event -> toPersistedResponse(graphId, event)
				.map(response -> ServerSentEvent.<ChatStreamResponse>builder()
					.id(graphId.threadId())
					.event(eventName(event))
					.data(response)
					.build()));
	}

	@PostMapping("/stop")
	public Mono<ApiResponse<String>> stop(@Valid @RequestBody GraphId graphId) {
		return runner.stopAndRecord(graphId.threadId())
			.map(stopped -> stopped ? ApiResponse.success(graphId.threadId())
					: ApiResponse.error("Failure", graphId.threadId()));
	}

	private GraphId graphId(ChatRequest request) {
		if (StringUtils.hasText(request.threadId())) {
			return new GraphId(request.sessionId(), request.threadId());
		}
		int count = sessionCount.merge(request.sessionId(), 1, Integer::sum);
		return new GraphId(request.sessionId(), "%s-%d".formatted(request.sessionId(), count));
	}

	private ChatStreamResponse toResponse(GraphId graphId, ResearchEvent event) {
		Object content = content(event);
		Object siteInformation = siteInformation(event);
		return ChatStreamResponse.from(graphId, event, content, siteInformation);
	}

	private Mono<ChatStreamResponse> toPersistedResponse(GraphId graphId, ResearchEvent event) {
		ChatStreamResponse response = toResponse(graphId, event);
		return eventHistoryService.save(graphId.sessionId(), graphId.threadId(), response).thenReturn(response);
	}

	private Object content(ResearchEvent event) {
		if ("error".equals(event.phase())) {
			return Map.of("reason", safeContent(event.content(), "Unknown error"));
		}
		if ("stopped".equals(event.phase())) {
			return Map.of("reason", safeContent(event.content(), "Research workflow stopped"), "done", true);
		}
		if (event.done()) {
			Map<String, Object> done = new LinkedHashMap<>();
			done.put("output", event.payload());
			done.put("done", true);
			return done;
		}
		return event.payload() == null ? event.content() : event.payload();
	}

	private String safeContent(String content, String fallback) {
		return content == null || content.isBlank() ? fallback : content;
	}

	private Object siteInformation(ResearchEvent event) {
		if ("information".equals(event.node()) && event.payload() instanceof InformationPayload payload) {
			return payload.siteInformation();
		}
		if ("information".equals(event.node()) && event.payload() instanceof List<?> payload) {
			return payload;
		}
		if ("background_investigator".equals(event.node())
				&& event.payload() instanceof BackgroundInvestigationPayload payload) {
			return payload.siteInformation();
		}
		return List.of();
	}

	private String eventName(ResearchEvent event) {
		ResearchStreamEventType eventType = event.eventType() == null ? ResearchStreamEventType.from(event)
				: event.eventType();
		if (ResearchStreamEventType.GRAPH_FAILED.equals(eventType)
				|| ResearchStreamEventType.NODE_FAILED.equals(eventType)) {
			return "error";
		}
		if (ResearchStreamEventType.GRAPH_STOPPED.equals(eventType)) {
			return "stopped";
		}
		return event.done() ? "done" : "message";
	}

}
