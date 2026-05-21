package top.lanshan.manmu.runner;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.ResearchTeamDecision;
import top.lanshan.manmu.model.ResearchTeamRoute;
import top.lanshan.manmu.model.StepType;
import top.lanshan.manmu.node.ResearchNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleResearchRunnerTest {

	@Test
	void routesThroughInformationBeforeResearchTeam() {
		SimpleResearchRunner runner = new SimpleResearchRunner(List.of(new PlanningNode(), new InformationNode(),
				new TeamNode(), new ResearchNodeStub(), new ReporterNode()));

		var events = runner.run(new ResearchRequest("Explain workflow.", "thread-1", 1)).collectList().block();

		assertThat(events).isNotNull();
		assertThat(events).extracting(ResearchEvent::node)
			.containsExactly("planner", "information", "research_team", "researcher", "research_team", "reporter",
					"__END__");
	}

	private static class PlanningNode implements ResearchNode {

		@Override
		public int order() {
			return 10;
		}

		@Override
		public String name() {
			return "planner";
		}

		@Override
		public Flux<ResearchEvent> run(ResearchState state) {
			state.plan(new ResearchPlan("Plan", true, "Think", List.of(new ResearchStep("Step", "Do work", false,
					StepType.RESEARCH, null, ResearchStep.STATUS_PENDING))));
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "completed", "planned", state.plan()));
		}

	}

	private static class InformationNode implements ResearchNode {

		@Override
		public int order() {
			return 20;
		}

		@Override
		public String name() {
			return "information";
		}

		@Override
		public Flux<ResearchEvent> run(ResearchState state) {
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "completed", "searched", List.of()));
		}

	}

	private static class TeamNode implements ResearchNode {

		@Override
		public int order() {
			return 30;
		}

		@Override
		public String name() {
			return "research_team";
		}

		@Override
		public Flux<ResearchEvent> run(ResearchState state) {
			boolean completed = ResearchStep.STATUS_COMPLETED.equals(state.plan().steps().get(0).executionStatus());
			state.researchTeamDecision(completed
					? new ResearchTeamDecision(ResearchTeamRoute.REPORTER, null, 1, 1, 0, 0)
					: new ResearchTeamDecision(ResearchTeamRoute.RESEARCHER, StepType.RESEARCH, 1, 0, 0, 1));
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "decision", "routed",
					state.researchTeamDecision()));
		}

	}

	private static class ResearchNodeStub implements ResearchNode {

		@Override
		public int order() {
			return 40;
		}

		@Override
		public String name() {
			return "researcher";
		}

		@Override
		public Flux<ResearchEvent> run(ResearchState state) {
			state.plan().steps().get(0).executionStatus(ResearchStep.STATUS_COMPLETED);
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "completed", "researched", "ok"));
		}

	}

	private static class ReporterNode implements ResearchNode {

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
			state.report("done");
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "completed", "reported", "done"));
		}

	}

}
