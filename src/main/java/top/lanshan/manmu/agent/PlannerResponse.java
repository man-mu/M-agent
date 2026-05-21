package top.lanshan.manmu.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import top.lanshan.manmu.model.StepType;

import java.util.List;

public record PlannerResponse(@JsonProperty(required = true) String title,
		@JsonProperty(required = true) List<Step> steps) {

	public record Step(@JsonProperty(required = true) String title,
			@JsonProperty(required = true) String description, StepType type) {
	}

}
