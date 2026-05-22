package top.lanshan.manmu.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record QueryRewritePayload(@JsonProperty("original_query") String originalQuery,
		@JsonProperty("optimize_queries") List<String> optimizedQueries,
		String reason) {

	public QueryRewritePayload(String originalQuery, List<String> optimizedQueries) {
		this(originalQuery, optimizedQueries, null);
	}

}
