package top.lanshan.manmu.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public class ResearchStep {

	public static final String STATUS_PENDING = "pending";

	public static final String STATUS_PROCESSING = "processing";

	public static final String STATUS_COMPLETED = "completed";

	public static final String STATUS_ERROR = "error";

	@JsonProperty("id")
	private String id;

	private String title;

	private String description;

	@JsonProperty("need_web_search")
	private boolean needWebSearch;

	@JsonProperty("step_type")
	private StepType stepType;

	@JsonProperty("assigned_node")
	private String assignedNode;

	@JsonProperty("attempt")
	private int attempt;

	@JsonProperty("error")
	private String error;

	@JsonProperty("started_at")
	private Instant startedAt;

	@JsonProperty("completed_at")
	private Instant completedAt;

	@JsonProperty("execution_res")
	private String executionRes;

	@JsonProperty("execution_status")
	private String executionStatus;

	@JsonProperty("search_context")
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
		this.executionStatus = StepExecutionStatus.normalize(executionStatus);
	}

	public ResearchStep copy() {
		ResearchStep copy = new ResearchStep(title, description, needWebSearch, stepType, executionRes,
				executionStatus);
		copy.id(id);
		copy.assignedNode(assignedNode);
		copy.attempt(attempt);
		copy.error(error);
		copy.startedAt(startedAt);
		copy.completedAt(completedAt);
		copy.searchContext(searchContext);
		return copy;
	}

	public String id() {
		return id;
	}

	public void id(String id) {
		this.id = id;
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

	public String assignedNode() {
		return assignedNode;
	}

	public void assignedNode(String assignedNode) {
		this.assignedNode = assignedNode;
	}

	public int attempt() {
		return attempt;
	}

	public void attempt(int attempt) {
		this.attempt = Math.max(0, attempt);
	}

	public void incrementAttempt() {
		this.attempt++;
	}

	public String error() {
		return error;
	}

	public void error(String error) {
		this.error = error;
	}

	public Instant startedAt() {
		return startedAt;
	}

	public void startedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public Instant completedAt() {
		return completedAt;
	}

	public void completedAt(Instant completedAt) {
		this.completedAt = completedAt;
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
		this.executionStatus = StepExecutionStatus.normalize(executionStatus);
	}

	public StepSearchContext searchContext() {
		return searchContext;
	}

	public void searchContext(StepSearchContext searchContext) {
		this.searchContext = searchContext;
	}

}
