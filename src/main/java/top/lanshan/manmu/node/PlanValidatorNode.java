package top.lanshan.manmu.node;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import top.lanshan.manmu.model.PlanValidatorDecision;
import top.lanshan.manmu.model.PlanValidatorRoute;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;

@Component
public class PlanValidatorNode implements ResearchNode {

	@Override
	public int order() {
		return 15;
	}

	@Override
	public String name() {
		return "plan_validator";
	}

	@Override
	public Flux<ResearchEvent> run(ResearchState state) {
		return Flux.defer(() -> {
			PlanValidatorDecision decision = decide(state);
			state.planValidatorDecision(decision);
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "decision",
					"Plan validator routed to " + decision.nextRoute().name().toLowerCase(), decision));
		});
	}

	PlanValidatorDecision decide(ResearchState state) {
		String invalidReason = invalidReason(state);
		if (invalidReason == null) {
			PlanValidatorRoute route = state.autoAcceptedPlan() ? PlanValidatorRoute.RESEARCH_TEAM
					: PlanValidatorRoute.HUMAN_FEEDBACK;
			return new PlanValidatorDecision(route, true, state.planIterations(), state.maxPlanIterations(),
					"Valid plan");
		}

		PlanValidatorRoute route = state.planIterations() < state.maxPlanIterations() ? PlanValidatorRoute.PLANNER
				: PlanValidatorRoute.FAILED;
		return new PlanValidatorDecision(route, false, state.planIterations(), state.maxPlanIterations(),
				invalidReason);
	}

	private String invalidReason(ResearchState state) {
		if (state.plannerError() != null && !state.plannerError().isBlank()) {
			return state.plannerError();
		}
		ResearchPlan plan = state.plan();
		if (plan == null) {
			return "Research plan is missing";
		}
		if (plan.steps() == null || plan.steps().isEmpty()) {
			return "Research plan has no steps";
		}
		for (ResearchStep step : plan.steps()) {
			if (step.title() == null || step.title().isBlank()) {
				return "Research plan contains a step with an empty title";
			}
			if (step.description() == null || step.description().isBlank()) {
				return "Research plan contains a step with an empty description";
			}
		}
		return null;
	}

}
