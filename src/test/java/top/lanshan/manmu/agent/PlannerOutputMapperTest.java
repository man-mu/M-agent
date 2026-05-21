package top.lanshan.manmu.agent;

import top.lanshan.manmu.model.StepType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlannerOutputMapperTest {

	private final PlannerOutputMapper mapper = new PlannerOutputMapper();

	@Test
	void mapsStructuredPlannerResponseToResearchPlan() {
		PlannerResponse response = new PlannerResponse("Agent learning plan",
				List.of(new PlannerResponse.Step("Understand the workflow",
						"Identify the request, state, node, and event boundaries.", StepType.RESEARCH),
						new PlannerResponse.Step("Summarize implementation path",
								"Turn the findings into a concrete coding sequence.", StepType.SYNTHESIS)));

		var plan = mapper.toResearchPlan(response, 3);

		assertThat(plan.title()).isEqualTo("Agent learning plan");
		assertThat(plan.steps()).hasSize(2);
		assertThat(plan.steps().get(0).type()).isEqualTo(StepType.RESEARCH);
		assertThat(plan.steps().get(1).type()).isEqualTo(StepType.SYNTHESIS);
	}

	@Test
	void limitsStepsByMaxSteps() {
		PlannerResponse response = new PlannerResponse("Agent learning plan",
				List.of(new PlannerResponse.Step("Step one", "Research one.", StepType.RESEARCH),
						new PlannerResponse.Step("Step two", "Research two.", StepType.RESEARCH),
						new PlannerResponse.Step("Step three", "Research three.", StepType.RESEARCH)));

		var plan = mapper.toResearchPlan(response, 2);

		assertThat(plan.steps()).extracting("title").containsExactly("Step one", "Step two");
	}

	@Test
	void rejectsEmptyPlannerResponse() {
		PlannerResponse response = new PlannerResponse("Agent learning plan", List.of());

		assertThatThrownBy(() -> mapper.toResearchPlan(response, 3))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("steps");
	}

}
