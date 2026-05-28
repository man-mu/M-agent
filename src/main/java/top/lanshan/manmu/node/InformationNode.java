package top.lanshan.manmu.node;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.lanshan.manmu.model.InformationPayload;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.SiteInformation;
import top.lanshan.manmu.model.StepSearchContext;
import top.lanshan.manmu.search.WebSearchClient;

import java.util.List;

@Component
public class InformationNode implements ResearchNode {

	private final WebSearchClient webSearchClient;

	public InformationNode(WebSearchClient webSearchClient) {
		this.webSearchClient = webSearchClient;
	}

	@Override
	public int order() {
		return 20;
	}

	@Override
	public String name() {
		return "information";
	}

	@Override
	public Flux<ResearchEvent> run(ResearchState state) {
		return Flux.defer(() -> {
			if (state.plan() == null) {
				return Flux.error(new IllegalStateException("Research plan is missing"));
			}

			List<ResearchStep> searchableSteps = state.plan()
				.steps()
				.stream()
				.filter(ResearchStep::needWebSearch)
				.toList();

			ResearchEvent started = ResearchEvent.message(state.threadId(), name(), "started",
					"Collecting web information for " + searchableSteps.size() + " planned steps", null);
			return Flux.concat(Flux.just(started),
					Flux.fromIterable(searchableSteps).concatMap(step -> search(state, step)),
					Mono.fromSupplier(() -> ResearchEvent.message(state.threadId(), name(), "completed",
							"Information gathering completed", state.siteInformation())).flux());
		});
	}

	private Mono<ResearchEvent> search(ResearchState state, ResearchStep step) {
		return Mono.fromSupplier(() -> {
			String searchQuery = searchQuery(state, step);
			List<SiteInformation> results = webSearchClient.search(searchQuery);
			StepSearchContext searchContext = new StepSearchContext(step.title(), searchQuery, results);
			step.searchContext(searchContext);
			state.addSearchContext(searchContext);

			return ResearchEvent.message(state.threadId(), name(), "search_completed",
					"Search completed: " + step.title(), new InformationPayload(searchQuery, results));
		});
	}

	private String searchQuery(ResearchState state, ResearchStep step) {
		String optimizedQueries = optimizedQueriesText(state);
		return """
				%s
				%s

				%s
				%s
				""".formatted(state.query(), optimizedQueries, step.title(), step.description()).strip();
	}

	private String optimizedQueriesText(ResearchState state) {
		if (state.optimizedQueries().isEmpty()) {
			return "";
		}
		return """

				Optimized queries:
				%s
				""".formatted(String.join("\n", state.optimizedQueries()));
	}

}
