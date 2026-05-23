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
		PlannerResponse response = new PlannerResponse("Agent learning plan", true, "Split the work into research and processing.",
				List.of(new PlannerResponse.Step("Understand the workflow",
						"Identify the request, state, node, and event boundaries.", true, StepType.RESEARCH, null),
						new PlannerResponse.Step("Summarize implementation path",
								"Turn the findings into a concrete coding sequence.", false, StepType.PROCESSING, null)));

		var plan = mapper.toResearchPlan(response, 3);

		assertThat(plan.title()).isEqualTo("Agent learning plan");
		assertThat(plan.hasEnoughContext()).isTrue();
		assertThat(plan.thought()).isEqualTo("Split the work into research and processing.");
		assertThat(plan.steps()).hasSize(2);
		assertThat(plan.steps()).extracting("id").containsExactly("step-1", "step-2");
		assertThat(plan.steps().get(0).needWebSearch()).isTrue();
		assertThat(plan.steps().get(0).stepType()).isEqualTo(StepType.RESEARCH);
		assertThat(plan.steps().get(0).executionStatus()).isEqualTo("pending");
		assertThat(plan.steps().get(1).stepType()).isEqualTo(StepType.PROCESSING);
	}

	@Test
	void limitsStepsByMaxSteps() {
		PlannerResponse response = new PlannerResponse("Agent learning plan", true, "",
				List.of(new PlannerResponse.Step("Step one", "Research one.", false, StepType.RESEARCH, null),
						new PlannerResponse.Step("Step two", "Research two.", false, StepType.RESEARCH, null),
						new PlannerResponse.Step("Step three", "Research three.", false, StepType.RESEARCH, null)));

		var plan = mapper.toResearchPlan(response, 2);

		assertThat(plan.steps()).extracting("title").containsExactly("Step one", "Step two");
	}

	@Test
	void rejectsEmptyPlannerResponse() {
		PlannerResponse response = new PlannerResponse("Agent learning plan", true, "", List.of());

		assertThatThrownBy(() -> mapper.toResearchPlan(response, 3))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("steps");
	}

	@Test
	void mapsLegacySynthesisTypeToProcessingStepType() {
		PlannerResponse response = new PlannerResponse("Agent learning plan", true, "",
				List.of(new PlannerResponse.Step("Synthesize", "Summarize the research.", false, null,
						StepType.valueOf("SYNTHESIS"))));

		var plan = mapper.toResearchPlan(response, 1);

		assertThat(plan.steps().get(0).stepType()).isEqualTo(StepType.PROCESSING);
	}

	@Test
	void normalizesMultiStepPlansToResearchBeforeFinalProcessing() {
		PlannerResponse response = new PlannerResponse("Agent learning plan", true, "",
				List.of(new PlannerResponse.Step("Organize", "Prepare the answer.", false, StepType.PROCESSING, null),
						new PlannerResponse.Step("Draft", "Write the answer.", false, StepType.RESEARCH, null)));

		var plan = mapper.toResearchPlan(response, 2);

		assertThat(plan.steps().get(0).stepType()).isEqualTo(StepType.RESEARCH);
		assertThat(plan.steps().get(1).stepType()).isEqualTo(StepType.PROCESSING);
	}

	@Test
	void expandsSingleStepPlanWhenMultipleStepsAreAllowed() {
		PlannerResponse response = new PlannerResponse("Agent learning plan", true, "",
				List.of(new PlannerResponse.Step("Summarize", "Produce a concise answer.", false,
						StepType.PROCESSING, null)));

		var plan = mapper.toResearchPlan(response, 2);

		assertThat(plan.steps()).hasSize(2);
		assertThat(plan.steps()).extracting("id").containsExactly("step-1", "step-2");
		assertThat(plan.steps().get(0).stepType()).isEqualTo(StepType.RESEARCH);
		assertThat(plan.steps().get(1).title()).isEqualTo("Synthesize findings");
		assertThat(plan.steps().get(1).stepType()).isEqualTo(StepType.PROCESSING);
	}

	@Test
	void usesQueryAsFallbackTitleWhenModelOmitsPlanTitle() {
		PlannerResponse response = new PlannerResponse(null, true, "",
				List.of(new PlannerResponse.Step("Step one", "Research one.", false, StepType.RESEARCH, null)));

		var plan = mapper.toResearchPlan(response, "Explain agent workflow roles.", 1);

		assertThat(plan.title()).isEqualTo("Research plan for: Explain agent workflow roles.");
		assertThat(plan.steps()).hasSize(1);
	}

}
