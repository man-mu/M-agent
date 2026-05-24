package top.lanshan.manmu.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdvancedExecutionPropertiesTest {

	@Test
	void defaultsProvideParallelNodeCounts() {
		AdvancedExecutionProperties properties = new AdvancedExecutionProperties();

		assertThat(properties.getParallelNodeCount().getResearcher()).isEqualTo(2);
		assertThat(properties.getParallelNodeCount().getCoder()).isEqualTo(1);
	}

	@Test
	void parallelNodeCountsAreAtLeastOne() {
		AdvancedExecutionProperties.ParallelNodeCount nodeCount =
				new AdvancedExecutionProperties.ParallelNodeCount();

		nodeCount.setResearcher(0);
		nodeCount.setCoder(-1);

		assertThat(nodeCount.getResearcher()).isEqualTo(1);
		assertThat(nodeCount.getCoder()).isEqualTo(1);
	}

}
