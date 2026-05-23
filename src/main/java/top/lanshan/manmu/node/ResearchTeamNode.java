package top.lanshan.manmu.node;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.ResearchTeamDecision;
import top.lanshan.manmu.model.ResearchTeamRoute;
import top.lanshan.manmu.model.StepExecutionStatus;
import top.lanshan.manmu.model.StepType;

import java.util.List;

@Component
public class ResearchTeamNode implements ResearchNode {

	@Override
	public int order() {
		return 30;
	}

	@Override
	public String name() {
		return "research_team";
	}

	@Override
	public Flux<ResearchEvent> run(ResearchState state) {
		return Flux.defer(() -> {
			ResearchTeamDecision decision = decide(state.plan());
			state.researchTeamDecision(decision);
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "decision",
					"Research team routed to " + decision.nextRoute().name().toLowerCase(), decision));
		});
	}

	ResearchTeamDecision decide(ResearchPlan plan) {
		if (plan == null) {
			throw new IllegalStateException("Research plan is missing");
		}
		if (plan.steps() == null || plan.steps().isEmpty()) {
			throw new IllegalStateException("Research plan has no steps");
		}

		List<ResearchStep> steps = plan.steps();
		int completedSteps = countCompleted(steps);
		int errorSteps = countErrors(steps);
		int terminalSteps = completedSteps + errorSteps;
		int remainingSteps = steps.size() - terminalSteps;

		if (remainingSteps == 0) {
			return new ResearchTeamDecision(ResearchTeamRoute.REPORTER, null, steps.size(), completedSteps, errorSteps,
					remainingSteps);
		}

		StepType nextStepType = steps.stream()
			.filter(step -> !isTerminal(step))
			.map(ResearchStep::stepType)
			.filter(StepType.RESEARCH::equals)
			.findFirst()
			.orElse(StepType.PROCESSING);
		ResearchTeamRoute nextRoute = StepType.PROCESSING.equals(nextStepType) ? ResearchTeamRoute.PROCESSOR
				: ResearchTeamRoute.RESEARCHER;
		return new ResearchTeamDecision(nextRoute, nextStepType, steps.size(), completedSteps,
				errorSteps, remainingSteps);
	}

	private int countCompleted(List<ResearchStep> steps) {
		return (int) steps.stream().filter(StepExecutionStatus::isCompleted).count();
	}

	private int countErrors(List<ResearchStep> steps) {
		return (int) steps.stream().filter(StepExecutionStatus::isError).count();
	}

	private boolean isTerminal(ResearchStep step) {
		return StepExecutionStatus.isTerminal(step);
	}

}
