package top.lanshan.manmu.api;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchStreamEventType;
import top.lanshan.manmu.runner.ResearchRunner;

@RestController
@RequestMapping("/api/research")
public class ResearchController {

	private final ResearchRunner runner;

	public ResearchController(ResearchRunner runner) {
		this.runner = runner;
	}

	@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<ResearchEvent>> stream(@Valid @RequestBody ResearchRequest request) {
		return runner.run(request).map(event -> ServerSentEvent.<ResearchEvent>builder()
			.id(event.threadId())
			.event(eventName(event))
			.data(event)
			.build());
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
