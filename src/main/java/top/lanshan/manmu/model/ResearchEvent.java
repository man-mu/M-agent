package top.lanshan.manmu.model;

import java.time.Instant;

public record ResearchEvent(String threadId, String node, String phase, String content, Object payload, boolean done,
		Instant timestamp) {

	public static ResearchEvent message(String threadId, String node, String phase, String content, Object payload) {
		return new ResearchEvent(threadId, node, phase, content, payload, false, Instant.now());
	}

	public static ResearchEvent done(String threadId, String content, Object payload) {
		return new ResearchEvent(threadId, "__END__", "completed", content, payload, true, Instant.now());
	}

	public static ResearchEvent stopped(String threadId, String content) {
		return new ResearchEvent(threadId, "__END__", "stopped", content, null, true, Instant.now());
	}

	public static ResearchEvent error(String threadId, String node, Throwable throwable) {
		return new ResearchEvent(threadId, node, "error", errorMessage(throwable), null, true, Instant.now());
	}

	private static String errorMessage(Throwable throwable) {
		if (throwable == null) {
			return "Unknown error";
		}
		if (throwable.getMessage() != null && !throwable.getMessage().isBlank()) {
			return throwable.getMessage();
		}
		return throwable.getClass().getSimpleName();
	}

}
