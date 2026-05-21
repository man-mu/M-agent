package top.lanshan.manmu.node;

import top.lanshan.manmu.agent.ResearcherAgent;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
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
		return 20;
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

			List<ResearchEvent> events = new ArrayList<>();
			events.add(ResearchEvent.message(state.threadId(), name(), "started", "Executing research steps", null));

			for (ResearchStep step : state.plan().steps()) {
				String observation = researcherAgent.research(state.query(), step);
				state.addObservation(observation);

				events.add(ResearchEvent.message(state.threadId(), name(), "step_completed",
						"Completed: " + step.title(), Map.of("step", step, "observation", observation)));
			}

			events.add(ResearchEvent.message(state.threadId(), name(), "completed", "Research steps completed",
					state.observations()));
			return Flux.fromIterable(events);
		});
	}

}
