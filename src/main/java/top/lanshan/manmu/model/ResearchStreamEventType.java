package top.lanshan.manmu.model;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum ResearchStreamEventType {

	GRAPH_STARTED("graph.started"),
	NODE_STARTED("node.started"),
	NODE_DELTA("node.delta"),
	NODE_COMPLETED("node.completed"),
	NODE_FAILED("node.failed"),
	PLAN_GENERATED("plan.generated"),
	HUMAN_FEEDBACK_WAITING("human_feedback.waiting"),
	HUMAN_FEEDBACK_ACCEPTED("human_feedback.accepted"),
	HUMAN_FEEDBACK_REJECTED("human_feedback.rejected"),
	REPORT_COMPLETED("report.completed"),
	GRAPH_COMPLETED("graph.completed"),
	GRAPH_STOPPED("graph.stopped"),
	GRAPH_FAILED("graph.failed");

	private final String value;

	ResearchStreamEventType(String value) {
		this.value = value;
	}

	@JsonValue
	public String value() {
		return value;
	}

	@JsonCreator
	public static ResearchStreamEventType fromValue(String value) {
		if (value == null || value.isBlank()) {
			return GRAPH_FAILED;
		}
		for (ResearchStreamEventType eventType : values()) {
			if (eventType.value.equals(value) || eventType.name().equalsIgnoreCase(value)) {
				return eventType;
			}
		}
		return GRAPH_FAILED;
	}

	public static ResearchStreamEventType from(ResearchEvent event) {
		if (event == null) {
			return GRAPH_FAILED;
		}
		String node = event.node();
		String phase = event.phase();
		if ("__END__".equals(node)) {
			if ("stopped".equals(phase)) {
				return GRAPH_STOPPED;
			}
			if ("error".equals(phase) || event.failed()) {
				return GRAPH_FAILED;
			}
			return GRAPH_COMPLETED;
		}
		if ("runner".equals(node) && ("error".equals(phase) || event.done())) {
			return GRAPH_FAILED;
		}
		if ("human_feedback".equals(node)) {
			if ("waiting".equals(phase)) {
				return HUMAN_FEEDBACK_WAITING;
			}
			if ("decision".equals(phase) && event.payload() instanceof HumanFeedbackDecision decision) {
				if (Boolean.TRUE.equals(decision.accepted())) {
					return HUMAN_FEEDBACK_ACCEPTED;
				}
				if (Boolean.FALSE.equals(decision.accepted())) {
					return HUMAN_FEEDBACK_REJECTED;
				}
			}
		}
		if ("planner".equals(node) && "completed".equals(phase)) {
			return PLAN_GENERATED;
		}
		if ("reporter".equals(node) && "completed".equals(phase)) {
			return REPORT_COMPLETED;
		}
		if ("started".equals(phase)) {
			return NODE_STARTED;
		}
		if ("completed".equals(phase)) {
			return NODE_COMPLETED;
		}
		if ("failed".equals(phase) || "error".equals(phase) || event.failed()) {
			return NODE_FAILED;
		}
		return NODE_DELTA;
	}

}
