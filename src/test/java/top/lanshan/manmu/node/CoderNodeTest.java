package top.lanshan.manmu.node;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.ResearchStreamEventType;
import top.lanshan.manmu.model.ResearchTeamDecision;
import top.lanshan.manmu.model.ResearchTeamRoute;
import top.lanshan.manmu.model.StepExecutionStatus;
import top.lanshan.manmu.model.StepType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CoderNodeTest {

	@Test
	void dynamicCoderProcessesOnlyAssignedProcessingStep() {
		AtomicReference<String> statusDuringExecution = new AtomicReference<>();
		CoderNode node = new CoderNode((state, step) -> {
			statusDuringExecution.set(step.executionStatus());
			return "Processed result for " + step.id();
		}, "0");
		ResearchStep own = step("step-1", StepType.PROCESSING, StepExecutionStatus.assigned("coder_0"));
		own.assignedNode("coder_0");
		own.attempt(1);
		ResearchStep otherCoder = step("step-2", StepType.PROCESSING, StepExecutionStatus.assigned("coder_1"));
		otherCoder.assignedNode("coder_1");
		ResearchStep research = step("step-3", StepType.RESEARCH, StepExecutionStatus.assigned("coder_0"));
		research.assignedNode("coder_0");
		ResearchState state = stateWithPlan(List.of(own, otherCoder, research));
		state.researchTeamDecision(new ResearchTeamDecision(ResearchTeamRoute.PROCESSOR, StepType.PROCESSING, 3, 0, 0,
				3));

		List<ResearchEvent> events = node.run(state).collectList().block();

		assertThat(node.name()).isEqualTo("coder_0");
		assertThat(events).hasSize(3);
		assertThat(own.executionStatus()).isEqualTo(StepExecutionStatus.completed("coder_0"));
		assertThat(statusDuringExecution.get()).isEqualTo(StepExecutionStatus.processing("coder_0"));
		assertThat(own.executionRes()).isEqualTo("Processed result for step-1");
		assertThat(own.attempt()).isEqualTo(1);
		assertThat(own.startedAt()).isNotNull();
		assertThat(own.completedAt()).isNotNull();
		assertThat(otherCoder.executionStatus()).isEqualTo(StepExecutionStatus.assigned("coder_1"));
		assertThat(otherCoder.executionRes()).isNull();
		assertThat(otherCoder.attempt()).isZero();
		assertThat(research.executionStatus()).isEqualTo(StepExecutionStatus.assigned("coder_0"));
		assertThat(research.executionRes()).isNull();
		assertThat(research.attempt()).isZero();
		assertThat(state.observations()).containsExactly("Processed result for step-1");
		assertThat(state.runningNodes()).isEmpty();
		assertThat(state.completedNodes()).containsExactly("coder_0");
		assertThat(state.lastAssignedNodes()).containsEntry("coder_0", "step-1");
		assertThat(events.get(1).node()).isEqualTo("coder_0");
		assertThat(events.get(1).stepId()).isEqualTo("step-1");
		assertThat(events.get(1).status()).isEqualTo(StepExecutionStatus.completed("coder_0"));
		ResearchEvent sequencedStepEvent = events.get(1).withSequence(7);
		assertThat(sequencedStepEvent.sequence()).isEqualTo(7);
		assertThat(sequencedStepEvent.eventType()).isEqualTo(ResearchStreamEventType.NODE_DELTA);
		assertThat(sequencedStepEvent.nodeName()).isEqualTo("coder_0");
		assertThat(sequencedStepEvent.nodeType()).isEqualTo("coder");
		assertThat(sequencedStepEvent.executorId()).isZero();
		assertThat(sequencedStepEvent.stepId()).isEqualTo("step-1");
	}

	@Test
	void dynamicCoderEmitsNoEventsWhenNoStepIsAssignedToIt() {
		CoderNode node = new CoderNode((state, step) -> "unused", "1");
		ResearchStep step = step("step-1", StepType.PROCESSING, StepExecutionStatus.assigned("coder_0"));
		step.assignedNode("coder_0");
		ResearchState state = stateWithPlan(List.of(step));
		state.researchTeamDecision(new ResearchTeamDecision(ResearchTeamRoute.PROCESSOR, StepType.PROCESSING, 1, 0, 0,
				1));

		StepVerifier.create(node.run(state)).verifyComplete();

		assertThat(step.executionStatus()).isEqualTo(StepExecutionStatus.assigned("coder_0"));
		assertThat(step.attempt()).isZero();
	}

	@Test
	void dynamicCoderSkipsResearchStepEvenWhenAssignedToCoder() {
		CoderNode node = new CoderNode((state, step) -> "unused", "0");
		ResearchStep step = step("step-1", StepType.RESEARCH, StepExecutionStatus.assigned("coder_0"));
		step.assignedNode("coder_0");
		ResearchState state = stateWithPlan(List.of(step));
		state.researchTeamDecision(new ResearchTeamDecision(ResearchTeamRoute.PROCESSOR, StepType.PROCESSING, 1, 0, 0,
				1));

		StepVerifier.create(node.run(state)).verifyComplete();

		assertThat(step.executionStatus()).isEqualTo(StepExecutionStatus.assigned("coder_0"));
		assertThat(step.executionRes()).isNull();
		assertThat(step.attempt()).isZero();
	}

	@Test
	void dynamicCoderFailureRecordsDynamicStatusAndFailureEvent() {
		CoderNode node = new CoderNode((state, step) -> {
			throw new RuntimeException();
		}, "0");
		ResearchStep step = step("step-1", StepType.PROCESSING, StepExecutionStatus.assigned("coder_0"));
		step.assignedNode("coder_0");
		ResearchState state = stateWithPlan(List.of(step));
		state.researchTeamDecision(new ResearchTeamDecision(ResearchTeamRoute.PROCESSOR, StepType.PROCESSING, 1, 0, 0,
				1));
		List<ResearchEvent> events = new ArrayList<>();

		StepVerifier.create(node.run(state).doOnNext(events::add))
			.expectNextCount(2)
			.expectError(RuntimeException.class)
			.verify();

		assertThat(step.executionStatus()).isEqualTo(StepExecutionStatus.error("coder_0"));
		assertThat(step.assignedNode()).isEqualTo("coder_0");
		assertThat(step.attempt()).isEqualTo(1);
		assertThat(step.error()).isEqualTo("RuntimeException");
		assertThat(step.startedAt()).isNotNull();
		assertThat(step.completedAt()).isNotNull();
		assertThat(state.failedNodes()).containsExactly("coder_0");
		assertThat(events).hasSize(2);
		ResearchEvent failedEvent = events.get(1);
		assertThat(failedEvent.node()).isEqualTo("coder_0");
		assertThat(failedEvent.phase()).isEqualTo("failed");
		assertThat(failedEvent.status()).isEqualTo(StepExecutionStatus.error("coder_0"));
		assertThat(failedEvent.stepId()).isEqualTo("step-1");
		ResearchEvent sequencedFailedEvent = failedEvent.withSequence(9);
		assertThat(sequencedFailedEvent.eventType()).isEqualTo(ResearchStreamEventType.NODE_FAILED);
		assertThat(sequencedFailedEvent.nodeType()).isEqualTo("coder");
		assertThat(sequencedFailedEvent.executorId()).isZero();
	}

	private ResearchStep step(String id, StepType stepType, String executionStatus) {
		ResearchStep step = new ResearchStep("Step " + id, "Do work for " + id + ".", false, stepType, null,
				executionStatus);
		step.id(id);
		return step;
	}

	private ResearchState stateWithPlan(List<ResearchStep> steps) {
		ResearchState state = ResearchState.from(new ResearchRequest("Explain the workflow.", "thread-1", 3));
		state.plan(new ResearchPlan("Workflow plan", true, "Research first, then process.", steps));
		return state;
	}

}
