package top.lanshan.manmu.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ResearchEvent(@JsonProperty("thread_id") String threadId,
		@JsonProperty("sequence") Long sequence,
		@JsonProperty("event_type") ResearchStreamEventType eventType,
		@JsonProperty("node") String node,
		@JsonProperty("node_name") String nodeName,
		@JsonProperty("node_type") String nodeType,
		@JsonProperty("executor_id") Integer executorId,
		@JsonProperty("step_id") String stepId,
		@JsonProperty("phase") String phase,
		@JsonProperty("status") String status,
		@JsonProperty("display_title") String displayTitle,
		@JsonProperty("content") String content,
		@JsonProperty("payload") Object payload,
		@JsonProperty("site_information") Object siteInformation,
		@JsonProperty("done") boolean done,
		@JsonProperty("timestamp") Instant timestamp) {

	public ResearchEvent(String threadId, String node, String phase, String content, Object payload, boolean done,
			Instant timestamp) {
		this(threadId, null, null, node, node, null, null, null, phase, phase, null, content, payload, null, done,
				timestamp);
	}

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

	public boolean failed() {
		return "failed".equals(phase) || "error".equals(phase);
	}

	@JsonProperty("threadId")
	public String legacyThreadId() {
		return threadId;
	}

	public ResearchEvent withSequence(long sequence) {
		ResearchNodeMetadata metadata = ResearchNodeMetadata.from(node);
		ResearchStreamEventType resolvedEventType = ResearchStreamEventType.from(this);
		return new ResearchEvent(threadId, sequence, resolvedEventType, node, metadata.nodeName(), metadata.nodeType(),
				metadata.executorId(), stepId, phase, status == null ? phase : status, metadata.displayTitle(),
				content, payload, siteInformation, done, timestamp);
	}

}
