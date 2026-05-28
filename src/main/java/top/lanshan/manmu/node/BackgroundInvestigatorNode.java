package top.lanshan.manmu.node;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.lanshan.manmu.model.BackgroundInvestigationPayload;
import top.lanshan.manmu.model.BackgroundInvestigationSearchResult;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.SiteInformation;
import top.lanshan.manmu.search.WebSearchClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class BackgroundInvestigatorNode implements ResearchNode {

	private static final int MAX_BACKGROUND_QUERIES = 3;

	private final WebSearchClient webSearchClient;

	public BackgroundInvestigatorNode(WebSearchClient webSearchClient) {
		this.webSearchClient = webSearchClient;
	}

	@Override
	public int order() {
		return 7;
	}

	@Override
	public String name() {
		return "background_investigator";
	}

	@Override
	public Flux<ResearchEvent> run(ResearchState state) {
		return Flux.defer(() -> {
			List<String> queries = searchQueries(state);
			List<BackgroundInvestigationSearchResult> results = new ArrayList<>();
			List<String> failures = new ArrayList<>();

			ResearchEvent started = ResearchEvent.message(state.threadId(), name(), "started",
					"Investigating current-question background",
					new BackgroundInvestigationPayload(queries, List.of(), ""));
			return Flux.concat(Flux.just(started),
					Flux.fromIterable(queries).concatMap(query -> search(state, query, results, failures)),
					Mono.fromSupplier(() -> complete(state, queries, results, failures)).flux());
		});
	}

	private Mono<ResearchEvent> search(ResearchState state, String query,
			List<BackgroundInvestigationSearchResult> results, List<String> failures) {
		return Mono.fromSupplier(() -> {
			try {
				List<SiteInformation> siteInformation = webSearchClient.search(query);
				BackgroundInvestigationSearchResult result = new BackgroundInvestigationSearchResult(query,
						siteInformation);
				results.add(result);
				return ResearchEvent.message(state.threadId(), name(), "search_completed",
						"Background search completed", new BackgroundInvestigationPayload(List.of(query),
								List.of(result), result.promptText()));
			}
			catch (RuntimeException error) {
				failures.add("%s: %s".formatted(query, errorMessage(error)));
				return ResearchEvent.message(state.threadId(), name(), "search_degraded",
						"Background search failed; continuing with visible degraded context",
						new BackgroundInvestigationPayload(List.of(query), List.of(), "", errorMessage(error)));
			}
		});
	}

	private ResearchEvent complete(ResearchState state, List<String> queries,
			List<BackgroundInvestigationSearchResult> results, List<String> failures) {
		String backgroundContext = backgroundContext(results, failures);
		state.backgroundInvestigationResults(results);
		state.backgroundInvestigationContext(backgroundContext);
		state.backgroundInvestigationCompleted(true);

		String finalPhase = results.isEmpty() && !failures.isEmpty() ? "degraded" : "completed";
		String reason = failures.isEmpty() ? null : String.join("\n", failures);
		return ResearchEvent.message(state.threadId(), name(), finalPhase,
				"Background investigation " + ("completed".equals(finalPhase) ? "completed" : "degraded"),
				new BackgroundInvestigationPayload(queries, results, backgroundContext, reason));
	}

	private List<String> searchQueries(ResearchState state) {
		LinkedHashSet<String> queries = new LinkedHashSet<>();
		state.optimizedQueries()
			.stream()
			.filter(query -> query != null && !query.isBlank())
			.map(String::strip)
			.forEach(queries::add);
		if (queries.isEmpty() && state.query() != null && !state.query().isBlank()) {
			queries.add(state.query().strip());
		}
		return queries.stream().limit(MAX_BACKGROUND_QUERIES).toList();
	}

	private String backgroundContext(List<BackgroundInvestigationSearchResult> results, List<String> failures) {
		if (results.isEmpty()) {
			if (failures.isEmpty()) {
				return "No current-question background investigation was performed.";
			}
			return "Current-question background investigation could not retrieve web search results.\nFailures:\n"
					+ bulletList(failures);
		}
		String context = IntStream.range(0, results.size())
			.mapToObj(index -> "Background query %d\n%s".formatted(index + 1, results.get(index).promptText()))
			.collect(Collectors.joining("\n\n"));
		if (failures.isEmpty()) {
			return context;
		}
		return context + "\n\nSome background searches failed and should not be treated as evidence:\n"
				+ bulletList(failures);
	}

	private String bulletList(List<String> values) {
		return values.stream().map(value -> "- " + value).collect(Collectors.joining("\n"));
	}

	private String errorMessage(RuntimeException error) {
		if (error.getMessage() != null && !error.getMessage().isBlank()) {
			return error.getMessage();
		}
		return error.getClass().getSimpleName();
	}

}
