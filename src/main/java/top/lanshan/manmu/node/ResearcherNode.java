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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ResearcherNode implements ResearchNode {

	private final ResearcherAgent researcherAgent;

	private final String nodeName;

	private final boolean executorNode;

	@Autowired
	public ResearcherNode(ResearcherAgent researcherAgent) {
		this(researcherAgent, null);
	}

	public ResearcherNode(ResearcherAgent researcherAgent, String executorId) {
		this.researcherAgent = researcherAgent;
		this.executorNode = executorId != null && !executorId.isBlank();
		this.nodeName = executorNode ? "researcher_" + executorId.strip() : "researcher";
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

			List<ResearchEvent> events = new ArrayList<>();
			List<ResearchStep> steps = runnableSteps(state, decision);
			if (steps.isEmpty()) {
				if (executorNode) {
					return Flux.empty();
				}
				return Flux.error(new IllegalStateException(
						"No pending " + decision.nextStepType().name().toLowerCase() + " steps are available"));
			}

			events.add(ResearchEvent.message(state.threadId(), name(), "started",
					"Executing " + decision.nextStepType().name().toLowerCase() + " steps", decision));

			for (ResearchStep step : steps) {
				markStepStarted(state, step);
				try {
					StepSearchContext searchContext = state.searchContextFor(step).orElse(step.searchContext());
					String observation = researcherAgent.research(state.query(), step, searchContext);
					step.executionRes(observation);
					markStepCompleted(state, step);
					state.addObservation(observation);

					events.add(stepEvent(state, step, "step_completed", step.executionStatus(),
							"Completed: " + step.title(), Map.of("step", step, "observation", observation)));
				}
				catch (RuntimeException ex) {
					String errorMessage = errorMessage(ex);
					markStepFailed(state, step, errorMessage);
					events.add(stepEvent(state, step, "failed", step.executionStatus(), "Failed: " + step.title(),
							Map.of("step", step, "error", errorMessage)));
					return Flux.concat(Flux.fromIterable(events), Flux.error(ex));
				}
			}

			events.add(ResearchEvent.message(state.threadId(), name(), "completed", "Research steps completed",
					state.observations()));
			return Flux.fromIterable(events);
		});
	}

	private List<ResearchStep> runnableSteps(ResearchState state, ResearchTeamDecision decision) {
		StepType stepType = executorNode ? StepType.RESEARCH : decision.nextStepType();
		List<ResearchStep> steps = state.plan()
			.steps()
			.stream()
			.filter(step -> stepType.equals(step.stepType()))
			.filter(step -> !isTerminal(step))
			.toList();
		if (!executorNode) {
			return steps;
		}
		return steps.stream()
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
		if (!executorNode || step.attempt() == 0) {
			step.incrementAttempt();
		}
		step.startedAt(Instant.now());
		step.completedAt(null);
		step.error(null);
		step.executionStatus(executorNode ? StepExecutionStatus.processing(name()) : ResearchStep.STATUS_PROCESSING);
		state.recordNodeStarted(name(), step);
	}

	private void markStepCompleted(ResearchState state, ResearchStep step) {
		step.completedAt(Instant.now());
		step.error(null);
		step.executionStatus(executorNode ? StepExecutionStatus.completed(name()) : ResearchStep.STATUS_COMPLETED);
		state.recordNodeCompleted(name());
	}

	private void markStepFailed(ResearchState state, ResearchStep step, String errorMessage) {
		step.completedAt(Instant.now());
		step.error(errorMessage);
		step.executionStatus(
				executorNode ? StepExecutionStatus.error(name()) : StepExecutionStatus.legacyError(errorMessage));
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

}
