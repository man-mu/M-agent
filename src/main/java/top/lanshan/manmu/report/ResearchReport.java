package top.lanshan.manmu.report;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ResearchReport(
		@JsonProperty("thread_id") String threadId,
		@JsonProperty("session_id") String sessionId,
		String query,
		String report,
		String status,
		@JsonProperty("error_message") String errorMessage,
		@JsonProperty("created_at") Instant createdAt,
		@JsonProperty("updated_at") Instant updatedAt) {
}
