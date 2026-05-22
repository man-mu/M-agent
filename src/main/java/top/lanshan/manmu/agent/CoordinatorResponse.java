package top.lanshan.manmu.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import top.lanshan.manmu.model.CoordinatorRoute;

public record CoordinatorResponse(@JsonProperty("next_route") CoordinatorRoute nextRoute,
		@JsonProperty("direct_answer") String directAnswer,
		String thought) {
}
