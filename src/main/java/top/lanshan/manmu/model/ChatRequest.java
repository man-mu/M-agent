package top.lanshan.manmu.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
		@JsonProperty("session_id") String sessionId,
		@JsonProperty("thread_id") String threadId,
		@JsonProperty("max_step_num") Integer maxStepNum,
		@JsonProperty("optimize_query_num") Integer optimizeQueryNum,
		@JsonProperty("auto_accepted_plan") Boolean autoAcceptedPlan,
		@JsonProperty("enable_deepresearch") Boolean enableDeepResearch,
		@NotBlank String query) {

	public ChatRequest(String sessionId, String threadId, Integer maxStepNum, Boolean autoAcceptedPlan,
			Boolean enableDeepResearch, String query) {
		this(sessionId, threadId, maxStepNum, null, autoAcceptedPlan, enableDeepResearch, query);
	}

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
		if (optimizeQueryNum == null) {
			optimizeQueryNum = 3;
		}
		if (optimizeQueryNum < 0) {
			optimizeQueryNum = 0;
		}
		if (optimizeQueryNum > 5) {
			optimizeQueryNum = 5;
		}
		if (autoAcceptedPlan == null) {
			autoAcceptedPlan = true;
		}
		if (enableDeepResearch == null) {
			enableDeepResearch = true;
		}
	}

}
