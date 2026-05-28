package top.lanshan.manmu.node;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.lanshan.manmu.agent.ProcessorAgent;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.ResearchTeamDecision;
import top.lanshan.manmu.model.StepExecutionStatus;
import top.lanshan.manmu.model.StepType;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CoderNode implements ResearchNode {

	private final ProcessorAgent processorAgent;

	private final String nodeName;

	public CoderNode(ProcessorAgent processorAgent, String executorId) {
		this.processorAgent = processorAgent;
		if (executorId == null || executorId.isBlank()) {
			throw new IllegalArgumentException("executorId must not be blank");
		}
		this.nodeName = "coder_" + executorId.strip();
	}

	@Override
	public int order() {
		return 46;
	}

	@Override
	public String name() {
		return nodeName;
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

			List<ResearchStep> steps = runnableSteps(state);
			if (steps.isEmpty()) {
				return Flux.empty();
			}

			ResearchEvent started =
					ResearchEvent.message(state.threadId(), name(), "started", "Executing processing steps", decision);
			return Flux.concat(Flux.just(started), Flux.fromIterable(steps).concatMap(step -> executeStep(state, step)),
					Mono.fromSupplier(() -> ResearchEvent.message(state.threadId(), name(), "completed",
							"Processing steps completed", state.observations())).flux());
		});
	}

	private Flux<ResearchEvent> executeStep(ResearchState state, ResearchStep step) {
		return Mono.fromSupplier(() -> {
			markStepStarted(state, step);
			try {
				String result = processorAgent.process(state, step);
				step.executionRes(result);
				markStepCompleted(state, step);
				state.addObservation(result);

				return stepEvent(state, step, "step_completed", step.executionStatus(),
						"Completed: " + step.title(), payload(step, "result", result));
			}
			catch (RuntimeException ex) {
				String errorMessage = errorMessage(ex);
				markStepFailed(state, step, errorMessage);
				throw new StepExecutionException(stepEvent(state, step, "failed", step.executionStatus(),
						"Failed: " + step.title(), payload(step, "error", errorMessage)), ex);
			}
		}).flux()
			.onErrorResume(StepExecutionException.class,
					error -> Flux.concat(Flux.just(error.event()), Flux.error(error.getCause())));
	}

	private List<ResearchStep> runnableSteps(ResearchState state) {
		return state.plan()
			.steps()
			.stream()
			.filter(step -> StepType.PROCESSING.equals(step.stepType()))
			.filter(step -> !StepExecutionStatus.isTerminal(step))
			.filter(step -> StepExecutionStatus.isAssignedTo(step.executionStatus(), name()))
			.findFirst()
			.map(List::of)
			.orElse(List.of());
	}

	private void markStepStarted(ResearchState state, ResearchStep step) {
		step.assignedNode(name());
		if (step.attempt() == 0) {
			step.incrementAttempt();
		}
		step.startedAt(Instant.now());
		step.completedAt(null);
		step.error(null);
		step.executionStatus(StepExecutionStatus.processing(name()));
		state.recordNodeStarted(name(), step);
	}

	private void markStepCompleted(ResearchState state, ResearchStep step) {
		step.completedAt(Instant.now());
		step.error(null);
		step.executionStatus(StepExecutionStatus.completed(name()));
		state.recordNodeCompleted(name());
	}

	private void markStepFailed(ResearchState state, ResearchStep step, String errorMessage) {
		step.completedAt(Instant.now());
		step.error(errorMessage);
		step.executionStatus(StepExecutionStatus.error(name()));
		state.recordNodeFailed(name());
	}

	private ResearchEvent stepEvent(ResearchState state, ResearchStep step, String phase, String status, String content,
			Object payload) {
		return new ResearchEvent(state.threadId(), null, null, name(), name(), null, null, step.id(), phase, status,
				null, content, payload, null, false, Instant.now());
	}

	private Map<String, Object> payload(ResearchStep step, String valueName, Object value) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("step", step);
		payload.put(valueName, value);
		return payload;
	}

	private String errorMessage(RuntimeException ex) {
		if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
			return ex.getMessage();
		}
		return ex.getClass().getSimpleName();
	}

	private static class StepExecutionException extends RuntimeException {

		private final ResearchEvent event;

		StepExecutionException(ResearchEvent event, RuntimeException cause) {
			super(cause);
			this.event = event;
		}

		ResearchEvent event() {
			return event;
		}

	}

}
