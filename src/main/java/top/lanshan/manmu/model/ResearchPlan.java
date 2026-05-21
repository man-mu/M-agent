package top.lanshan.manmu.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ResearchPlan(String title,
		@JsonProperty("has_enough_context") boolean hasEnoughContext,
		String thought,
		List<ResearchStep> steps) {
}
