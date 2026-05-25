package top.lanshan.manmu.node;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import top.lanshan.manmu.agent.CoordinatorAgent;
import top.lanshan.manmu.memory.UserProfileService;
import top.lanshan.manmu.model.CoordinatorDecision;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;

@Component
public class CoordinatorNode implements ResearchNode {

	private final CoordinatorAgent coordinatorAgent;

	private final UserProfileService userProfileService;

	public CoordinatorNode(CoordinatorAgent coordinatorAgent, UserProfileService userProfileService) {
		this.coordinatorAgent = coordinatorAgent;
		this.userProfileService = userProfileService;
	}

	@Override
	public int order() {
		return 1;
	}

	@Override
	public String name() {
		return "coordinator";
	}

	@Override
	public Flux<ResearchEvent> run(ResearchState state) {
		return Flux.defer(() -> {
			String userProfileContext = userProfileService.getOrCreateProfile(state.sessionId());
			CoordinatorDecision decision = coordinatorAgent.coordinate(
					state.query(), state.deepResearchEnabled(), userProfileContext);
			state.coordinatorDecision(decision);
			if (decision.directAnswerRoute()) {
				state.report(decision.directAnswer());
			}
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "decision",
					"Coordinator routed to " + decision.nextRoute().name().toLowerCase(), decision));
		});
	}

}
