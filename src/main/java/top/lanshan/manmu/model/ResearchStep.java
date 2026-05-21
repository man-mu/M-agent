package top.lanshan.manmu.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ResearchStep {

	public static final String STATUS_PENDING = "pending";

	public static final String STATUS_PROCESSING = "processing";

	public static final String STATUS_COMPLETED = "completed";

	public static final String STATUS_ERROR = "error";

	private String title;

	private String description;

	@JsonProperty("need_web_search")
	private boolean needWebSearch;

	@JsonProperty("step_type")
	private StepType stepType;

	private String executionRes;

	private String executionStatus;

	private StepSearchContext searchContext;

	public ResearchStep(String title, String description, StepType stepType) {
		this(title, description, false, stepType, null, STATUS_PENDING);
	}

	public ResearchStep(String title, String description, boolean needWebSearch, StepType stepType,
			String executionRes, String executionStatus) {
		this.title = title;
		this.description = description;
		this.needWebSearch = needWebSearch;
		this.stepType = stepType == null ? StepType.RESEARCH : stepType;
		this.executionRes = executionRes;
		this.executionStatus = executionStatus == null || executionStatus.isBlank() ? STATUS_PENDING : executionStatus;
	}

	public String title() {
		return title;
	}

	public void title(String title) {
		this.title = title;
	}

	public String description() {
		return description;
	}

	public void description(String description) {
		this.description = description;
	}

	public boolean needWebSearch() {
		return needWebSearch;
	}

	public void needWebSearch(boolean needWebSearch) {
		this.needWebSearch = needWebSearch;
	}

	public StepType stepType() {
		return stepType;
	}

	public StepType type() {
		return stepType;
	}

	public void stepType(StepType stepType) {
		this.stepType = stepType == null ? StepType.RESEARCH : stepType;
	}

	public String executionRes() {
		return executionRes;
	}

	public void executionRes(String executionRes) {
		this.executionRes = executionRes;
	}

	public String executionStatus() {
		return executionStatus;
	}

	public void executionStatus(String executionStatus) {
		this.executionStatus = executionStatus;
	}

	public StepSearchContext searchContext() {
		return searchContext;
	}

	public void searchContext(StepSearchContext searchContext) {
		this.searchContext = searchContext;
	}

}
