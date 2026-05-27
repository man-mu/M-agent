package top.lanshan.manmu.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import top.lanshan.manmu.model.StepType;

import java.util.List;

public class PlannerResponse {

	private String title;

	@JsonProperty("has_enough_context")
	private Boolean hasEnoughContext;

	private String thought;

	@JsonProperty(required = true)
	private List<Step> steps;

	public PlannerResponse() {
	}

	public PlannerResponse(String title, Boolean hasEnoughContext, String thought, List<Step> steps) {
		this.title = title;
		this.hasEnoughContext = hasEnoughContext;
		this.thought = thought;
		this.steps = steps;
	}

	public String title() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public Boolean hasEnoughContext() {
		return hasEnoughContext;
	}

	public void setHasEnoughContext(Boolean hasEnoughContext) {
		this.hasEnoughContext = hasEnoughContext;
	}

	public String thought() {
		return thought;
	}

	public void setThought(String thought) {
		this.thought = thought;
	}

	public List<Step> steps() {
		return steps;
	}

	public void setSteps(List<Step> steps) {
		this.steps = steps;
	}

	public static class Step {

		@JsonProperty(required = true)
		private String title;

		@JsonProperty(required = true)
		private String description;

		@JsonProperty("need_web_search")
		private Boolean needWebSearch;

		@JsonProperty("step_type")
		private StepType stepType;

		private StepType type;

		public Step() {
		}

		public Step(String title, String description, Boolean needWebSearch, StepType stepType, StepType type) {
			this.title = title;
			this.description = description;
			this.needWebSearch = needWebSearch;
			this.stepType = stepType;
			this.type = type;
		}

		public String title() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public String description() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		public Boolean needWebSearch() {
			return needWebSearch;
		}

		public void setNeedWebSearch(Boolean needWebSearch) {
			this.needWebSearch = needWebSearch;
		}

		public StepType stepType() {
			return stepType;
		}

		public void setStepType(StepType stepType) {
			this.stepType = stepType;
		}

		public StepType type() {
			return type;
		}

		public void setType(StepType type) {
			this.type = type;
		}

	}

}
