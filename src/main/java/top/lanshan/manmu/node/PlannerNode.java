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
			ResearchPlan plan = plannerAgent.plan(state.query(), state.maxSteps());
			state.plan(plan);
			return Flux.just(
					ResearchEvent.message(state.threadId(), name(), "started", "Planning research steps", null),
					ResearchEvent.message(state.threadId(), name(), "completed", "Plan generated", plan));
		});
	}

}
