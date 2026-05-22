package top.lanshan.manmu.node;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import top.lanshan.manmu.agent.QueryRewriteAgent;
import top.lanshan.manmu.model.QueryRewritePayload;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;

import java.util.List;

@Component
public class QueryRewriteNode implements ResearchNode {

	private final QueryRewriteAgent queryRewriteAgent;

	public QueryRewriteNode(QueryRewriteAgent queryRewriteAgent) {
		this.queryRewriteAgent = queryRewriteAgent;
	}

	@Override
	public int order() {
		return 5;
	}

	@Override
	public String name() {
		return "rewrite_multi_query";
	}

	@Override
	public Flux<ResearchEvent> run(ResearchState state) {
		return Flux.defer(() -> {
			try {
				List<String> optimizedQueries = queryRewriteAgent.rewrite(state.query(), state.optimizeQueryNum());
				state.optimizedQueries(optimizedQueries);
				state.queryRewriteCompleted(true);
				return Flux.just(ResearchEvent.message(state.threadId(), name(), "completed",
						"Optimized research queries generated",
						new QueryRewritePayload(state.query(), state.optimizedQueries())));
			}
			catch (RuntimeException error) {
				return degraded(state, error);
			}
		});
	}

	private Flux<ResearchEvent> degraded(ResearchState state, RuntimeException error) {
		if (state.optimizeQueryNum() == 0) {
			state.optimizedQueries(List.of());
		}
		else {
			state.optimizedQueries(List.of(state.query()));
		}
		state.queryRewriteCompleted(true);
		return Flux.just(ResearchEvent.message(state.threadId(), name(), "degraded",
				"Query rewrite failed; continuing with the original query",
				new QueryRewritePayload(state.query(), state.optimizedQueries(), error.getMessage())));
	}

}
