package top.lanshan.manmu.model;

import java.util.List;

public record BackgroundInvestigationPayload(List<String> queries,
		List<BackgroundInvestigationSearchResult> searchResults, String backgroundContext, String reason) {

	public BackgroundInvestigationPayload {
		queries = queries == null ? List.of() : List.copyOf(queries);
		searchResults = searchResults == null ? List.of() : List.copyOf(searchResults);
	}

	public BackgroundInvestigationPayload(List<String> queries,
			List<BackgroundInvestigationSearchResult> searchResults, String backgroundContext) {
		this(queries, searchResults, backgroundContext, null);
	}

	public List<SiteInformation> siteInformation() {
		return searchResults.stream().flatMap(result -> result.siteInformation().stream()).toList();
	}

}
