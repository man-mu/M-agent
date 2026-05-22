package top.lanshan.manmu.graph;

import top.lanshan.manmu.model.CoordinatorRoute;
import top.lanshan.manmu.model.HumanFeedbackRoute;
import top.lanshan.manmu.model.PlanValidatorRoute;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchTeamRoute;
import top.lanshan.manmu.runner.ResumeDecision;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ResearchGraphState {

	private ResearchGraphState() {
	}

	public static Map<String, Object> from(ResearchRequest request) {
		return from(ResearchState.from(request));
	}

	public static Map<String, Object> from(ResearchRequest request, String sessionId) {
		return from(ResearchState.from(request, sessionId));
	}

	public static Map<String, Object> from(ResearchState state) {
		Map<String, Object> graphState = new LinkedHashMap<>();
		setResearchState(graphState, state);
		graphState.put(ResearchGraphStateKeys.EVENTS, new ArrayList<ResearchEvent>());
		return graphState;
	}

	public static ResearchState researchState(Map<String, Object> graphState) {
		Object state = requiredValue(graphState, ResearchGraphStateKeys.RESEARCH_STATE);
		if (state instanceof ResearchState researchState) {
			return researchState;
		}
		throw new IllegalStateException("Graph state value '" + ResearchGraphStateKeys.RESEARCH_STATE
				+ "' is not a ResearchState");
	}

	public static void setResearchState(Map<String, Object> graphState, ResearchState state) {
		Objects.requireNonNull(graphState, "graphState must not be null");
		graphState.put(ResearchGraphStateKeys.RESEARCH_STATE, Objects.requireNonNull(state,
				"state must not be null"));
	}

	public static void appendEvent(Map<String, Object> graphState, ResearchEvent event) {
		eventsForAppend(graphState).add(Objects.requireNonNull(event, "event must not be null"));
	}

	public static void appendEvents(Map<String, Object> graphState, Collection<ResearchEvent> events) {
		Objects.requireNonNull(events, "events must not be null");
		events.forEach(event -> appendEvent(graphState, event));
	}

	public static List<ResearchEvent> events(Map<String, Object> graphState) {
		return List.copyOf(eventsForAppend(graphState));
	}

	public static void resumeDecision(Map<String, Object> graphState, ResumeDecision decision) {
		Objects.requireNonNull(graphState, "graphState must not be null");
		if (decision == null) {
			graphState.remove(ResearchGraphStateKeys.RESUME_DECISION);
			return;
		}
		graphState.put(ResearchGraphStateKeys.RESUME_DECISION, decision);
	}

	public static Optional<ResumeDecision> resumeDecision(Map<String, Object> graphState) {
		Object decision = optionalValue(graphState, ResearchGraphStateKeys.RESUME_DECISION);
		if (decision == null) {
			return Optional.empty();
		}
		if (decision instanceof ResumeDecision resumeDecision) {
			return Optional.of(resumeDecision);
		}
		throw new IllegalStateException("Graph state value '" + ResearchGraphStateKeys.RESUME_DECISION
				+ "' is not a ResumeDecision");
	}

	public static void terminalStatus(Map<String, Object> graphState, String status) {
		Objects.requireNonNull(graphState, "graphState must not be null");
		if (status == null || status.isBlank()) {
			graphState.remove(ResearchGraphStateKeys.TERMINAL_STATUS);
			return;
		}
		graphState.put(ResearchGraphStateKeys.TERMINAL_STATUS, status);
	}

	public static Optional<String> terminalStatus(Map<String, Object> graphState) {
		Object status = optionalValue(graphState, ResearchGraphStateKeys.TERMINAL_STATUS);
		if (status == null) {
			return Optional.empty();
		}
		if (status instanceof String terminalStatus) {
			return Optional.of(terminalStatus);
		}
		throw new IllegalStateException("Graph state value '" + ResearchGraphStateKeys.TERMINAL_STATUS
				+ "' is not a String");
	}

	public static Optional<CoordinatorRoute> coordinatorRoute(Map<String, Object> graphState) {
		ResearchState state = researchState(graphState);
		return state.coordinatorDecision() == null ? Optional.empty()
				: Optional.of(state.coordinatorDecision().nextRoute());
	}

	public static Optional<PlanValidatorRoute> planValidatorRoute(Map<String, Object> graphState) {
		ResearchState state = researchState(graphState);
		return state.planValidatorDecision() == null ? Optional.empty()
				: Optional.of(state.planValidatorDecision().nextRoute());
	}

	public static Optional<HumanFeedbackRoute> humanFeedbackRoute(Map<String, Object> graphState) {
		ResearchState state = researchState(graphState);
		return state.humanFeedbackDecision() == null ? Optional.empty()
				: Optional.of(state.humanFeedbackDecision().nextRoute());
	}

	public static Optional<ResearchTeamRoute> researchTeamRoute(Map<String, Object> graphState) {
		ResearchState state = researchState(graphState);
		return state.researchTeamDecision() == null ? Optional.empty()
				: Optional.of(state.researchTeamDecision().nextRoute());
	}

	@SuppressWarnings("unchecked")
	private static List<ResearchEvent> eventsForAppend(Map<String, Object> graphState) {
		Objects.requireNonNull(graphState, "graphState must not be null");
		Object events = graphState.computeIfAbsent(ResearchGraphStateKeys.EVENTS,
				key -> new ArrayList<ResearchEvent>());
		if (events instanceof List<?> eventList) {
			for (Object event : eventList) {
				if (!(event instanceof ResearchEvent)) {
					throw new IllegalStateException("Graph state value '" + ResearchGraphStateKeys.EVENTS
							+ "' contains a non-ResearchEvent value");
				}
			}
			return (List<ResearchEvent>) eventList;
		}
		throw new IllegalStateException("Graph state value '" + ResearchGraphStateKeys.EVENTS
				+ "' is not a List");
	}

	private static Object requiredValue(Map<String, Object> graphState, String key) {
		Objects.requireNonNull(graphState, "graphState must not be null");
		Object value = graphState.get(key);
		if (value == null) {
			throw new IllegalStateException("Graph state is missing required value '" + key + "'");
		}
		return value;
	}

	private static Object optionalValue(Map<String, Object> graphState, String key) {
		Objects.requireNonNull(graphState, "graphState must not be null");
		return graphState.get(key);
	}

}
