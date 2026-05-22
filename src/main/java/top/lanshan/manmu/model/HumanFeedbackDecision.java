package top.lanshan.manmu.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HumanFeedbackDecision(@JsonProperty("next_route") HumanFeedbackRoute nextRoute,
		Boolean accepted,
		@JsonProperty("feedback_content") String feedbackContent,
		@JsonProperty("plan_iterations") int planIterations,
		@JsonProperty("max_plan_iterations") int maxPlanIterations,
		String reason) {
}
