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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SimpleResearchRunner {

	private final List<ResearchNode> nodes;

	private final ResearchNode plannerNode;

	private final ResearchNode informationNode;

	private final ResearchNode researchTeamNode;

	private final ResearchNode researcherNode;

	private final ResearchNode processorNode;

	private final ResearchNode reporterNode;

	private final Map<String, ResearchState> pausedStates = new ConcurrentHashMap<>();

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
		return runToCompletion(state);
	}

	public Flux<ResearchEvent> runUntilPlanGate(ResearchRequest request) {
		ResearchState state = ResearchState.from(request);

		return plannerNode.run(state)
			.concatWith(Flux.defer(() -> {
				pausedStates.put(state.threadId(), state);
				return Flux.just(ResearchEvent.message(state.threadId(), "human_feedback", "waiting",
						"Waiting for human plan feedback", state.plan()));
			}))
			.subscribeOn(Schedulers.boundedElastic())
			.onErrorResume(error -> Flux.just(ResearchEvent.error(state.threadId(), "runner", error)));
	}

	public Flux<ResearchEvent> resume(String threadId, ResumeDecision decision) {
		ResearchState state = pausedStates.remove(threadId);
		if (state == null) {
			return Flux.just(ResearchEvent.error(threadId, "human_feedback",
					new IllegalArgumentException("No paused research state found for thread: " + threadId)));
		}
		if (decision.accepted()) {
			return resumeExecution(state).subscribeOn(Schedulers.boundedElastic());
		}
		state.planFeedback(decision.feedbackContent());
		return plannerNode.run(state)
			.concatWith(Flux.defer(() -> {
				pausedStates.put(state.threadId(), state);
				return Flux.just(ResearchEvent.message(state.threadId(), "human_feedback", "waiting",
						"Waiting for human plan feedback", state.plan()));
			}))
			.subscribeOn(Schedulers.boundedElastic())
			.onErrorResume(error -> Flux.just(ResearchEvent.error(state.threadId(), "runner", error)));
	}

	public boolean stop(String threadId) {
		if (threadId == null || threadId.isBlank()) {
			return false;
		}
		return pausedStates.remove(threadId) != null;
	}

	private Flux<ResearchEvent> runToCompletion(ResearchState state) {
		return Flux.concat(plannerNode.run(state), informationNode.run(state),
				Flux.defer(() -> researchLoop(state, state.plan().steps().size() + 1)), reporterNode.run(state))
			.subscribeOn(Schedulers.boundedElastic())
			.concatWith(Flux.defer(
					() -> Flux.just(ResearchEvent.done(state.threadId(), "Research workflow completed", state.report()))))
			.onErrorResume(error -> Flux.just(ResearchEvent.error(state.threadId(), "runner", error)));
	}

	private Flux<ResearchEvent> resumeExecution(ResearchState state) {
		return Flux.concat(informationNode.run(state),
				Flux.defer(() -> researchLoop(state, state.plan().steps().size() + 1)), reporterNode.run(state))
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
