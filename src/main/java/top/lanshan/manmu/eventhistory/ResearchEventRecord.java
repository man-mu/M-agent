package top.lanshan.manmu.eventhistory;

import com.fasterxml.jackson.annotation.JsonProperty;
import top.lanshan.manmu.model.ChatStreamResponse;

import java.time.Instant;

public record ResearchEventRecord(
		@JsonProperty("session_id") String sessionId,
		@JsonProperty("thread_id") String threadId,
		Long sequence,
		@JsonProperty("event_type") String eventType,
		@JsonProperty("node_name") String nodeName,
		@JsonProperty("node_type") String nodeType,
		@JsonProperty("executor_id") Integer executorId,
		@JsonProperty("step_id") String stepId,
		String phase,
		String status,
		@JsonProperty("display_title") String displayTitle,
		boolean done,
		@JsonProperty("event_timestamp") Instant eventTimestamp,
		@JsonProperty("created_at") Instant createdAt,
		ChatStreamResponse event) {
}
