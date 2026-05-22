package top.lanshan.manmu.node;

import top.lanshan.manmu.agent.PlannerAgent;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchState;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class PlannerNode implements ResearchNode {

	private final PlannerAgent plannerAgent;

	public PlannerNode(PlannerAgent plannerAgent) {
		this.plannerAgent = plannerAgent;
	}

	@Override
	public int order() {
		return 10;
	}

	@Override
	public String name() {
		return "planner";
	}

	@Override
	public Flux<ResearchEvent> run(ResearchState state) {
		return Flux.defer(() -> {
			state.recordPlanAttempt();
			state.plannerError(null);
			ResearchEvent started =
					ResearchEvent.message(state.threadId(), name(), "started", "Planning research steps", null);
			try {
				ResearchPlan plan = plannerAgent.plan(state.query(), state.maxSteps(), state.planFeedback(),
						state.backgroundContext(), state.optimizedQueries(), state.backgroundInvestigationContext());
				state.plan(plan);
				return Flux.just(started,
						ResearchEvent.message(state.threadId(), name(), "completed", "Plan generated", plan));
			}
			catch (RuntimeException error) {
				state.plan(null);
				state.plannerError(errorMessage(error));
				return Flux.just(started, ResearchEvent.message(state.threadId(), name(), "failed",
						"Plan generation failed", state.plannerError()));
			}
		});
	}

	private String errorMessage(RuntimeException error) {
		if (error.getMessage() != null && !error.getMessage().isBlank()) {
			return error.getMessage();
		}
		return error.getClass().getSimpleName();
	}

}
