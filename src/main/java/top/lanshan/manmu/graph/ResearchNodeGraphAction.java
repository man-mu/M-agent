package top.lanshan.manmu.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.node.ResearchNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ResearchNodeGraphAction implements NodeAction {

	private final ResearchNode node;

	public ResearchNodeGraphAction(ResearchNode node) {
		this.node = Objects.requireNonNull(node, "node must not be null");
	}

	public static ResearchNodeGraphAction from(ResearchNode node) {
		return new ResearchNodeGraphAction(node);
	}

	public static AsyncNodeAction async(ResearchNode node) {
		return AsyncNodeAction.node_async(from(node));
	}

	public AsyncNodeAction async() {
		return AsyncNodeAction.node_async(this);
	}

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		Objects.requireNonNull(state, "state must not be null");
		return apply(mutableGraphState(state));
	}

	public Map<String, Object> apply(Map<String, Object> graphState) {
		Objects.requireNonNull(graphState, "graphState must not be null");
		ResearchState state = ResearchGraphState.researchState(graphState);
		List<ResearchEvent> events = Objects.requireNonNull(node.run(state), "node result must not be null")
			.doOnNext(state::emitLiveEvent)
			.collectList()
			.block();
		if (events != null) {
			ResearchGraphState.appendEvents(graphState, events);
		}
		ResearchGraphState.setResearchState(graphState, state);
		return graphState;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> mutableGraphState(OverAllState state) {
		Map<String, Object> graphState = new LinkedHashMap<>(state.data());
		Object events = graphState.get(ResearchGraphStateKeys.EVENTS);
		if (events == null) {
			return graphState;
		}
		if (events instanceof Collection<?> eventCollection) {
			for (Object event : eventCollection) {
				if (!(event instanceof ResearchEvent)) {
					throw new IllegalStateException("Graph state value '" + ResearchGraphStateKeys.EVENTS
							+ "' contains a non-ResearchEvent value");
				}
			}
			graphState.put(ResearchGraphStateKeys.EVENTS,
					new ArrayList<>((Collection<ResearchEvent>) eventCollection));
			return graphState;
		}
		throw new IllegalStateException("Graph state value '" + ResearchGraphStateKeys.EVENTS + "' is not a List");
	}

}
