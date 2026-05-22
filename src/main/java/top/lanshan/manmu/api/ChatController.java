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
import top.lanshan.manmu.runner.ResumeDecision;
import top.lanshan.manmu.runner.SimpleResearchRunner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/chat")
public class ChatController {

	private final SimpleResearchRunner runner;

	private final ConcurrentHashMap<String, Integer> sessionCount = new ConcurrentHashMap<>();

	public ChatController(SimpleResearchRunner runner) {
		this.runner = runner;
	}

	@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<ChatStreamResponse>> stream(@Valid @RequestBody ChatRequest request) {
		GraphId graphId = graphId(request);
		ResearchRequest researchRequest = new ResearchRequest(request.query(), graphId.threadId(), request.maxStepNum(),
				request.optimizeQueryNum(), request.enableDeepResearch(), request.autoAcceptedPlan());

		Flux<ResearchEvent> events = request.autoAcceptedPlan() ? runner.runChat(researchRequest, graphId.sessionId())
				: runner.runUntilPlanGate(researchRequest, graphId.sessionId());
		return events.map(event -> ServerSentEvent.<ChatStreamResponse>builder()
			.id(graphId.threadId())
			.event(eventName(event))
			.data(toResponse(graphId, event))
			.build());
	}

	@PostMapping(value = "/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<ChatStreamResponse>> resume(@Valid @RequestBody FeedbackRequest request) {
		GraphId graphId = new GraphId(request.sessionId(), request.threadId());
		ResumeDecision decision = new ResumeDecision(request.feedback(), request.feedbackContent());
		return runner.resume(request.threadId(), decision).map(event -> ServerSentEvent.<ChatStreamResponse>builder()
			.id(graphId.threadId())
			.event(eventName(event))
			.data(toResponse(graphId, event))
			.build());
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
		String nodeName = event.node();
		Object content = content(event);
		return new ChatStreamResponse(nodeName, graphId, displayTitle(nodeName), content, siteInformation(event));
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

	private String displayTitle(String nodeName) {
		return switch (nodeName) {
			case "rewrite_multi_query" -> "Query Rewrite";
			case "coordinator" -> "Coordinator";
			case "background_investigator" -> "Background Investigation";
			case "human_feedback" -> "人工反馈";
			case "planner" -> "研究计划";
			case "plan_validator" -> "Plan Validator";
			case "information" -> "信息检索";
			case "research_team" -> "研究团队";
			case "researcher" -> "研究执行";
			case "processor" -> "信息整理";
			case "reporter" -> "报告生成";
			case "__END__" -> "结束";
			case "runner" -> "运行异常";
			default -> nodeName;
		};
	}

	private String eventName(ResearchEvent event) {
		if ("error".equals(event.phase())) {
			return "error";
		}
		if ("stopped".equals(event.phase())) {
			return "stopped";
		}
		return event.done() ? "done" : "message";
	}

}
