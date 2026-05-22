package top.lanshan.manmu.graph;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.node.ResearchNode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResearchNodeGraphActionTest {

	@Test
	void invokesResearchNodeWithResearchStateAndAppendsEvents() {
		Map<String, Object> graphState = ResearchGraphState.from(new ResearchRequest("Adapt nodes", "thread-1", 2));
		ResearchState expectedState = ResearchGraphState.researchState(graphState);
		ResearchEvent first = ResearchEvent.message("thread-1", "stub", "started", "started", null);
		ResearchEvent second = ResearchEvent.message("thread-1", "stub", "completed", "completed", null);
		CapturingNode node = new CapturingNode(first, second);

		Map<String, Object> result = new ResearchNodeGraphAction(node).apply(graphState);

		assertThat(node.seenState()).isSameAs(expectedState);
		assertThat(result).isSameAs(graphState);
		assertThat(ResearchGraphState.researchState(result)).isSameAs(expectedState);
		assertThat(ResearchGraphState.events(result)).containsExactly(first, second);
	}

	@Test
	void preservesMutationsMadeByResearchNode() {
		Map<String, Object> graphState = ResearchGraphState.from(new ResearchRequest("Mutate state", "thread-2", 1));
		ResearchNode node = new StubNode() {
			@Override
			public Flux<ResearchEvent> run(ResearchState state) {
				state.addObservation("node observation");
				state.report("node report");
				return Flux.just(ResearchEvent.message(state.threadId(), name(), "done", "mutated", null));
			}
		};

		Map<String, Object> result = ResearchNodeGraphAction.from(node).apply(graphState);
		ResearchState state = ResearchGraphState.researchState(result);

		assertThat(state.observations()).containsExactly("node observation");
		assertThat(state.report()).isEqualTo("node report");
		assertThat(ResearchGraphState.events(result)).extracting(ResearchEvent::content).containsExactly("mutated");
	}

	@Test
	void invokesThroughGraphOverallState() throws Exception {
		Map<String, Object> graphState = ResearchGraphState.from(new ResearchRequest("Overall state", "thread-3", 1));
		ResearchEvent event = ResearchEvent.message("thread-3", "stub", "done", "from overall state", null);
		ResearchNodeGraphAction action = ResearchNodeGraphAction.from(new CapturingNode(event));

		Map<String, Object> result = action.apply(new OverAllState(graphState));

		assertThat(result).isNotSameAs(graphState);
		assertThat(ResearchGraphState.events(result)).containsExactly(event);
		assertThat(ResearchGraphState.events(graphState)).isEmpty();
	}

	@Test
	void exposesAsyncActionForStateGraphNodes() {
		Map<String, Object> graphState = ResearchGraphState.from(new ResearchRequest("Async action", "thread-4", 1));
		ResearchEvent event = ResearchEvent.message("thread-4", "stub", "done", "async", null);
		AsyncNodeAction action = ResearchNodeGraphAction.async(new CapturingNode(event));

		Map<String, Object> result = action.apply(new OverAllState(graphState)).join();

		assertThat(ResearchGraphState.events(result)).containsExactly(event);
		assertThat(ResearchGraphState.events(graphState)).isEmpty();
	}

	@Test
	void propagatesNodeFailures() {
		Map<String, Object> graphState = ResearchGraphState.from(new ResearchRequest("Failure", "thread-5", 1));
		ResearchNodeGraphAction action = ResearchNodeGraphAction.from(new StubNode() {
			@Override
			public Flux<ResearchEvent> run(ResearchState state) {
				return Flux.error(new IllegalStateException("node failed"));
			}
		});

		assertThatThrownBy(() -> action.apply(graphState))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("node failed");
		assertThat(ResearchGraphState.events(graphState)).isEmpty();
	}

	@Test
	void asyncActionPropagatesNodeFailures() {
		Map<String, Object> graphState = ResearchGraphState.from(new ResearchRequest("Async failure", "thread-6", 1));
		AsyncNodeAction action = ResearchNodeGraphAction.from(new StubNode() {
			@Override
			public Flux<ResearchEvent> run(ResearchState state) {
				return Flux.error(new IllegalStateException("async node failed"));
			}
		}).async();

		assertThatThrownBy(() -> action.apply(new OverAllState(graphState)).join())
			.isInstanceOf(CompletionException.class)
			.hasCauseInstanceOf(IllegalStateException.class)
			.hasMessageContaining("async node failed");
		assertThat(ResearchGraphState.events(graphState)).isEmpty();
	}

	private static class CapturingNode extends StubNode {

		private final List<ResearchEvent> events;

		private ResearchState seenState;

		CapturingNode(ResearchEvent... events) {
			this.events = List.of(events);
		}

		@Override
		public Flux<ResearchEvent> run(ResearchState state) {
			this.seenState = state;
			return Flux.fromIterable(events);
		}

		ResearchState seenState() {
			return seenState;
		}

	}

	private static class StubNode implements ResearchNode {

		@Override
		public int order() {
			return 0;
		}

		@Override
		public String name() {
			return "stub";
		}

		@Override
		public Flux<ResearchEvent> run(ResearchState state) {
			return Flux.empty();
		}

	}

}
