package top.lanshan.manmu.node;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import top.lanshan.manmu.model.HumanFeedbackDecision;
import top.lanshan.manmu.model.HumanFeedbackRoute;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;

@Component
public class HumanFeedbackNode implements ResearchNode {

	@Override
	public int order() {
		return 17;
	}

	@Override
	public String name() {
		return "human_feedback";
	}

	@Override
	public Flux<ResearchEvent> run(ResearchState state) {
		return Flux.defer(() -> {
			HumanFeedbackDecision decision = decide(state);
			state.humanFeedbackDecision(decision);
			if (HumanFeedbackRoute.WAITING.equals(decision.nextRoute())) {
				return Flux.just(ResearchEvent.message(state.threadId(), name(), "waiting",
						"Waiting for human plan feedback", state.plan()));
			}
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "decision",
					"Human feedback routed to " + decision.nextRoute().name().toLowerCase(), decision));
		});
	}

	HumanFeedbackDecision decide(ResearchState state) {
		Boolean accepted = state.humanFeedbackAccepted();
		String feedbackContent = state.humanFeedbackContent();
		if (accepted == null) {
			return new HumanFeedbackDecision(HumanFeedbackRoute.WAITING, null, null, state.planIterations(),
					state.maxPlanIterations(), "Waiting for human plan feedback");
		}

		state.clearHumanFeedbackInput();
		if (accepted) {
			return new HumanFeedbackDecision(HumanFeedbackRoute.RESEARCH_TEAM, true, null,
					state.planIterations(), state.maxPlanIterations(), "Plan accepted");
		}
		if (state.planIterations() >= state.maxPlanIterations()) {
			return new HumanFeedbackDecision(HumanFeedbackRoute.RESEARCH_TEAM, false, feedbackContent,
					state.planIterations(), state.maxPlanIterations(), "Maximum plan iterations reached");
		}
		state.planFeedback(StringUtils.hasText(feedbackContent) ? feedbackContent.strip() : null);
		return new HumanFeedbackDecision(HumanFeedbackRoute.PLANNER, false, state.planFeedback(),
				state.planIterations(), state.maxPlanIterations(), "Plan rejected");
	}

}
