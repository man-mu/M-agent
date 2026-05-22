package top.lanshan.manmu.sessionhistory;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ResearchSessionHistory(
		@JsonProperty("thread_id") String threadId,
		@JsonProperty("session_id") String sessionId,
		String query,
		String status,
		@JsonProperty("report_thread_id") String reportThreadId,
		@JsonProperty("error_message") String errorMessage,
		@JsonProperty("created_at") Instant createdAt,
		@JsonProperty("updated_at") Instant updatedAt,
		@JsonProperty("completed_at") Instant completedAt,
		@JsonProperty("stopped_at") Instant stoppedAt) {
}
