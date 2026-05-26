package top.lanshan.manmu.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record ResearchRequest(@NotBlank String query, @JsonProperty("session_id") String sessionId, String threadId, Integer maxSteps,
		@JsonProperty("optimize_query_num") Integer optimizeQueryNum,
		@JsonProperty("enable_deepresearch") Boolean enableDeepResearch,
		@JsonProperty("auto_accepted_plan") Boolean autoAcceptedPlan,
		@JsonProperty("max_plan_iterations") Integer maxPlanIterations) {

	public ResearchRequest(String query, String threadId, Integer maxSteps) {
		this(query, null, threadId, maxSteps, null, null, null, null);
	}

	public ResearchRequest(String query, String threadId, Integer maxSteps,
			@JsonProperty("optimize_query_num") Integer optimizeQueryNum) {
		this(query, null, threadId, maxSteps, optimizeQueryNum, null, null, null);
	}

	public ResearchRequest(String query, String threadId, Integer maxSteps,
			@JsonProperty("optimize_query_num") Integer optimizeQueryNum,
			@JsonProperty("enable_deepresearch") Boolean enableDeepResearch) {
		this(query, null, threadId, maxSteps, optimizeQueryNum, enableDeepResearch, null, null);
	}

	public ResearchRequest(String query, String threadId, Integer maxSteps,
			@JsonProperty("optimize_query_num") Integer optimizeQueryNum,
			@JsonProperty("enable_deepresearch") Boolean enableDeepResearch,
			@JsonProperty("auto_accepted_plan") Boolean autoAcceptedPlan) {
		this(query, null, threadId, maxSteps, optimizeQueryNum, enableDeepResearch, autoAcceptedPlan, null);
	}

	public ResearchRequest(String query, String threadId, Integer maxSteps,
			@JsonProperty("optimize_query_num") Integer optimizeQueryNum,
			@JsonProperty("enable_deepresearch") Boolean enableDeepResearch,
			@JsonProperty("auto_accepted_plan") Boolean autoAcceptedPlan,
			@JsonProperty("max_plan_iterations") Integer maxPlanIterations) {
		this(query, null, threadId, maxSteps, optimizeQueryNum, enableDeepResearch, autoAcceptedPlan, maxPlanIterations);
	}

	public ResearchRequest {
		if (threadId == null || threadId.isBlank()) {
			threadId = UUID.randomUUID().toString();
		}
		if (maxSteps == null || maxSteps < 1) {
			maxSteps = 3;
		}
		if (maxSteps > 6) {
			maxSteps = 6;
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
		if (enableDeepResearch == null) {
			enableDeepResearch = true;
		}
		if (autoAcceptedPlan == null) {
			autoAcceptedPlan = true;
		}
		if (maxPlanIterations == null || maxPlanIterations < 1) {
			maxPlanIterations = 3;
		}
		if (maxPlanIterations > 5) {
			maxPlanIterations = 5;
		}
	}

}
