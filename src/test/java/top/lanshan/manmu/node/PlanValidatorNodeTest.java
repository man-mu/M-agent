package top.lanshan.manmu.node;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import top.lanshan.manmu.model.PlanValidatorDecision;
import top.lanshan.manmu.model.PlanValidatorRoute;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.StepType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlanValidatorNodeTest {

	private final PlanValidatorNode node = new PlanValidatorNode();

	@Test
	void routesValidAutoAcceptedPlanToResearchTeam() {
		ResearchState state = stateWithValidPlan(true);
		state.recordPlanAttempt();

		StepVerifier.create(node.run(state)).assertNext(event -> {
			assertThat(event.node()).isEqualTo("plan_validator");
			assertThat(event.phase()).isEqualTo("decision");
			PlanValidatorDecision decision = (PlanValidatorDecision) event.payload();
			assertThat(decision.nextRoute()).isEqualTo(PlanValidatorRoute.RESEARCH_TEAM);
			assertThat(decision.valid()).isTrue();
			assertThat(decision.planIterations()).isEqualTo(1);
		}).verifyComplete();

		assertThat(state.planValidatorDecision().nextRoute()).isEqualTo(PlanValidatorRoute.RESEARCH_TEAM);
	}

	@Test
	void routesValidManualPlanToHumanFeedback() {
		ResearchState state = stateWithValidPlan(false);
		state.recordPlanAttempt();

		node.run(state).collectList().block();

		assertThat(state.planValidatorDecision().nextRoute()).isEqualTo(PlanValidatorRoute.HUMAN_FEEDBACK);
		assertThat(state.planValidatorDecision().valid()).isTrue();
	}

	@Test
	void routesInvalidPlanBackToPlannerWhileAttemptsRemain() {
		ResearchState state = ResearchState.from(new ResearchRequest("Explain routing.", "thread-1", 2));
		state.recordPlanAttempt();
		state.plannerError("bad json");

		node.run(state).collectList().block();

		assertThat(state.planValidatorDecision().nextRoute()).isEqualTo(PlanValidatorRoute.PLANNER);
		assertThat(state.planValidatorDecision().valid()).isFalse();
		assertThat(state.planValidatorDecision().reason()).isEqualTo("bad json");
	}

	@Test
	void routesInvalidPlanToFailureAtRetryLimit() {
		ResearchState state = ResearchState.from(new ResearchRequest("Explain routing.", "thread-1", 2,
				null, true, true, 1));
		state.recordPlanAttempt();
		state.plannerError("bad json");

		node.run(state).collectList().block();

		assertThat(state.planValidatorDecision().nextRoute()).isEqualTo(PlanValidatorRoute.FAILED);
		assertThat(state.planValidatorDecision().maxPlanIterations()).isEqualTo(1);
	}

	private ResearchState stateWithValidPlan(boolean autoAccepted) {
		ResearchState state = ResearchState.from(new ResearchRequest("Explain routing.", "thread-1", 2,
				null, true, autoAccepted));
		state.plan(new ResearchPlan("Plan", true, "Use a small plan.",
				List.of(new ResearchStep("Explain coordinator", "Describe the routing decision.", false,
						StepType.RESEARCH, null, ResearchStep.STATUS_PENDING))));
		return state;
	}

}
