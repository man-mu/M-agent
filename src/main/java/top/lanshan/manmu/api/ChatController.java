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
import top.lanshan.manmu.model.ChatRequest;
import top.lanshan.manmu.model.ChatStreamResponse;
import top.lanshan.manmu.model.GraphId;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.runner.SimpleResearchRunner;

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
		ResearchRequest researchRequest = new ResearchRequest(request.query(), graphId.threadId(), request.maxStepNum());

		return runner.run(researchRequest).map(event -> ServerSentEvent.<ChatStreamResponse>builder()
			.id(graphId.threadId())
			.event(eventName(event))
			.data(toResponse(graphId, event))
			.build());
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
		return new ChatStreamResponse(nodeName, graphId, displayTitle(nodeName), content, List.of());
	}

	private Object content(ResearchEvent event) {
		if ("error".equals(event.phase())) {
			return Map.of("reason", event.content());
		}
		if (event.done()) {
			return Map.of("output", event.payload(), "done", true);
		}
		return event.payload() == null ? event.content() : event.payload();
	}

	private String displayTitle(String nodeName) {
		return switch (nodeName) {
			case "planner" -> "研究计划";
			case "researcher" -> "研究执行";
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
		return event.done() ? "done" : "message";
	}

}
