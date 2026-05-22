package top.lanshan.manmu.node;

import org.junit.jupiter.api.Test;
import top.lanshan.manmu.model.HumanFeedbackDecision;
import top.lanshan.manmu.model.HumanFeedbackRoute;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.StepType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HumanFeedbackNodeTest {

	private final HumanFeedbackNode node = new HumanFeedbackNode();

	@Test
	void waitsWhenNoFeedbackHasArrived() {
		ResearchState state = state(3);

		var events = node.run(state).collectList().block();

		assertThat(events).isNotNull();
		assertThat(events).singleElement().satisfies(event -> {
			assertThat(event.node()).isEqualTo("human_feedback");
			assertThat(event.phase()).isEqualTo("waiting");
			assertThat(event.payload()).isInstanceOf(ResearchPlan.class);
		});
		assertThat(state.humanFeedbackDecision().nextRoute()).isEqualTo(HumanFeedbackRoute.WAITING);
	}

	@Test
	void acceptedFeedbackRoutesToResearchTeam() {
		ResearchState state = state(3);
		state.humanFeedback(true, null);

		node.run(state).collectList().block();

		HumanFeedbackDecision decision = state.humanFeedbackDecision();
		assertThat(decision.nextRoute()).isEqualTo(HumanFeedbackRoute.RESEARCH_TEAM);
		assertThat(decision.accepted()).isTrue();
		assertThat(state.humanFeedbackAccepted()).isNull();
	}

	@Test
	void rejectedFeedbackRoutesToPlannerAndStoresFeedback() {
		ResearchState state = state(3);
		state.humanFeedback(false, " Focus on risks. ");

		node.run(state).collectList().block();

		HumanFeedbackDecision decision = state.humanFeedbackDecision();
		assertThat(decision.nextRoute()).isEqualTo(HumanFeedbackRoute.PLANNER);
		assertThat(decision.feedbackContent()).isEqualTo("Focus on risks.");
		assertThat(state.planFeedback()).isEqualTo("Focus on risks.");
	}

	@Test
	void rejectedFeedbackRoutesToResearchTeamAtRetryLimit() {
		ResearchState state = state(1);
		state.humanFeedback(false, "Try again.");

		node.run(state).collectList().block();

		HumanFeedbackDecision decision = state.humanFeedbackDecision();
		assertThat(decision.nextRoute()).isEqualTo(HumanFeedbackRoute.RESEARCH_TEAM);
		assertThat(decision.accepted()).isFalse();
		assertThat(decision.reason()).isEqualTo("Maximum plan iterations reached");
	}

	private ResearchState state(int maxPlanIterations) {
		ResearchState state = ResearchState.from(new ResearchRequest("Explain routing.", "thread-1", 2,
				null, true, false, maxPlanIterations));
		state.recordPlanAttempt();
		state.plan(new ResearchPlan("Plan", true, "Use a small plan.",
				List.of(new ResearchStep("Explain coordinator", "Describe the routing decision.", false,
						StepType.RESEARCH, null, ResearchStep.STATUS_PENDING))));
		return state;
	}

}
