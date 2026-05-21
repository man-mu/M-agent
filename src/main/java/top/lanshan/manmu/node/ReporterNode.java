package top.lanshan.manmu.node;

import top.lanshan.manmu.agent.ReporterAgent;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class ReporterNode implements ResearchNode {

	private final ReporterAgent reporterAgent;

	public ReporterNode(ReporterAgent reporterAgent) {
		this.reporterAgent = reporterAgent;
	}

	@Override
	public int order() {
		return 40;
	}

	@Override
	public String name() {
		return "reporter";
	}

	@Override
	public Flux<ResearchEvent> run(ResearchState state) {
		return Flux.defer(() -> {
			String report = reporterAgent.report(state);
			state.report(report);
			return Flux.just(
					ResearchEvent.message(state.threadId(), name(), "started", "Generating final report", null),
					ResearchEvent.message(state.threadId(), name(), "completed", report, report));
		});
	}

}
