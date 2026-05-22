package top.lanshan.manmu.node;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import top.lanshan.manmu.agent.QueryRewriteAgent;
import top.lanshan.manmu.model.QueryRewritePayload;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryRewriteNodeTest {

	@Test
	void storesOptimizedQueriesAndEmitsPayload() {
		QueryRewriteNode node = new QueryRewriteNode((query, count) -> List.of(query, "Variant query"));
		ResearchState state = ResearchState.from(new ResearchRequest("Original question", "thread-1", 3));

		StepVerifier.create(node.run(state)).assertNext(event -> {
			assertThat(event.node()).isEqualTo("rewrite_multi_query");
			assertThat(event.phase()).isEqualTo("completed");
			assertThat(event.payload()).isInstanceOf(QueryRewritePayload.class);
			QueryRewritePayload payload = (QueryRewritePayload) event.payload();
			assertThat(payload.optimizedQueries()).containsExactly("Original question", "Variant query");
		}).verifyComplete();

		assertThat(state.queryRewriteCompleted()).isTrue();
		assertThat(state.optimizedQueries()).containsExactly("Original question", "Variant query");
	}

	@Test
	void degradesToOriginalQueryWhenAgentFails() {
		QueryRewriteNode node = new QueryRewriteNode(new FailingQueryRewriteAgent());
		ResearchState state = ResearchState.from(new ResearchRequest("Original question", "thread-1", 3));

		StepVerifier.create(node.run(state)).assertNext(event -> {
			assertThat(event.node()).isEqualTo("rewrite_multi_query");
			assertThat(event.phase()).isEqualTo("degraded");
			QueryRewritePayload payload = (QueryRewritePayload) event.payload();
			assertThat(payload.optimizedQueries()).containsExactly("Original question");
			assertThat(payload.reason()).contains("provider unavailable");
		}).verifyComplete();

		assertThat(state.queryRewriteCompleted()).isTrue();
		assertThat(state.optimizedQueries()).containsExactly("Original question");
	}

	@Test
	void degradesToEmptyQueriesWhenOptimizationDisabled() {
		QueryRewriteNode node = new QueryRewriteNode(new FailingQueryRewriteAgent());
		ResearchState state = ResearchState.from(new ResearchRequest("Original question", "thread-1", 3, 0));

		StepVerifier.create(node.run(state)).assertNext(event -> {
			assertThat(event.phase()).isEqualTo("degraded");
			QueryRewritePayload payload = (QueryRewritePayload) event.payload();
			assertThat(payload.optimizedQueries()).isEmpty();
		}).verifyComplete();

		assertThat(state.optimizedQueries()).isEmpty();
	}

	private static class FailingQueryRewriteAgent implements QueryRewriteAgent {

		@Override
		public List<String> rewrite(String query, int optimizeQueryNum) {
			throw new IllegalStateException("provider unavailable");
		}

	}

}
