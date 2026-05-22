package top.lanshan.manmu.node;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import top.lanshan.manmu.model.BackgroundInvestigationPayload;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.SiteInformation;
import top.lanshan.manmu.search.WebSearchClient;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BackgroundInvestigatorNodeTest {

	@Test
	void searchesBoundedOptimizedQueriesAndStoresContext() {
		RecordingSearchClient searchClient = new RecordingSearchClient();
		BackgroundInvestigatorNode node = new BackgroundInvestigatorNode(searchClient);
		ResearchState state = ResearchState.from(new ResearchRequest("Explain workflow.", "thread-1", 3));
		state.optimizedQueries(List.of("Explain workflow.", "DeepResearch planner context", "Bocha search", "Ignored"));

		StepVerifier.create(node.run(state))
			.assertNext(event -> {
				assertThat(event.node()).isEqualTo("background_investigator");
				assertThat(event.phase()).isEqualTo("started");
				BackgroundInvestigationPayload payload = (BackgroundInvestigationPayload) event.payload();
				assertThat(payload.queries())
					.containsExactly("Explain workflow.", "DeepResearch planner context", "Bocha search");
			})
			.assertNext(event -> assertThat(event.phase()).isEqualTo("search_completed"))
			.assertNext(event -> assertThat(event.phase()).isEqualTo("search_completed"))
			.assertNext(event -> assertThat(event.phase()).isEqualTo("search_completed"))
			.assertNext(event -> {
				assertThat(event.phase()).isEqualTo("completed");
				BackgroundInvestigationPayload payload = (BackgroundInvestigationPayload) event.payload();
				assertThat(payload.searchResults()).hasSize(3);
				assertThat(payload.siteInformation()).hasSize(3);
				assertThat(payload.backgroundContext()).contains("Background query 1").contains("Result for Bocha search");
			})
			.verifyComplete();

		assertThat(searchClient.queries)
			.containsExactly("Explain workflow.", "DeepResearch planner context", "Bocha search");
		assertThat(state.backgroundInvestigationCompleted()).isTrue();
		assertThat(state.backgroundInvestigationResults()).hasSize(3);
		assertThat(state.backgroundInvestigationContext()).contains("Background search query: Explain workflow.");
	}

	@Test
	void fallsBackToOriginalQueryWhenOptimizedQueriesAreEmpty() {
		RecordingSearchClient searchClient = new RecordingSearchClient();
		BackgroundInvestigatorNode node = new BackgroundInvestigatorNode(searchClient);
		ResearchState state = ResearchState.from(new ResearchRequest("Original question", "thread-1", 3, 0));

		StepVerifier.create(node.run(state))
			.expectNextCount(2)
			.assertNext(event -> {
				assertThat(event.phase()).isEqualTo("completed");
				BackgroundInvestigationPayload payload = (BackgroundInvestigationPayload) event.payload();
				assertThat(payload.queries()).containsExactly("Original question");
			})
			.verifyComplete();

		assertThat(searchClient.queries).containsExactly("Original question");
		assertThat(state.backgroundInvestigationContext()).contains("Original question");
	}

	@Test
	void degradesWhenSearchFailsWithoutInventingFacts() {
		FailingSearchClient searchClient = new FailingSearchClient();
		BackgroundInvestigatorNode node = new BackgroundInvestigatorNode(searchClient);
		ResearchState state = ResearchState.from(new ResearchRequest("Original question", "thread-1", 3));
		state.optimizedQueries(List.of("Original question"));

		StepVerifier.create(node.run(state))
			.assertNext(event -> assertThat(event.phase()).isEqualTo("started"))
			.assertNext(event -> assertThat(event.phase()).isEqualTo("search_degraded"))
			.assertNext(event -> {
				assertThat(event.phase()).isEqualTo("degraded");
				BackgroundInvestigationPayload payload = (BackgroundInvestigationPayload) event.payload();
				assertThat(payload.searchResults()).isEmpty();
				assertThat(payload.backgroundContext()).contains("could not retrieve web search results");
				assertThat(payload.reason()).contains("search unavailable");
			})
			.verifyComplete();

		assertThat(state.backgroundInvestigationCompleted()).isTrue();
		assertThat(state.backgroundInvestigationResults()).isEmpty();
		assertThat(state.backgroundInvestigationContext()).contains("Failures").contains("search unavailable");
	}

	private static class RecordingSearchClient implements WebSearchClient {

		private final List<String> queries = new ArrayList<>();

		@Override
		public List<SiteInformation> search(String query) {
			queries.add(query);
			return List.of(new SiteInformation("Result for " + query, "https://example.com/" + queries.size(),
					"Snippet " + query, "Summary " + query, "Example", "", ""));
		}

	}

	private static class FailingSearchClient implements WebSearchClient {

		@Override
		public List<SiteInformation> search(String query) {
			throw new IllegalStateException("search unavailable");
		}

	}

}
