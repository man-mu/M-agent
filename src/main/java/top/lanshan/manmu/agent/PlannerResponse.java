package top.lanshan.manmu.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import top.lanshan.manmu.model.StepType;

import java.util.List;

public record PlannerResponse(@JsonProperty(required = true) String title,
		@JsonProperty("has_enough_context") Boolean hasEnoughContext,
		String thought,
		@JsonProperty(required = true) List<Step> steps) {

	public record Step(@JsonProperty(required = true) String title,
			@JsonProperty(required = true) String description,
			@JsonProperty("need_web_search") Boolean needWebSearch,
			@JsonProperty("step_type") StepType stepType,
			StepType type) {
	}

}
