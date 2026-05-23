package top.lanshan.manmu.node;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.ResearchTeamDecision;
import top.lanshan.manmu.model.ResearchTeamRoute;
import top.lanshan.manmu.model.StepType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResearcherNodeTest {

	@Test
	void failedResearchStepWithBlankExceptionMessageRecordsFallbackError() {
		ResearcherNode node = new ResearcherNode((query, step, searchContext) -> {
			throw new RuntimeException();
		});
		ResearchStep researchStep = new ResearchStep("Research source", "Collect evidence.", true,
				StepType.RESEARCH, null, ResearchStep.STATUS_PENDING);
		ResearchState state = stateWithPlan(List.of(researchStep));
		state.researchTeamDecision(new ResearchTeamDecision(ResearchTeamRoute.RESEARCHER, StepType.RESEARCH, 1, 0, 0,
				1));

		StepVerifier.create(node.run(state))
			.expectError(RuntimeException.class)
			.verify();

		assertThat(researchStep.executionStatus()).isEqualTo("error: RuntimeException");
		assertThat(researchStep.assignedNode()).isEqualTo("researcher");
		assertThat(researchStep.attempt()).isEqualTo(1);
		assertThat(researchStep.error()).isEqualTo("RuntimeException");
		assertThat(researchStep.startedAt()).isNotNull();
		assertThat(researchStep.completedAt()).isNotNull();
		assertThat(state.failedNodes()).containsExactly("researcher");
	}

	private ResearchState stateWithPlan(List<ResearchStep> steps) {
		ResearchState state = ResearchState.from(new ResearchRequest("Explain the workflow.", "thread-1", 3));
		state.plan(new ResearchPlan("Workflow plan", true, "Research first.", steps));
		return state;
	}

}
