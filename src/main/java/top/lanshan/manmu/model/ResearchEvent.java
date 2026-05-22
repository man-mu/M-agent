package top.lanshan.manmu.model;

import java.time.Instant;

// ResearchEvent 是对外输出的“进度事件”。它和 ResearchState 的区别是：
// - ResearchState 是内部共享状态，节点之间用它传递上下文；
// - ResearchEvent 是外部可观察消息，Controller 会把它包装成 SSE 推给前端。
public record ResearchEvent(String threadId, String node, String phase, String content, Object payload, boolean done,
		Instant timestamp) {

	public static ResearchEvent message(String threadId, String node, String phase, String content, Object payload) {
		// 普通进度事件，例如 planner started、researcher step_completed。
		// payload 可以放结构化数据，方便前端不仅展示文本，也能渲染计划、搜索结果等对象。
		return new ResearchEvent(threadId, node, phase, content, payload, false, Instant.now());
	}

	public static ResearchEvent done(String threadId, String content, Object payload) {
		// done 事件表示整个工作流结束。这里用 __END__ 作为虚拟节点名，方便测试和前端识别。
		return new ResearchEvent(threadId, "__END__", "completed", content, payload, true, Instant.now());
	}

	public static ResearchEvent stopped(String threadId, String content) {
		return new ResearchEvent(threadId, "__END__", "stopped", content, null, true, Instant.now());
	}

	public static ResearchEvent error(String threadId, String node, Throwable throwable) {
		// error 事件也是一条正常 SSE 消息。这样前端能收到结构化错误，而不是只看到连接中断。
		return new ResearchEvent(threadId, node, "error", throwable.getMessage(), null, true, Instant.now());
	}

}
