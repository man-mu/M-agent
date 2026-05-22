package top.lanshan.manmu.model;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record BackgroundInvestigationSearchResult(String query, List<SiteInformation> siteInformation) {

	public BackgroundInvestigationSearchResult {
		siteInformation = siteInformation == null ? List.of() : List.copyOf(siteInformation);
	}

	public String promptText() {
		if (siteInformation.isEmpty()) {
			return "No web search results were returned for this background query.";
		}
		String body = IntStream.range(0, siteInformation.size())
			.mapToObj(index -> "Source %d\n%s".formatted(index + 1, siteInformation.get(index).promptText()))
			.collect(Collectors.joining("\n\n"));
		return "Background search query: " + query + "\n\n" + body;
	}

}
