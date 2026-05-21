package top.lanshan.manmu.runner;

import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.node.ResearchNode;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.Comparator;
import java.util.List;

@Component
public class SimpleResearchRunner {

	private final List<ResearchNode> nodes;

	public SimpleResearchRunner(List<ResearchNode> nodes) {
		this.nodes = nodes.stream().sorted(Comparator.comparingInt(ResearchNode::order)).toList();
	}

	public Flux<ResearchEvent> run(ResearchRequest request) {
		ResearchState state = ResearchState.from(request);
		List<Flux<ResearchEvent>> nodeStreams = nodes.stream().map(node -> node.run(state)).toList();

		return Flux.concat(nodeStreams)
			.subscribeOn(Schedulers.boundedElastic())
			.concatWith(Flux.defer(
					() -> Flux.just(ResearchEvent.done(state.threadId(), "Research workflow completed", state.report()))))
			.onErrorResume(error -> Flux.just(ResearchEvent.error(state.threadId(), "runner", error)));
	}

}
