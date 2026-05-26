package top.lanshan.manmu.node;

import top.lanshan.manmu.agent.ReporterAgent;
import top.lanshan.manmu.config.UserProfileProperties;
import top.lanshan.manmu.memory.UserProfileService;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class ReporterNode implements ResearchNode {

	private final ReporterAgent reporterAgent;

	private final UserProfileService userProfileService;

	private final UserProfileProperties userProfileProperties;

	public ReporterNode(ReporterAgent reporterAgent, UserProfileService userProfileService,
			UserProfileProperties userProfileProperties) {
		this.reporterAgent = reporterAgent;
		this.userProfileService = userProfileService;
		this.userProfileProperties = userProfileProperties;
	}

	@Override
	public int order() {
		return 50;
	}

	@Override
	public String name() {
		return "reporter";
	}

	@Override
	public Flux<ResearchEvent> run(ResearchState state) {
		return Flux.defer(() -> {
			String userProfileContext = "";
			if (userProfileProperties != null && userProfileProperties.isGuideReporter()
					&& userProfileService != null) {
				userProfileContext = userProfileService.getOrCreateProfile(state.sessionId());
			}
			String report = reporterAgent.report(state, userProfileContext);
			state.report(report);
			return Flux.just(
					ResearchEvent.message(state.threadId(), name(), "started", "Generating final report", null),
					ResearchEvent.message(state.threadId(), name(), "completed", report, report));
		});
	}

}
