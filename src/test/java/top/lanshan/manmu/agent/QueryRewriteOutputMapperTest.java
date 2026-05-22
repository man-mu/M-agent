package top.lanshan.manmu.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryRewriteOutputMapperTest {

	private final QueryRewriteOutputMapper mapper = new QueryRewriteOutputMapper();

	@Test
	void clampsOptimizeQueryCountToReferenceRange() {
		assertThat(mapper.clampCount(-2)).isZero();
		assertThat(mapper.clampCount(3)).isEqualTo(3);
		assertThat(mapper.clampCount(12)).isEqualTo(5);
	}

	@Test
	void includesOriginalQueryDeduplicatesAndLimitsOutput() {
		QueryRewriteResponse response = new QueryRewriteResponse(List.of("Original question", "  Variant A  ",
				"", "Variant B", "Variant C"));

		List<String> queries = mapper.toOptimizedQueries(response, "Original question", 3);

		assertThat(queries).containsExactly("Original question", "Variant A", "Variant B");
	}

	@Test
	void returnsEmptyWhenOptimizeQueryCountIsZero() {
		QueryRewriteResponse response = new QueryRewriteResponse(List.of("Variant A"));

		assertThat(mapper.toOptimizedQueries(response, "Original question", 0)).isEmpty();
	}

}
