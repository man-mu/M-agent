package top.lanshan.manmu.node;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.SiteInformation;
import top.lanshan.manmu.model.StepType;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InformationNodeTest {

	@Test
	void searchesOnlyStepsThatNeedWebSearch() {
		RecordingSearchClient searchClient = new RecordingSearchClient(
				List.of(new SiteInformation("Bocha docs", "https://open.bochaai.com", "Snippet", "Summary", "Bocha",
						"", "")));
		InformationNode node = new InformationNode(searchClient);
		ResearchStep searchStep = new ResearchStep("Find current API docs", "Look up Bocha web search parameters.",
				true, StepType.RESEARCH, null, ResearchStep.STATUS_PENDING);
		ResearchStep localStep = new ResearchStep("Summarize architecture", "Use the repository context.", false,
				StepType.PROCESSING, null, ResearchStep.STATUS_PENDING);
		ResearchState state = stateWithPlan(List.of(searchStep, localStep));

		StepVerifier.create(node.run(state))
			.assertNext(event -> assertThat(event.phase()).isEqualTo("started"))
			.assertNext(event -> assertThat(event.phase()).isEqualTo("search_completed"))
			.assertNext(event -> assertThat(event.phase()).isEqualTo("completed"))
			.verifyComplete();

		assertThat(searchClient.queries).hasSize(1);
		assertThat(searchClient.queries.get(0)).contains("Explain search integration")
			.contains("Find current API docs")
			.contains("Look up Bocha web search parameters.");
		assertThat(searchStep.searchContext()).isNotNull();
		assertThat(searchStep.searchContext().results()).hasSize(1);
		assertThat(localStep.searchContext()).isNull();
		assertThat(state.searchContexts()).hasSize(1);
		assertThat(state.siteInformation()).extracting(SiteInformation::url).containsExactly("https://open.bochaai.com");
	}

	@Test
	void emitsStartedAndCompletedWhenNoStepNeedsSearch() {
		RecordingSearchClient searchClient = new RecordingSearchClient(List.of());
		InformationNode node = new InformationNode(searchClient);
		ResearchState state = stateWithPlan(List.of(new ResearchStep("Summarize architecture",
				"Use the repository context.", false, StepType.PROCESSING, null, ResearchStep.STATUS_PENDING)));

		StepVerifier.create(node.run(state))
			.assertNext(event -> assertThat(event.phase()).isEqualTo("started"))
			.assertNext(event -> assertThat(event.phase()).isEqualTo("completed"))
			.verifyComplete();

		assertThat(searchClient.queries).isEmpty();
		assertThat(state.siteInformation()).isEmpty();
	}

	private ResearchState stateWithPlan(List<ResearchStep> steps) {
		ResearchState state = ResearchState.from(new ResearchRequest("Explain search integration.", "thread-1", 3));
		state.plan(new ResearchPlan("Search plan", true, "Use web search where needed.", steps));
		return state;
	}

	private static class RecordingSearchClient implements top.lanshan.manmu.search.WebSearchClient {

		private final List<SiteInformation> results;

		private final List<String> queries = new ArrayList<>();

		private RecordingSearchClient(List<SiteInformation> results) {
			this.results = results;
		}

		@Override
		public List<SiteInformation> search(String query) {
			queries.add(query);
			return results;
		}

	}

}
