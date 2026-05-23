package top.lanshan.manmu.runner;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mvp.research")
public class ResearchRunnerProperties {

	private RunnerType runner = RunnerType.GRAPH;

	public RunnerType getRunner() {
		return runner;
	}

	public void setRunner(RunnerType runner) {
		this.runner = runner == null ? RunnerType.GRAPH : runner;
	}

	public enum RunnerType {

		SIMPLE,

		GRAPH

	}

}
