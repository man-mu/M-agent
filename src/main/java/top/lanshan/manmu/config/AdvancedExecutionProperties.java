package top.lanshan.manmu.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mvp.research.advanced-execution")
public class AdvancedExecutionProperties {

	private ParallelNodeCount parallelNodeCount = new ParallelNodeCount();

	public ParallelNodeCount getParallelNodeCount() {
		return parallelNodeCount;
	}

	public void setParallelNodeCount(ParallelNodeCount parallelNodeCount) {
		this.parallelNodeCount = parallelNodeCount == null ? new ParallelNodeCount() : parallelNodeCount;
	}

	public static class ParallelNodeCount {

		private int researcher = 2;

		private int coder = 1;

		public int getResearcher() {
			return researcher;
		}

		public void setResearcher(int researcher) {
			this.researcher = atLeastOne(researcher);
		}

		public int getCoder() {
			return coder;
		}

		public void setCoder(int coder) {
			this.coder = atLeastOne(coder);
		}

		private int atLeastOne(int value) {
			return Math.max(1, value);
		}

	}

}
