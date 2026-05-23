package top.lanshan.manmu.node;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import top.lanshan.manmu.agent.ProcessorAgent;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.ResearchTeamDecision;
import top.lanshan.manmu.model.StepExecutionStatus;
import top.lanshan.manmu.model.StepType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ProcessorNode implements ResearchNode {

	private final ProcessorAgent processorAgent;

	public ProcessorNode(ProcessorAgent processorAgent) {
		this.processorAgent = processorAgent;
	}

	@Override
	public int order() {
		return 45;
	}

	@Override
	public String name() {
		return "processor";
	}

	@Override
	public Flux<ResearchEvent> run(ResearchState state) {
		return Flux.defer(() -> {
			if (state.plan() == null) {
				return Flux.error(new IllegalStateException("Research plan is missing"));
			}
			ResearchTeamDecision decision = state.researchTeamDecision();
			if (decision == null || decision.nextStepType() == null) {
				return Flux.error(new IllegalStateException("Research team decision is missing"));
			}
			if (!StepType.PROCESSING.equals(decision.nextStepType())) {
				return Flux.error(new IllegalStateException("Processor can only execute processing steps"));
			}

			List<ResearchStep> steps = state.plan()
				.steps()
				.stream()
				.filter(step -> StepType.PROCESSING.equals(step.stepType()))
				.filter(step -> !isTerminal(step))
				.toList();
			if (steps.isEmpty()) {
				return Flux.error(new IllegalStateException("No pending processing steps are available"));
			}

			List<ResearchEvent> events = new ArrayList<>();
			events.add(ResearchEvent.message(state.threadId(), name(), "started", "Executing processing steps",
					decision));

			for (ResearchStep step : steps) {
				markStepStarted(state, step);
				try {
					String result = processorAgent.process(state, step);
					step.executionRes(result);
					markStepCompleted(state, step);
					state.addObservation(result);

					events.add(ResearchEvent.message(state.threadId(), name(), "step_completed",
							"Completed: " + step.title(), Map.of("step", step, "result", result)));
				}
				catch (RuntimeException ex) {
					String errorMessage = errorMessage(ex);
					markStepFailed(state, step, errorMessage);
					events.add(ResearchEvent.message(state.threadId(), name(), "step_error",
							"Failed: " + step.title(), Map.of("step", step, "error", errorMessage)));
					throw ex;
				}
			}

			events.add(ResearchEvent.message(state.threadId(), name(), "completed", "Processing steps completed",
					state.observations()));
			return Flux.fromIterable(events);
		});
	}

	private boolean isTerminal(ResearchStep step) {
		return StepExecutionStatus.isTerminal(step);
	}

	private void markStepStarted(ResearchState state, ResearchStep step) {
		step.assignedNode(name());
		step.incrementAttempt();
		step.startedAt(Instant.now());
		step.completedAt(null);
		step.error(null);
		step.executionStatus(ResearchStep.STATUS_PROCESSING);
		state.recordNodeStarted(name(), step);
	}

	private void markStepCompleted(ResearchState state, ResearchStep step) {
		step.completedAt(Instant.now());
		step.error(null);
		step.executionStatus(ResearchStep.STATUS_COMPLETED);
		state.recordNodeCompleted(name());
	}

	private void markStepFailed(ResearchState state, ResearchStep step, String errorMessage) {
		step.completedAt(Instant.now());
		step.error(errorMessage);
		step.executionStatus(StepExecutionStatus.legacyError(errorMessage));
		state.recordNodeFailed(name());
	}

	private String errorMessage(RuntimeException ex) {
		if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
			return ex.getMessage();
		}
		return ex.getClass().getSimpleName();
	}

}
