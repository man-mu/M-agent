package top.lanshan.manmu.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FeedbackRequest(
		@JsonProperty("session_id") String sessionId,
		@JsonProperty("thread_id") @NotBlank String threadId,
		@NotNull Boolean feedback,
		@JsonProperty("feedback_content") String feedbackContent) {

	public FeedbackRequest {
		if (sessionId == null || sessionId.isBlank()) {
			sessionId = "__default__";
		}
	}

}
