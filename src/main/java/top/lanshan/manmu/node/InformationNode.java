package top.lanshan.manmu.node;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import top.lanshan.manmu.model.InformationPayload;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.SiteInformation;
import top.lanshan.manmu.model.StepSearchContext;
import top.lanshan.manmu.search.WebSearchClient;

import java.util.ArrayList;
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

			List<ResearchEvent> events = new ArrayList<>();
			events.add(ResearchEvent.message(state.threadId(), name(), "started",
					"Collecting web information for " + searchableSteps.size() + " planned steps", null));

			for (ResearchStep step : searchableSteps) {
				String searchQuery = searchQuery(state.query(), step);
				List<SiteInformation> results = webSearchClient.search(searchQuery);
				StepSearchContext searchContext = new StepSearchContext(step.title(), searchQuery, results);
				step.searchContext(searchContext);
				state.addSearchContext(searchContext);

				events.add(ResearchEvent.message(state.threadId(), name(), "search_completed",
						"Search completed: " + step.title(), new InformationPayload(searchQuery, results)));
			}

			events.add(ResearchEvent.message(state.threadId(), name(), "completed",
					"Information gathering completed", state.siteInformation()));
			return Flux.fromIterable(events);
		});
	}

	private String searchQuery(String userQuery, ResearchStep step) {
		return """
				%s

				%s
				%s
				""".formatted(userQuery, step.title(), step.description()).strip();
	}

}
