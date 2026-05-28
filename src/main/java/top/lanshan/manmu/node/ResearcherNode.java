package top.lanshan.manmu.node;

import top.lanshan.manmu.agent.ResearcherAgent;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.ResearchTeamDecision;
import top.lanshan.manmu.model.StepSearchContext;
import top.lanshan.manmu.model.StepExecutionStatus;
import top.lanshan.manmu.model.StepType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class ResearcherNode implements ResearchNode {

	private final ResearcherAgent researcherAgent;

	private final String nodeName;

	@Autowired
	public ResearcherNode(ResearcherAgent researcherAgent) {
		this(researcherAgent, "0");
	}

	public ResearcherNode(ResearcherAgent researcherAgent, String executorId) {
		this.researcherAgent = researcherAgent;
		this.nodeName = "researcher_" + executorId.strip();
	}

	@Override
	public int order() {
		return 40;
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

			List<ResearchStep> steps = runnableSteps(state, decision);
			if (steps.isEmpty()) {
				return Flux.empty();
			}

			ResearchEvent started = ResearchEvent.message(state.threadId(), name(), "started",
					"Executing " + decision.nextStepType().name().toLowerCase() + " steps", decision);
			return Flux.concat(Flux.just(started), Flux.fromIterable(steps).concatMap(step -> executeStep(state, step)),
					Mono.fromSupplier(() -> ResearchEvent.message(state.threadId(), name(), "completed",
							"Research steps completed", state.observations())).flux());
		});
	}

	private Flux<ResearchEvent> executeStep(ResearchState state, ResearchStep step) {
		return Mono.fromSupplier(() -> {
			markStepStarted(state, step);
			try {
				StepSearchContext searchContext = state.searchContextFor(step).orElse(step.searchContext());
				String observation = researcherAgent.research(state.query(), step, searchContext);
				step.executionRes(observation);
				markStepCompleted(state, step);
				state.addObservation(observation);

				return stepEvent(state, step, "step_completed", step.executionStatus(),
						"Completed: " + step.title(), Map.of("step", step, "observation", observation));
			}
			catch (RuntimeException ex) {
				String errorMessage = errorMessage(ex);
				markStepFailed(state, step, errorMessage);
				throw new StepExecutionException(stepEvent(state, step, "failed", step.executionStatus(),
						"Failed: " + step.title(), Map.of("step", step, "error", errorMessage)), ex);
			}
		}).flux()
			.onErrorResume(StepExecutionException.class,
					error -> Flux.concat(Flux.just(error.event()), Flux.error(error.getCause())));
	}

	private List<ResearchStep> runnableSteps(ResearchState state, ResearchTeamDecision decision) {
		return state.plan()
			.steps()
			.stream()
			.filter(step -> StepType.RESEARCH.equals(step.stepType()))
			.filter(step -> !isTerminal(step))
			.filter(step -> StepExecutionStatus.isAssignedTo(step.executionStatus(), name()))
			.findFirst()
			.map(List::of)
			.orElse(List.of());
	}

	private boolean isTerminal(ResearchStep step) {
		return StepExecutionStatus.isTerminal(step);
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
