package top.lanshan.manmu.node;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import top.lanshan.manmu.agent.ProcessorAgent;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.ResearchTeamDecision;
import top.lanshan.manmu.model.ResearchTeamRoute;
import top.lanshan.manmu.model.SiteInformation;
import top.lanshan.manmu.model.StepSearchContext;
import top.lanshan.manmu.model.StepType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessorNodeTest {

	@Test
	void completesProcessingStepUsingStateContext() {
		RecordingProcessorAgent processorAgent = new RecordingProcessorAgent();
		ProcessorNode node = new ProcessorNode(processorAgent);
		ResearchStep processingStep = new ResearchStep("Synthesize findings", "Turn evidence into next steps.",
				false, StepType.PROCESSING, null, ResearchStep.STATUS_PENDING);
		ResearchState state = stateWithPlan(List.of(
				new ResearchStep("Research source", "Collect evidence.", true, StepType.RESEARCH, "Observation",
						ResearchStep.STATUS_COMPLETED),
				processingStep));
		state.addObservation("Prior observation about the workflow.");
		state.addSearchContext(new StepSearchContext("Research source", "workflow query",
				List.of(new SiteInformation("Source title", "https://example.test/source", "Snippet", "Summary",
						"Example", "", ""))));
		state.researchTeamDecision(new ResearchTeamDecision(ResearchTeamRoute.PROCESSOR, StepType.PROCESSING, 2, 1, 0,
				1));

		StepVerifier.create(node.run(state))
			.assertNext(event -> assertThat(event.phase()).isEqualTo("started"))
			.assertNext(event -> {
				assertThat(event.node()).isEqualTo("processor");
				assertThat(event.phase()).isEqualTo("step_completed");
			})
			.assertNext(event -> assertThat(event.phase()).isEqualTo("completed"))
			.verifyComplete();

		assertThat(processorAgent.state).isSameAs(state);
		assertThat(processorAgent.step).isSameAs(processingStep);
		assertThat(processorAgent.state.observations()).contains("Prior observation about the workflow.",
				"Processed result grounded in prior context.");
		assertThat(processorAgent.state.siteInformation()).extracting(SiteInformation::url)
			.containsExactly("https://example.test/source");
		assertThat(processingStep.executionRes()).isEqualTo("Processed result grounded in prior context.");
		assertThat(processingStep.executionStatus()).isEqualTo(ResearchStep.STATUS_COMPLETED);
		assertThat(processingStep.assignedNode()).isEqualTo("processor");
		assertThat(processingStep.attempt()).isEqualTo(1);
		assertThat(processingStep.error()).isNull();
		assertThat(processingStep.startedAt()).isNotNull();
		assertThat(processingStep.completedAt()).isNotNull();
		assertThat(state.runningNodes()).isEmpty();
		assertThat(state.completedNodes()).containsExactly("processor");
	}

	@Test
	void failedProcessingStepWithBlankExceptionMessageRecordsFallbackError() {
		ProcessorNode node = new ProcessorNode((state, step) -> {
			throw new RuntimeException();
		});
		ResearchStep processingStep = new ResearchStep("Synthesize findings", "Turn evidence into next steps.",
				false, StepType.PROCESSING, null, ResearchStep.STATUS_PENDING);
		ResearchState state = stateWithPlan(List.of(processingStep));
		state.researchTeamDecision(new ResearchTeamDecision(ResearchTeamRoute.PROCESSOR, StepType.PROCESSING, 1, 0, 0,
				1));

		StepVerifier.create(node.run(state))
			.expectError(RuntimeException.class)
			.verify();

		assertThat(processingStep.executionStatus()).isEqualTo("error: RuntimeException");
		assertThat(processingStep.assignedNode()).isEqualTo("processor");
		assertThat(processingStep.attempt()).isEqualTo(1);
		assertThat(processingStep.error()).isEqualTo("RuntimeException");
		assertThat(state.failedNodes()).containsExactly("processor");
	}

	private ResearchState stateWithPlan(List<ResearchStep> steps) {
		ResearchState state = ResearchState.from(new ResearchRequest("Explain the workflow.", "thread-1", 3));
		state.plan(new ResearchPlan("Workflow plan", true, "Research first, then process.", steps));
		return state;
	}

	private static class RecordingProcessorAgent implements ProcessorAgent {

		private ResearchState state;

		private ResearchStep step;

		@Override
		public String process(ResearchState state, ResearchStep step) {
			this.state = state;
			this.step = step;
			assertThat(state.observations()).contains("Prior observation about the workflow.");
			assertThat(state.siteInformation()).extracting(SiteInformation::title).containsExactly("Source title");
			return "Processed result grounded in prior context.";
		}

	}

}
