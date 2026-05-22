package top.lanshan.manmu.agent;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class QueryRewriteOutputMapper {

	public static final int MIN_OPTIMIZE_QUERY_NUM = 0;

	public static final int MAX_OPTIMIZE_QUERY_NUM = 5;

	public int clampCount(int optimizeQueryNum) {
		return Math.max(MIN_OPTIMIZE_QUERY_NUM, Math.min(MAX_OPTIMIZE_QUERY_NUM, optimizeQueryNum));
	}

	public List<String> toOptimizedQueries(QueryRewriteResponse response, String originalQuery, int optimizeQueryNum) {
		int count = clampCount(optimizeQueryNum);
		if (count == 0) {
			return List.of();
		}

		Set<String> queries = new LinkedHashSet<>();
		addIfPresent(queries, originalQuery);
		if (response != null && response.optimizedQueries() != null) {
			response.optimizedQueries().forEach(query -> addIfPresent(queries, query));
		}
		return new ArrayList<>(queries).stream().limit(count).toList();
	}

	private void addIfPresent(Set<String> queries, String query) {
		if (query == null) {
			return;
		}
		String normalized = query.strip();
		if (!normalized.isBlank()) {
			queries.add(normalized);
		}
	}

}
