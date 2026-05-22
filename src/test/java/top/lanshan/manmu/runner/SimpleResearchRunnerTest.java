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
	void routesThroughInformationResearcherProcessorAndReporter() {
		SimpleResearchRunner runner = new SimpleResearchRunner(List.of(new PlanningNode(), new InformationNode(),
				new TeamNode(), new ResearchNodeStub(), new ProcessorNodeStub(), new ReporterNode()));

		var events = runner.run(new ResearchRequest("Explain workflow.", "thread-1", 1)).collectList().block();

		assertThat(events).isNotNull();
		assertThat(events).extracting(ResearchEvent::node)
			.containsExactly("planner", "information", "research_team", "researcher", "research_team", "processor",
					"research_team", "reporter", "__END__");
	}

	@Test
	void pausesAfterPlannerWhenPlanGateIsRequested() {
		SimpleResearchRunner runner = new SimpleResearchRunner(List.of(new PlanningNode(), new InformationNode(),
				new TeamNode(), new ResearchNodeStub(), new ProcessorNodeStub(), new ReporterNode()));

		var events = runner.runUntilPlanGate(new ResearchRequest("Explain workflow.", "thread-2", 1))
			.collectList()
			.block();

		assertThat(events).isNotNull();
		assertThat(events).extracting(ResearchEvent::node).containsExactly("planner", "human_feedback");
		assertThat(events.get(1).phase()).isEqualTo("waiting");
		assertThat(events.get(1).payload()).isInstanceOf(ResearchPlan.class);
	}

	@Test
	void acceptedResumeContinuesFromInformationWithoutReplanning() {
		PlanningNode planningNode = new PlanningNode();
		SimpleResearchRunner runner = new SimpleResearchRunner(List.of(planningNode, new InformationNode(),
				new TeamNode(), new ResearchNodeStub(), new ProcessorNodeStub(), new ReporterNode()));

		runner.runUntilPlanGate(new ResearchRequest("Explain workflow.", "thread-3", 1)).collectList().block();
		var events = runner.resume("thread-3", new ResumeDecision(true, null)).collectList().block();

		assertThat(events).isNotNull();
		assertThat(planningNode.runCount()).isEqualTo(1);
		assertThat(events).extracting(ResearchEvent::node)
			.containsExactly("information", "research_team", "researcher", "research_team", "processor",
					"research_team", "reporter", "__END__");
	}

	@Test
	void rejectedResumeReplansWithFeedbackAndWaitsAgain() {
		PlanningNode planningNode = new PlanningNode();
		SimpleResearchRunner runner = new SimpleResearchRunner(List.of(planningNode, new InformationNode(),
				new TeamNode(), new ResearchNodeStub(), new ProcessorNodeStub(), new ReporterNode()));

		runner.runUntilPlanGate(new ResearchRequest("Explain workflow.", "thread-4", 1)).collectList().block();
		var events = runner.resume("thread-4", new ResumeDecision(false, "Focus on risks.")).collectList().block();

		assertThat(events).isNotNull();
		assertThat(planningNode.runCount()).isEqualTo(2);
		assertThat(planningNode.lastFeedback()).isEqualTo("Focus on risks.");
		assertThat(events).extracting(ResearchEvent::node).containsExactly("planner", "human_feedback");
		assertThat(events.get(1).phase()).isEqualTo("waiting");
	}

	@Test
	void missingPausedStateReturnsHumanFeedbackError() {
		SimpleResearchRunner runner = new SimpleResearchRunner(List.of(new PlanningNode(), new InformationNode(),
				new TeamNode(), new ResearchNodeStub(), new ProcessorNodeStub(), new ReporterNode()));

		var events = runner.resume("missing-thread", new ResumeDecision(true, null)).collectList().block();

		assertThat(events).isNotNull();
		assertThat(events).singleElement().satisfies(event -> {
			assertThat(event.node()).isEqualTo("human_feedback");
			assertThat(event.phase()).isEqualTo("error");
			assertThat(event.done()).isTrue();
		});
	}

	private static class PlanningNode implements ResearchNode {

		private int runCount;

		private String lastFeedback;

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
			runCount++;
			lastFeedback = state.planFeedback();
			state.plan(new ResearchPlan("Plan", true, "Think", List.of(new ResearchStep("Step", "Do work", false,
					StepType.RESEARCH, null, ResearchStep.STATUS_PENDING),
					new ResearchStep("Summarize", "Process findings", false, StepType.PROCESSING, null,
							ResearchStep.STATUS_PENDING))));
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "completed", "planned", state.plan()));
		}

		int runCount() {
			return runCount;
		}

		String lastFeedback() {
			return lastFeedback;
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
			boolean researchCompleted = ResearchStep.STATUS_COMPLETED
				.equals(state.plan().steps().get(0).executionStatus());
			boolean processingCompleted = ResearchStep.STATUS_COMPLETED
				.equals(state.plan().steps().get(1).executionStatus());
			if (!researchCompleted) {
				state.researchTeamDecision(new ResearchTeamDecision(ResearchTeamRoute.RESEARCHER, StepType.RESEARCH, 2,
						0, 0, 2));
			}
			else if (!processingCompleted) {
				state.researchTeamDecision(new ResearchTeamDecision(ResearchTeamRoute.PROCESSOR, StepType.PROCESSING,
						2, 1, 0, 1));
			}
			else {
				state.researchTeamDecision(new ResearchTeamDecision(ResearchTeamRoute.REPORTER, null, 2, 2, 0, 0));
			}
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

	private static class ProcessorNodeStub implements ResearchNode {

		@Override
		public int order() {
			return 45;
		}

		@Override
		public String name() {
			return "processor";
		}

		@Override
		public Flux<ResearchEvent> run(ResearchState state) {
			state.plan().steps().get(1).executionStatus(ResearchStep.STATUS_COMPLETED);
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "completed", "processed", "ok"));
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
