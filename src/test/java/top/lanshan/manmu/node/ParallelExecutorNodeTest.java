package top.lanshan.manmu.node;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import top.lanshan.manmu.config.AdvancedExecutionProperties;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.ResearchStreamEventType;
import top.lanshan.manmu.model.StepExecutionStatus;
import top.lanshan.manmu.model.StepType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelExecutorNodeTest {

	private final ParallelExecutorNode node = new ParallelExecutorNode(defaultProperties());

	@Test
	void assignsPendingResearchStepsRoundRobin() {
		ResearchStep first = step("step-1", StepType.RESEARCH, ResearchStep.STATUS_PENDING);
		ResearchStep second = step("step-2", StepType.RESEARCH, ResearchStep.STATUS_PENDING);
		ResearchStep third = step("step-3", StepType.RESEARCH, ResearchStep.STATUS_PENDING);
		ResearchStep processing = step("step-4", StepType.PROCESSING, ResearchStep.STATUS_PENDING);
		ResearchState state = stateWithPlan(List.of(first, second, third, processing));

		List<ResearchEvent> events = node.run(state).collectList().block();

		assertThat(events).hasSize(3);
		assertAssigned(first, "researcher_0");
		assertAssigned(second, "researcher_1");
		assertAssigned(third, "researcher_0");
		assertThat(processing.executionStatus()).isEqualTo(ResearchStep.STATUS_PENDING);
		assertThat(processing.assignedNode()).isNull();
		assertThat(state.runningNodes()).containsExactly("researcher_0", "researcher_1");
		assertThat(state.lastAssignedNodes()).containsEntry("researcher_0", "step-3")
			.containsEntry("researcher_1", "step-2");
		assertThat(events).extracting(ResearchEvent::stepId).containsExactly("step-1", "step-2", "step-3");
		assertThat(events).extracting(ResearchEvent::status)
			.containsExactly(StepExecutionStatus.assigned("researcher_0"),
					StepExecutionStatus.assigned("researcher_1"),
					StepExecutionStatus.assigned("researcher_0"));
	}

	@Test
	void waitsToAssignProcessingStepsUntilResearchStepsAreTerminal() {
		ResearchStep research = step("step-1", StepType.RESEARCH, ResearchStep.STATUS_PENDING);
		ResearchStep processing = step("step-2", StepType.PROCESSING, ResearchStep.STATUS_PENDING);
		ResearchState state = stateWithPlan(List.of(research, processing));

		StepVerifier.create(node.run(state)).expectNextCount(1).verifyComplete();

		assertAssigned(research, "researcher_0");
		assertThat(processing.executionStatus()).isEqualTo(ResearchStep.STATUS_PENDING);
		assertThat(processing.assignedNode()).isNull();
	}

	@Test
	void assignsProcessingStepsToCoderAfterResearchStepsAreTerminal() {
		ResearchStep research = step("step-1", StepType.RESEARCH, ResearchStep.STATUS_COMPLETED);
		ResearchStep firstProcessing = step("step-2", StepType.PROCESSING, ResearchStep.STATUS_PENDING);
		ResearchStep secondProcessing = step("step-3", StepType.PROCESSING, ResearchStep.STATUS_PENDING);
		ResearchState state = stateWithPlan(List.of(research, firstProcessing, secondProcessing));

		List<ResearchEvent> events = node.run(state).collectList().block();

		assertThat(events).hasSize(2);
		assertAssigned(firstProcessing, "coder_0");
		assertAssigned(secondProcessing, "coder_0");
		assertThat(state.runningNodes()).containsExactly("coder_0");
		assertThat(state.lastAssignedNodes()).containsEntry("coder_0", "step-3");
	}

	@Test
	void skipsAssignedProcessingCompletedAndErrorSteps() {
		ResearchStep assigned = step("step-1", StepType.RESEARCH, StepExecutionStatus.assigned("researcher_0"));
		assigned.assignedNode("researcher_0");
		ResearchStep processing = step("step-2", StepType.RESEARCH, StepExecutionStatus.processing("researcher_1"));
		processing.assignedNode("researcher_1");
		ResearchStep completed = step("step-3", StepType.RESEARCH, StepExecutionStatus.completed("researcher_0"));
		ResearchStep error = step("step-4", StepType.RESEARCH, StepExecutionStatus.error("researcher_1"));
		ResearchStep pending = step("step-5", StepType.RESEARCH, ResearchStep.STATUS_PENDING);
		ResearchState state = stateWithPlan(List.of(assigned, processing, completed, error, pending));

		List<ResearchEvent> events = node.run(state).collectList().block();

		assertThat(events).singleElement().satisfies(event -> assertThat(event.stepId()).isEqualTo("step-5"));
		assertAssigned(pending, "researcher_0");
		assertThat(assigned.attempt()).isZero();
		assertThat(processing.attempt()).isZero();
		assertThat(completed.attempt()).isZero();
		assertThat(error.attempt()).isZero();
		assertThat(assigned.executionStatus()).isEqualTo(StepExecutionStatus.assigned("researcher_0"));
		assertThat(processing.executionStatus()).isEqualTo(StepExecutionStatus.processing("researcher_1"));
		assertThat(completed.executionStatus()).isEqualTo(StepExecutionStatus.completed("researcher_0"));
		assertThat(error.executionStatus()).isEqualTo(StepExecutionStatus.error("researcher_1"));
	}

	@Test
	void emitsStepAssignedEventPayloadWithExecutorDetails() {
		ResearchStep step = step("step-1", StepType.RESEARCH, ResearchStep.STATUS_PENDING);
		ResearchState state = stateWithPlan(List.of(step));

		ResearchEvent event = node.run(state).blockFirst();

		assertThat(event).isNotNull();
		assertThat(event.node()).isEqualTo("parallel_executor");
		assertThat(event.phase()).isEqualTo("step.assigned");
		assertThat(event.stepId()).isEqualTo("step-1");
		assertThat(event.status()).isEqualTo(StepExecutionStatus.assigned("researcher_0"));
		assertThat(event.payload()).isInstanceOf(Map.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> payload = (Map<String, Object>) event.payload();
		assertThat(payload).containsEntry("step_id", "step-1")
			.containsEntry("assigned_node", "researcher_0")
			.containsEntry("step_type", StepType.RESEARCH)
			.containsEntry("execution_status", StepExecutionStatus.assigned("researcher_0"))
			.containsEntry("status", StepExecutionStatus.assigned("researcher_0"));

		ResearchEvent sequenced = event.withSequence(7);
		assertThat(sequenced.sequence()).isEqualTo(7);
		assertThat(sequenced.eventType()).isEqualTo(ResearchStreamEventType.NODE_DELTA);
		assertThat(sequenced.nodeName()).isEqualTo("parallel_executor");
		assertThat(sequenced.nodeType()).isEqualTo("parallel_executor");
		assertThat(sequenced.displayTitle()).isEqualTo("Parallel Executor");
	}

	@Test
	void emitsNoEventsWhenNoStepsAreAssignable() {
		ResearchState state = stateWithPlan(List.of(
				step("step-1", StepType.RESEARCH, ResearchStep.STATUS_COMPLETED),
				step("step-2", StepType.PROCESSING, StepExecutionStatus.error("coder_0"))));

		StepVerifier.create(node.run(state)).verifyComplete();
	}

	@Test
	void failsWhenPlanIsMissing() {
		ResearchState state = ResearchState.from(new ResearchRequest("Explain the workflow.", "thread-1", 3));

		StepVerifier.create(node.run(state))
			.expectErrorMessage("Research plan is missing")
			.verify();
	}

	@Test
	void failsWhenPlanHasNoSteps() {
		ResearchState state = stateWithPlan(List.of());

		StepVerifier.create(node.run(state))
			.expectErrorMessage("Research plan has no steps")
			.verify();
	}

	private void assertAssigned(ResearchStep step, String nodeName) {
		assertThat(step.assignedNode()).isEqualTo(nodeName);
		assertThat(step.executionStatus()).isEqualTo(StepExecutionStatus.assigned(nodeName));
		assertThat(step.attempt()).isEqualTo(1);
	}

	private ResearchStep step(String id, StepType stepType, String executionStatus) {
		ResearchStep step = new ResearchStep("Step " + id, "Do work for " + id + ".", false, stepType, null,
				executionStatus);
		step.id(id);
		return step;
	}

	private ResearchState stateWithPlan(List<ResearchStep> steps) {
		ResearchState state = ResearchState.from(new ResearchRequest("Explain the workflow.", "thread-1", 3));
		state.plan(new ResearchPlan("Workflow plan", true, "Keep the work small.", steps));
		return state;
	}

	private AdvancedExecutionProperties defaultProperties() {
		return new AdvancedExecutionProperties();
	}

}
