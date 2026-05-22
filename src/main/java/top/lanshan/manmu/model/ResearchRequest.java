package top.lanshan.manmu.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record ResearchRequest(@NotBlank String query, String threadId, Integer maxSteps,
		@JsonProperty("optimize_query_num") Integer optimizeQueryNum) {

	public ResearchRequest(String query, String threadId, Integer maxSteps) {
		this(query, threadId, maxSteps, null);
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
	}

}
