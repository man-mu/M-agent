package top.lanshan.manmu.api;

import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.runner.ResearchRunner;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/research")
public class ResearchController {

	private final ResearchRunner runner;

	public ResearchController(ResearchRunner runner) {
		this.runner = runner;
	}

	@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<ResearchEvent>> stream(@Valid @RequestBody ResearchRequest request) {
		// 这里返回的是 Flux，而不是普通对象。可以先把它理解成“未来会陆续产生多个
		// ResearchEvent 的数据管道”。Controller 创建这条管道后立即交给 Spring WebFlux，
		// WebFlux 会在 HTTP 连接建立后订阅它，并把每个事件写成一段 SSE 响应。
		//
		// 关键点：调用 runner.run(request) 本身不会阻塞等待完整报告生成。真正的执行由
		// WebFlux 对 Flux 的订阅驱动，数据一边产生，一边通过 text/event-stream 推给客户端。
		return runner.run(request).map(event -> ServerSentEvent.<ResearchEvent>builder()
			// SSE 的 id 通常用于前端识别同一个会话或断线重连，这里直接使用 threadId。
			.id(event.threadId())
			// event 字段是前端 EventSource / fetch 流式读取时看到的事件类型。
			.event(eventName(event))
			// data 字段是真正发给前端的 JSON 内容，也就是 ResearchEvent 本身。
			.data(event)

			.build());
	}

	private String eventName(ResearchEvent event) {
		// HTTP 连接始终是同一个，但每条 SSE 可以有不同事件名。这样前端可以分别处理
		// 普通进度、结束、错误，而不用只靠 data 里的字段猜测状态。
		if ("error".equals(event.phase())) {
			return "error";
		}
		return event.done() ? "done" : "message";
	}

}
