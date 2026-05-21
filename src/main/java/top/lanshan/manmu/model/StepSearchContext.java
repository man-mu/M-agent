package top.lanshan.manmu.model;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record StepSearchContext(String stepTitle, String query, List<SiteInformation> results) {

	public StepSearchContext {
		results = results == null ? List.of() : List.copyOf(results);
	}

	public String promptText() {
		if (results.isEmpty()) {
			return "No web search results were returned for this step.";
		}
		String header = "Web search query: " + query;
		String body = IntStream.range(0, results.size())
			.mapToObj(index -> "Source %d\n%s".formatted(index + 1, results.get(index).promptText()))
			.collect(Collectors.joining("\n\n"));
		return header + "\n\n" + body;
	}

}
