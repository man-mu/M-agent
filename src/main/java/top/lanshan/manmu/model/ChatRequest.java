package top.lanshan.manmu.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
		@JsonProperty("session_id") String sessionId,
		@JsonProperty("thread_id") String threadId,
		@JsonProperty("max_step_num") Integer maxStepNum,
		@JsonProperty("auto_accepted_plan") Boolean autoAcceptedPlan,
		@JsonProperty("enable_deepresearch") Boolean enableDeepResearch,
		@NotBlank String query) {

	public ChatRequest {
		if (sessionId == null || sessionId.isBlank()) {
			sessionId = "__default__";
		}
		if (maxStepNum == null || maxStepNum < 1) {
			maxStepNum = 3;
		}
		if (maxStepNum > 6) {
			maxStepNum = 6;
		}
		if (autoAcceptedPlan == null) {
			autoAcceptedPlan = true;
		}
		if (enableDeepResearch == null) {
			enableDeepResearch = true;
		}
	}

}
