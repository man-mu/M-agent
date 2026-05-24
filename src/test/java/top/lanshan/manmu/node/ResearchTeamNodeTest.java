package top.lanshan.manmu.node;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.ResearchTeamRoute;
import top.lanshan.manmu.model.StepExecutionStatus;
import top.lanshan.manmu.model.StepType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchTeamNodeTest {

	private final ResearchTeamNode node = new ResearchTeamNode();

	@Test
	void routesToParallelExecutorByDefault() {
		ResearchState state = stateWithPlan(List.of(
				new ResearchStep("Inspect workflow", "Read the current workflow.", false, StepType.RESEARCH, null,
						ResearchStep.STATUS_PENDING),
				new ResearchStep("Summarize", "Turn findings into a report section.", false, StepType.PROCESSING, null,
						ResearchStep.STATUS_PENDING)));

		StepVerifier.create(node.run(state)).expectNextCount(1).verifyComplete();

		assertThat(state.researchTeamDecision().nextRoute()).isEqualTo(ResearchTeamRoute.PARALLEL_EXECUTOR);
		assertThat(state.researchTeamDecision().nextStepType()).isEqualTo(StepType.RESEARCH);
		assertThat(state.researchTeamDecision().remainingSteps()).isEqualTo(2);
	}

	@Test
	void routesToReporterWhenEveryStepIsTerminal() {
		ResearchState state = stateWithPlan(List.of(
				new ResearchStep("Inspect workflow", "Read the current workflow.", false, StepType.RESEARCH,
						"Observation", ResearchStep.STATUS_COMPLETED),
				new ResearchStep("Summarize", "Turn findings into a report section.", false, StepType.PROCESSING,
						"Agent failed", ResearchStep.STATUS_ERROR + ": provider rejected request")));

		StepVerifier.create(node.run(state)).expectNextCount(1).verifyComplete();

		assertThat(state.researchTeamDecision().nextRoute()).isEqualTo(ResearchTeamRoute.REPORTER);
		assertThat(state.researchTeamDecision().nextStepType()).isNull();
		assertThat(state.researchTeamDecision().completedSteps()).isEqualTo(1);
		assertThat(state.researchTeamDecision().errorSteps()).isEqualTo(1);
		assertThat(state.researchTeamDecision().remainingSteps()).isZero();
	}

	@Test
	void routesDynamicCompletedAndErrorStatusesAsTerminal() {
		ResearchState state = stateWithPlan(List.of(
				new ResearchStep("Inspect workflow", "Read the current workflow.", false, StepType.RESEARCH,
						"Observation", StepExecutionStatus.completed("researcher_0")),
				new ResearchStep("Summarize", "Turn findings into a report section.", false, StepType.PROCESSING,
						"Agent failed", StepExecutionStatus.error("coder_0"))));

		StepVerifier.create(node.run(state)).expectNextCount(1).verifyComplete();

		assertThat(state.researchTeamDecision().nextRoute()).isEqualTo(ResearchTeamRoute.REPORTER);
		assertThat(state.researchTeamDecision().completedSteps()).isEqualTo(1);
		assertThat(state.researchTeamDecision().errorSteps()).isEqualTo(1);
		assertThat(state.researchTeamDecision().remainingSteps()).isZero();
	}

	private ResearchState stateWithPlan(List<ResearchStep> steps) {
		ResearchState state = ResearchState.from(new ResearchRequest("Explain the workflow.", "thread-1", 3));
		state.plan(new ResearchPlan("Workflow plan", true, "Keep the work small.", steps));
		return state;
	}

}
