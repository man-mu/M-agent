package top.lanshan.manmu.node;

import top.lanshan.manmu.agent.ResearcherAgent;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.ResearchTeamDecision;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ResearcherNode implements ResearchNode {

	private final ResearcherAgent researcherAgent;

	public ResearcherNode(ResearcherAgent researcherAgent) {
		this.researcherAgent = researcherAgent;
	}

	@Override
	public int order() {
		return 30;
	}

	@Override
	public String name() {
		return "researcher";
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
			List<ResearchStep> steps = state.plan()
				.steps()
				.stream()
				.filter(step -> decision.nextStepType().equals(step.stepType()))
				.filter(step -> !isTerminal(step))
				.toList();
			if (steps.isEmpty()) {
				return Flux.error(new IllegalStateException(
						"No pending " + decision.nextStepType().name().toLowerCase() + " steps are available"));
			}

			events.add(ResearchEvent.message(state.threadId(), name(), "started",
					"Executing " + decision.nextStepType().name().toLowerCase() + " steps", decision));

			for (ResearchStep step : steps) {
				step.executionStatus(ResearchStep.STATUS_PROCESSING);
				try {
					String observation = researcherAgent.research(state.query(), step);
					step.executionRes(observation);
					step.executionStatus(ResearchStep.STATUS_COMPLETED);
					state.addObservation(observation);

					events.add(ResearchEvent.message(state.threadId(), name(), "step_completed",
							"Completed: " + step.title(), Map.of("step", step, "observation", observation)));
				}
				catch (RuntimeException ex) {
					step.executionStatus(ResearchStep.STATUS_ERROR + ": " + ex.getMessage());
					events.add(ResearchEvent.message(state.threadId(), name(), "step_error",
							"Failed: " + step.title(), Map.of("step", step, "error", ex.getMessage())));
					throw ex;
				}
			}

			events.add(ResearchEvent.message(state.threadId(), name(), "completed", "Research steps completed",
					state.observations()));
			return Flux.fromIterable(events);
		});
	}

	private boolean isTerminal(ResearchStep step) {
		return hasStatusPrefix(step, ResearchStep.STATUS_COMPLETED)
				|| hasStatusPrefix(step, ResearchStep.STATUS_ERROR);
	}

	private boolean hasStatusPrefix(ResearchStep step, String statusPrefix) {
		return step.executionStatus() != null && step.executionStatus().startsWith(statusPrefix);
	}

}
