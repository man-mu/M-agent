package top.lanshan.manmu.node;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStreamEventType;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.ResearchTeamDecision;
import top.lanshan.manmu.model.ResearchTeamRoute;
import top.lanshan.manmu.model.StepExecutionStatus;
import top.lanshan.manmu.model.StepType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ResearcherNodeTest {

	@Test
	void dynamicResearcherProcessesOnlyAssignedResearchStep() {
		AtomicReference<String> statusDuringExecution = new AtomicReference<>();
		ResearcherNode node = new ResearcherNode((query, step, searchContext) -> {
			statusDuringExecution.set(step.executionStatus());
			return "Observation for " + step.id();
		}, "0");
		ResearchStep own = step("step-1", StepType.RESEARCH, StepExecutionStatus.assigned("researcher_0"));
		own.assignedNode("researcher_0");
		own.attempt(1);
		ResearchStep otherResearcher = step("step-2", StepType.RESEARCH,
				StepExecutionStatus.assigned("researcher_1"));
		otherResearcher.assignedNode("researcher_1");
		ResearchStep processing = step("step-3", StepType.PROCESSING,
				StepExecutionStatus.assigned("researcher_0"));
		processing.assignedNode("researcher_0");
		ResearchState state = stateWithPlan(List.of(own, otherResearcher, processing));
		state.researchTeamDecision(new ResearchTeamDecision(ResearchTeamRoute.RESEARCHER, StepType.RESEARCH, 2, 0, 0,
				2));

		List<ResearchEvent> events = node.run(state).collectList().block();

		assertThat(node.name()).isEqualTo("researcher_0");
		assertThat(events).hasSize(3);
		assertThat(own.executionStatus()).isEqualTo(StepExecutionStatus.completed("researcher_0"));
		assertThat(statusDuringExecution.get()).isEqualTo(StepExecutionStatus.processing("researcher_0"));
		assertThat(own.executionRes()).isEqualTo("Observation for step-1");
		assertThat(own.attempt()).isEqualTo(1);
		assertThat(own.startedAt()).isNotNull();
		assertThat(own.completedAt()).isNotNull();
		assertThat(otherResearcher.executionStatus()).isEqualTo(StepExecutionStatus.assigned("researcher_1"));
		assertThat(otherResearcher.executionRes()).isNull();
		assertThat(otherResearcher.attempt()).isZero();
		assertThat(processing.executionStatus()).isEqualTo(StepExecutionStatus.assigned("researcher_0"));
		assertThat(processing.executionRes()).isNull();
		assertThat(processing.attempt()).isZero();
		assertThat(state.observations()).containsExactly("Observation for step-1");
		assertThat(state.runningNodes()).isEmpty();
		assertThat(state.completedNodes()).containsExactly("researcher_0");
		assertThat(state.lastAssignedNodes()).containsEntry("researcher_0", "step-1");
		assertThat(events.get(1).node()).isEqualTo("researcher_0");
		assertThat(events.get(1).stepId()).isEqualTo("step-1");
		assertThat(events.get(1).status()).isEqualTo(StepExecutionStatus.completed("researcher_0"));
		ResearchEvent sequencedStepEvent = events.get(1).withSequence(7);
		assertThat(sequencedStepEvent.sequence()).isEqualTo(7);
		assertThat(sequencedStepEvent.eventType()).isEqualTo(ResearchStreamEventType.NODE_DELTA);
		assertThat(sequencedStepEvent.nodeName()).isEqualTo("researcher_0");
		assertThat(sequencedStepEvent.nodeType()).isEqualTo("researcher");
		assertThat(sequencedStepEvent.executorId()).isZero();
		assertThat(sequencedStepEvent.stepId()).isEqualTo("step-1");
	}

	@Test
	void dynamicResearcherEmitsNoEventsWhenNoStepIsAssignedToIt() {
		ResearcherNode node = new ResearcherNode((query, step, searchContext) -> "unused", "1");
		ResearchStep step = step("step-1", StepType.RESEARCH, StepExecutionStatus.assigned("researcher_0"));
		step.assignedNode("researcher_0");
		ResearchState state = stateWithPlan(List.of(step));
		state.researchTeamDecision(new ResearchTeamDecision(ResearchTeamRoute.RESEARCHER, StepType.RESEARCH, 1, 0, 0,
				1));

		StepVerifier.create(node.run(state)).verifyComplete();

		assertThat(step.executionStatus()).isEqualTo(StepExecutionStatus.assigned("researcher_0"));
		assertThat(step.attempt()).isZero();
	}

	@Test
	void dynamicResearcherFailureRecordsDynamicStatusAndFailureEvent() {
		ResearcherNode node = new ResearcherNode((query, step, searchContext) -> {
			throw new RuntimeException();
		}, "0");
		ResearchStep step = step("step-1", StepType.RESEARCH, StepExecutionStatus.assigned("researcher_0"));
		step.assignedNode("researcher_0");
		ResearchState state = stateWithPlan(List.of(step));
		state.researchTeamDecision(new ResearchTeamDecision(ResearchTeamRoute.RESEARCHER, StepType.RESEARCH, 1, 0, 0,
				1));
		List<ResearchEvent> events = new ArrayList<>();

		StepVerifier.create(node.run(state).doOnNext(events::add))
			.expectNextCount(2)
			.expectError(RuntimeException.class)
			.verify();

		assertThat(step.executionStatus()).isEqualTo(StepExecutionStatus.error("researcher_0"));
		assertThat(step.assignedNode()).isEqualTo("researcher_0");
		assertThat(step.attempt()).isEqualTo(1);
		assertThat(step.error()).isEqualTo("RuntimeException");
		assertThat(step.startedAt()).isNotNull();
		assertThat(step.completedAt()).isNotNull();
		assertThat(state.failedNodes()).containsExactly("researcher_0");
		assertThat(events).hasSize(2);
		ResearchEvent failedEvent = events.get(1);
		assertThat(failedEvent.node()).isEqualTo("researcher_0");
		assertThat(failedEvent.phase()).isEqualTo("failed");
		assertThat(failedEvent.status()).isEqualTo(StepExecutionStatus.error("researcher_0"));
		assertThat(failedEvent.stepId()).isEqualTo("step-1");
		ResearchEvent sequencedFailedEvent = failedEvent.withSequence(9);
		assertThat(sequencedFailedEvent.eventType()).isEqualTo(ResearchStreamEventType.NODE_FAILED);
		assertThat(sequencedFailedEvent.nodeType()).isEqualTo("researcher");
		assertThat(sequencedFailedEvent.executorId()).isZero();
	}

	@Test
	void compatibilityResearcherKeepsLegacyStatusesAndProcessesAllMatchingSteps() {
		ResearcherNode node = new ResearcherNode((query, step, searchContext) -> "Observation for " + step.id());
		ResearchStep first = step("step-1", StepType.RESEARCH, ResearchStep.STATUS_PENDING);
		ResearchStep second = step("step-2", StepType.RESEARCH, StepExecutionStatus.assigned("researcher_0"));
		ResearchState state = stateWithPlan(List.of(first, second));
		state.researchTeamDecision(new ResearchTeamDecision(ResearchTeamRoute.RESEARCHER, StepType.RESEARCH, 2, 0, 0,
				2));

		List<ResearchEvent> events = node.run(state).collectList().block();

		assertThat(events).hasSize(4);
		assertThat(first.assignedNode()).isEqualTo("researcher");
		assertThat(second.assignedNode()).isEqualTo("researcher");
		assertThat(first.executionStatus()).isEqualTo(ResearchStep.STATUS_COMPLETED);
		assertThat(second.executionStatus()).isEqualTo(ResearchStep.STATUS_COMPLETED);
		assertThat(first.executionRes()).isEqualTo("Observation for step-1");
		assertThat(second.executionRes()).isEqualTo("Observation for step-2");
		assertThat(first.attempt()).isEqualTo(1);
		assertThat(second.attempt()).isEqualTo(1);
		assertThat(state.observations()).containsExactly("Observation for step-1", "Observation for step-2");
		assertThat(events).extracting(ResearchEvent::node).containsOnly("researcher");
	}

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
		List<ResearchEvent> events = new ArrayList<>();

		StepVerifier.create(node.run(state).doOnNext(events::add))
			.expectNextCount(2)
			.expectError(RuntimeException.class)
			.verify();

		assertThat(researchStep.executionStatus()).isEqualTo("error: RuntimeException");
		assertThat(researchStep.assignedNode()).isEqualTo("researcher");
		assertThat(researchStep.attempt()).isEqualTo(1);
		assertThat(researchStep.error()).isEqualTo("RuntimeException");
		assertThat(researchStep.startedAt()).isNotNull();
		assertThat(researchStep.completedAt()).isNotNull();
		assertThat(state.failedNodes()).containsExactly("researcher");
		assertThat(events).hasSize(2);
		assertThat(events.get(1).node()).isEqualTo("researcher");
		assertThat(events.get(1).phase()).isEqualTo("failed");
		assertThat(events.get(1).status()).isEqualTo("error: RuntimeException");
		assertThat(events.get(1).stepId()).isEqualTo(researchStep.id());
	}

	private ResearchStep step(String id, StepType stepType, String executionStatus) {
		ResearchStep step = new ResearchStep("Step " + id, "Do work for " + id + ".", false, stepType, null,
				executionStatus);
		step.id(id);
		return step;
	}

	private ResearchState stateWithPlan(List<ResearchStep> steps) {
		ResearchState state = ResearchState.from(new ResearchRequest("Explain the workflow.", "thread-1", 3));
		state.plan(new ResearchPlan("Workflow plan", true, "Research first.", steps));
		return state;
	}

}
