package top.lanshan.manmu.runner;

import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchTeamRoute;
import top.lanshan.manmu.node.ResearchNode;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.Comparator;
import java.util.List;

@Component
public class SimpleResearchRunner {

	private final List<ResearchNode> nodes;

	private final ResearchNode plannerNode;

	private final ResearchNode informationNode;

	private final ResearchNode researchTeamNode;

	private final ResearchNode researcherNode;

	private final ResearchNode processorNode;

	private final ResearchNode reporterNode;

	public SimpleResearchRunner(List<ResearchNode> nodes) {
		this.nodes = nodes.stream().sorted(Comparator.comparingInt(ResearchNode::order)).toList();
		this.plannerNode = requiredNode("planner");
		this.informationNode = requiredNode("information");
		this.researchTeamNode = requiredNode("research_team");
		this.researcherNode = requiredNode("researcher");
		this.processorNode = requiredNode("processor");
		this.reporterNode = requiredNode("reporter");
	}

	public Flux<ResearchEvent> run(ResearchRequest request) {
		ResearchState state = ResearchState.from(request);

		return Flux.concat(plannerNode.run(state), informationNode.run(state),
				Flux.defer(() -> researchLoop(state, state.plan().steps().size() + 1)), reporterNode.run(state))
			.subscribeOn(Schedulers.boundedElastic())
			.concatWith(Flux.defer(
					() -> Flux.just(ResearchEvent.done(state.threadId(), "Research workflow completed", state.report()))))
			.onErrorResume(error -> Flux.just(ResearchEvent.error(state.threadId(), "runner", error)));
	}

	private Flux<ResearchEvent> researchLoop(ResearchState state, int remainingCycles) {
		if (remainingCycles < 1) {
			return Flux.error(new IllegalStateException("Research team did not reach reporter route"));
		}
		return researchTeamNode.run(state).concatWith(Flux.defer(() -> {
			if (state.researchTeamDecision().nextRoute() == ResearchTeamRoute.REPORTER) {
				return Flux.empty();
			}
			ResearchNode executorNode = switch (state.researchTeamDecision().nextRoute()) {
				case RESEARCHER -> researcherNode;
				case PROCESSOR -> processorNode;
				case REPORTER -> reporterNode;
			};
			return executorNode.run(state).concatWith(researchLoop(state, remainingCycles - 1));
		}));
	}

	private ResearchNode requiredNode(String name) {
		return nodes.stream()
			.filter(node -> name.equals(node.name()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Missing research node: " + name));
	}

}
