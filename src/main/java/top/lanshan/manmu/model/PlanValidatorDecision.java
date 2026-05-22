package top.lanshan.manmu.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PlanValidatorDecision(@JsonProperty("next_route") PlanValidatorRoute nextRoute, boolean valid,
		@JsonProperty("plan_iterations") int planIterations,
		@JsonProperty("max_plan_iterations") int maxPlanIterations,
		String reason) {
}
