package top.lanshan.manmu.graph;

import org.junit.jupiter.api.Test;
import top.lanshan.manmu.model.CoordinatorDecision;
import top.lanshan.manmu.model.CoordinatorRoute;
import top.lanshan.manmu.model.HumanFeedbackDecision;
import top.lanshan.manmu.model.HumanFeedbackRoute;
import top.lanshan.manmu.model.PlanValidatorDecision;
import top.lanshan.manmu.model.PlanValidatorRoute;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchTeamDecision;
import top.lanshan.manmu.model.ResearchTeamRoute;
import top.lanshan.manmu.model.StepType;
import top.lanshan.manmu.runner.ResumeDecision;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResearchGraphStateTest {

	@Test
	void createsGraphStateFromRequest() {
		ResearchRequest request = new ResearchRequest("Explain graph orchestration.", "thread-1", 2,
				4, true, false, 2);

		Map<String, Object> graphState = ResearchGraphState.from(request);

		assertThat(graphState.keySet()).containsExactly(ResearchGraphStateKeys.RESEARCH_STATE,
				ResearchGraphStateKeys.EVENTS);
		ResearchState state = ResearchGraphState.researchState(graphState);
		assertThat(state.threadId()).isEqualTo("thread-1");
		assertThat(state.sessionId()).isEqualTo("thread-1");
		assertThat(state.query()).isEqualTo("Explain graph orchestration.");
		assertThat(state.maxSteps()).isEqualTo(2);
		assertThat(state.optimizeQueryNum()).isEqualTo(4);
		assertThat(state.deepResearchEnabled()).isTrue();
		assertThat(state.autoAcceptedPlan()).isFalse();
		assertThat(state.maxPlanIterations()).isEqualTo(2);
		assertThat(ResearchGraphState.events(graphState)).isEmpty();
	}

	@Test
	void createsGraphStateFromChatSession() {
		ResearchRequest request = new ResearchRequest("Continue research.", "thread-2", 3);

		ResearchState state = ResearchGraphState.researchState(ResearchGraphState.from(request, "session-2"));

		assertThat(state.threadId()).isEqualTo("thread-2");
		assertThat(state.sessionId()).isEqualTo("session-2");
	}

	@Test
	void readsAndWritesResearchState() {
		Map<String, Object> graphState = new HashMap<>();
		ResearchState first = ResearchState.from(new ResearchRequest("First", "thread-a", 1));
		ResearchState second = ResearchState.from(new ResearchRequest("Second", "thread-b", 1));

		ResearchGraphState.setResearchState(graphState, first);
		assertThat(ResearchGraphState.researchState(graphState)).isSameAs(first);

		ResearchGraphState.setResearchState(graphState, second);
		assertThat(ResearchGraphState.researchState(graphState)).isSameAs(second);
	}

	@Test
	void appendsEventsInInsertionOrder() {
		Map<String, Object> graphState = ResearchGraphState.from(new ResearchRequest("Events", "thread-3", 1));
		ResearchEvent first = ResearchEvent.message("thread-3", "coordinator", "decision", "routed", null);
		ResearchEvent second = ResearchEvent.message("thread-3", "planner", "completed", "planned", null);
		ResearchEvent third = ResearchEvent.done("thread-3", "done", null);

		ResearchGraphState.appendEvent(graphState, first);
		ResearchGraphState.appendEvents(graphState, List.of(second, third));

		assertThat(ResearchGraphState.events(graphState)).containsExactly(first, second, third);
	}

	@Test
	void eventReadsAreDefensiveCopies() {
		Map<String, Object> graphState = ResearchGraphState.from(new ResearchRequest("Events", "thread-4", 1));
		ResearchEvent event = ResearchEvent.message("thread-4", "coordinator", "decision", "routed", null);
		ResearchGraphState.appendEvent(graphState, event);

		List<ResearchEvent> events = ResearchGraphState.events(graphState);

		assertThatThrownBy(() -> events.add(ResearchEvent.done("thread-4", "done", null)))
			.isInstanceOf(UnsupportedOperationException.class);
		assertThat(ResearchGraphState.events(graphState)).containsExactly(event);
	}

	@Test
	void savesReadsAndClearsResumeDecision() {
		Map<String, Object> graphState = ResearchGraphState.from(new ResearchRequest("Resume", "thread-5", 1));
		ResumeDecision decision = new ResumeDecision(false, "Focus on risks.");

		assertThat(ResearchGraphState.resumeDecision(graphState)).isEmpty();

		ResearchGraphState.resumeDecision(graphState, decision);
		assertThat(ResearchGraphState.resumeDecision(graphState)).contains(decision);

		ResearchGraphState.resumeDecision(graphState, null);
		assertThat(ResearchGraphState.resumeDecision(graphState)).isEmpty();
		assertThat(graphState).doesNotContainKey(ResearchGraphStateKeys.RESUME_DECISION);
	}

	@Test
	void savesReadsAndClearsTerminalStatus() {
		Map<String, Object> graphState = ResearchGraphState.from(new ResearchRequest("Status", "thread-6", 1));

		assertThat(ResearchGraphState.terminalStatus(graphState)).isEmpty();

		ResearchGraphState.terminalStatus(graphState, "COMPLETED");
		assertThat(ResearchGraphState.terminalStatus(graphState)).contains("COMPLETED");

		ResearchGraphState.terminalStatus(graphState, " ");
		assertThat(ResearchGraphState.terminalStatus(graphState)).isEmpty();
		assertThat(graphState).doesNotContainKey(ResearchGraphStateKeys.TERMINAL_STATUS);
	}

	@Test
	void readsRouteHelpersFromStoredResearchState() {
		Map<String, Object> graphState = ResearchGraphState.from(new ResearchRequest("Routes", "thread-7", 1));
		ResearchState state = ResearchGraphState.researchState(graphState);

		assertThat(ResearchGraphState.coordinatorRoute(graphState)).isEmpty();
		assertThat(ResearchGraphState.planValidatorRoute(graphState)).isEmpty();
		assertThat(ResearchGraphState.humanFeedbackRoute(graphState)).isEmpty();
		assertThat(ResearchGraphState.researchTeamRoute(graphState)).isEmpty();

		state.coordinatorDecision(new CoordinatorDecision(CoordinatorRoute.DEEP_RESEARCH, true, null,
				"Needs research."));
		state.planValidatorDecision(new PlanValidatorDecision(PlanValidatorRoute.HUMAN_FEEDBACK, true,
				1, 3, "valid"));
		state.humanFeedbackDecision(new HumanFeedbackDecision(HumanFeedbackRoute.PLANNER, false,
				"Add risks.", 1, 3, "feedback"));
		state.researchTeamDecision(new ResearchTeamDecision(ResearchTeamRoute.RESEARCHER,
				StepType.RESEARCH, 2, 0, 0, 2));

		assertThat(ResearchGraphState.coordinatorRoute(graphState)).contains(CoordinatorRoute.DEEP_RESEARCH);
		assertThat(ResearchGraphState.planValidatorRoute(graphState)).contains(PlanValidatorRoute.HUMAN_FEEDBACK);
		assertThat(ResearchGraphState.humanFeedbackRoute(graphState)).contains(HumanFeedbackRoute.PLANNER);
		assertThat(ResearchGraphState.researchTeamRoute(graphState)).contains(ResearchTeamRoute.RESEARCHER);
	}

	@Test
	void rejectsUnexpectedGraphStateValueTypes() {
		Map<String, Object> graphState = new HashMap<>();

		assertThatThrownBy(() -> ResearchGraphState.researchState(graphState))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("missing required value");

		graphState.put(ResearchGraphStateKeys.RESEARCH_STATE, "not-state");
		assertThatThrownBy(() -> ResearchGraphState.researchState(graphState))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("not a ResearchState");

		graphState.put(ResearchGraphStateKeys.EVENTS, List.of("not-event"));
		assertThatThrownBy(() -> ResearchGraphState.events(graphState))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("non-ResearchEvent");
	}

}
