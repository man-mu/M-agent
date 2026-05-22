package top.lanshan.manmu.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CoordinatorDecision(@JsonProperty("next_route") CoordinatorRoute nextRoute,
		@JsonProperty("deep_research") boolean deepResearch,
		@JsonProperty("direct_answer") String directAnswer,
		String thought) {

	public boolean directAnswerRoute() {
		return CoordinatorRoute.DIRECT_ANSWER.equals(nextRoute);
	}

}
