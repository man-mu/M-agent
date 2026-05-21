package top.lanshan.manmu.model;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record ResearchRequest(@NotBlank String query, String threadId, Integer maxSteps) {

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
	}

}
