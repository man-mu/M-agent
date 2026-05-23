package top.lanshan.manmu.runner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import top.lanshan.manmu.graph.ResearchGraphBuilder;
import top.lanshan.manmu.model.CoordinatorDecision;
import top.lanshan.manmu.model.CoordinatorRoute;
import top.lanshan.manmu.model.HumanFeedbackDecision;
import top.lanshan.manmu.model.HumanFeedbackRoute;
import top.lanshan.manmu.model.PlanValidatorDecision;
import top.lanshan.manmu.model.PlanValidatorRoute;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.ResearchTeamDecision;
import top.lanshan.manmu.model.ResearchTeamRoute;
import top.lanshan.manmu.model.StepType;
import top.lanshan.manmu.node.HumanFeedbackNode;
import top.lanshan.manmu.node.PlanValidatorNode;
import top.lanshan.manmu.node.ResearchNode;
import top.lanshan.manmu.report.ReportService;
import top.lanshan.manmu.report.ResearchReport;
import top.lanshan.manmu.sessioncontext.SessionContextReport;
import top.lanshan.manmu.sessioncontext.SessionContextService;
import top.lanshan.manmu.sessionhistory.ResearchSessionHistory;
import top.lanshan.manmu.sessionhistory.SessionHistoryService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GraphResearchRunnerTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(RunnerSelectionConfiguration.class, ResearchRunnerConfiguration.class,
				SimpleResearchRunner.class, GraphResearchRunner.class);

	@Test
	void directAnswerRouteSkipsResearchNodesAndSavesReport() {
		RecordingReportService reportService = new RecordingReportService();
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		GraphResearchRunner runner = newRunner(List.of(new DirectAnswerCoordinatorNodeStub(),
				new PlanValidatorNodeStub(), new HumanFeedbackNodeStub(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), new PlanningNode(), new InformationNode(), new TeamNode(), new ResearchNodeStub(),
				new ProcessorNodeStub(), new ReporterNode()), reportService, sessionHistoryService,
				new RecordingSessionContextService());

		var events = runner.run(new ResearchRequest("Say hello.", "thread-direct", 2, null, false))
			.collectList()
			.block();

		assertThat(events).isNotNull();
		assertThat(events).as("events: %s", events).extracting(ResearchEvent::node)
			.containsExactly("coordinator", "__END__");
		assertThat(reportService.savedReports()).singleElement().satisfies(report -> {
			assertThat(report.threadId()).isEqualTo("thread-direct");
			assertThat(report.sessionId()).isEqualTo("thread-direct");
			assertThat(report.report()).isEqualTo("Direct answer");
		});
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "COMPLETED");
	}

	@Test
	void autoAcceptedResearchCompletesThroughResearchTeamLoop() {
		RecordingReportService reportService = new RecordingReportService();
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		RecordingSessionContextService sessionContextService = new RecordingSessionContextService();
		sessionContextService.context("thread-graph", "Prior report context");
		PlanningNode planningNode = new PlanningNode();
		GraphResearchRunner runner = newRunner(List.of(new CoordinatorNodeStub(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), planningNode, new PlanValidatorNodeStub(),
				new HumanFeedbackNodeStub(), new InformationNode(), new TeamNode(), new ResearchNodeStub(),
				new ProcessorNodeStub(), new ReporterNode()), reportService, sessionHistoryService,
				sessionContextService);

		var events = runner.run(new ResearchRequest("Explain workflow.", "thread-graph", 1)).collectList().block();

		assertThat(events).isNotNull();
		assertThat(events).as("events: %s", events)
			.extracting(ResearchEvent::node)
			.containsExactly("coordinator", "rewrite_multi_query", "background_investigator", "planner",
					"plan_validator", "information", "research_team", "researcher", "research_team", "processor",
					"research_team", "reporter", "__END__");
		assertThat(planningNode.lastBackgroundContext()).isEqualTo("Prior report context");
		assertThat(reportService.savedReports()).singleElement().satisfies(report -> {
			assertThat(report.threadId()).isEqualTo("thread-graph");
			assertThat(report.report()).isEqualTo("done");
		});
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "COMPLETED");
	}

	@Test
	void invalidPlanIsRetriedBeforeResearchContinues() {
		RetryingPlanningNode planningNode = new RetryingPlanningNode(1);
		RecordingReportService reportService = new RecordingReportService();
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		GraphResearchRunner runner = newRunner(List.of(new CoordinatorNodeStub(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), planningNode, new RealPlanValidatorNodeAdapter(),
				new HumanFeedbackNodeStub(), new InformationNode(), new TeamNode(), new ResearchNodeStub(),
				new ProcessorNodeStub(), new ReporterNode()), reportService, sessionHistoryService,
				new RecordingSessionContextService());

		var events = runner.run(new ResearchRequest("Explain workflow.", "thread-retry", 1))
			.collectList()
			.block();

		assertThat(events).isNotNull();
		assertThat(planningNode.runCount()).isEqualTo(2);
		assertThat(events).as("events: %s", events)
			.extracting(ResearchEvent::node)
			.containsSubsequence("planner", "plan_validator", "planner", "plan_validator", "information",
					"research_team", "reporter", "__END__");
		assertThat(reportService.savedReports()).singleElement().satisfies(report -> {
			assertThat(report.threadId()).isEqualTo("thread-retry");
			assertThat(report.report()).isEqualTo("done");
		});
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "COMPLETED");
	}

	@Test
	void manualPlanGatePausesAtHumanFeedbackWithoutSavingReport() {
		RecordingReportService reportService = new RecordingReportService();
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		GraphResearchRunner runner = newRunner(List.of(new CoordinatorNodeStub(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), new PlanningNode(), new RealPlanValidatorNodeAdapter(),
				new RealHumanFeedbackNodeAdapter(), new InformationNode(), new TeamNode(), new ResearchNodeStub(),
				new ProcessorNodeStub(), new ReporterNode()), reportService, sessionHistoryService,
				new RecordingSessionContextService());

		var events = runner
			.runUntilPlanGate(new ResearchRequest("Explain workflow.", "thread-pause", 1), "session-pause")
			.collectList()
			.block();

		assertThat(events).isNotNull();
		assertThat(events).as("events: %s", events)
			.extracting(ResearchEvent::node)
			.containsExactly("coordinator", "rewrite_multi_query", "background_investigator", "planner",
					"plan_validator", "human_feedback");
		assertThat(events.get(5)).satisfies(event -> {
			assertThat(event.phase()).isEqualTo("waiting");
			assertThat(event.payload()).isInstanceOf(ResearchPlan.class);
		});
		assertThat(reportService.savedReports()).isEmpty();
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "PAUSED");
	}

	@Test
	void acceptedResumeContinuesFromHumanFeedbackAndSavesReport() {
		PlanningNode planningNode = new PlanningNode();
		RecordingReportService reportService = new RecordingReportService();
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		GraphResearchRunner runner = newRunner(List.of(new CoordinatorNodeStub(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), planningNode, new RealPlanValidatorNodeAdapter(),
				new RealHumanFeedbackNodeAdapter(), new InformationNode(), new TeamNode(), new ResearchNodeStub(),
				new ProcessorNodeStub(), new ReporterNode()), reportService, sessionHistoryService,
				new RecordingSessionContextService());

		runner.runUntilPlanGate(new ResearchRequest("Explain workflow.", "thread-resume", 1),
				"session-resume")
			.collectList()
			.block();
		var events = runner.resume("thread-resume", new ResumeDecision(true, null)).collectList().block();

		assertThat(events).isNotNull();
		assertThat(planningNode.runCount()).isEqualTo(1);
		assertThat(events).as("events: %s", events)
			.extracting(ResearchEvent::node)
			.containsExactly("human_feedback", "information", "research_team", "researcher", "research_team",
					"processor", "research_team", "reporter", "__END__");
		assertThat(events.get(0).phase()).isEqualTo("decision");
		assertThat(reportService.savedReports()).singleElement().satisfies(report -> {
			assertThat(report.threadId()).isEqualTo("thread-resume");
			assertThat(report.sessionId()).isEqualTo("session-resume");
			assertThat(report.report()).isEqualTo("done");
		});
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "PAUSED", "RUNNING", "COMPLETED");
	}

	@Test
	void rejectedResumeReplansWithFeedbackAndWaitsAgain() {
		PlanningNode planningNode = new PlanningNode();
		RecordingReportService reportService = new RecordingReportService();
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		GraphResearchRunner runner = newRunner(List.of(new CoordinatorNodeStub(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), planningNode, new RealPlanValidatorNodeAdapter(),
				new RealHumanFeedbackNodeAdapter(), new InformationNode(), new TeamNode(), new ResearchNodeStub(),
				new ProcessorNodeStub(), new ReporterNode()), reportService, sessionHistoryService,
				new RecordingSessionContextService());

		runner.runUntilPlanGate(new ResearchRequest("Explain workflow.", "thread-reject", 1),
				"session-reject")
			.collectList()
			.block();
		var rejectedEvents = runner.resume("thread-reject", new ResumeDecision(false, "Focus on risks."))
			.collectList()
			.block();

		assertThat(rejectedEvents).isNotNull();
		assertThat(planningNode.runCount()).isEqualTo(2);
		assertThat(planningNode.lastFeedback()).isEqualTo("Focus on risks.");
		assertThat(rejectedEvents).extracting(ResearchEvent::node).containsExactly("human_feedback", "planner",
				"plan_validator", "human_feedback");
		assertThat(rejectedEvents.get(0).phase()).isEqualTo("decision");
		assertThat(rejectedEvents.get(3).phase()).isEqualTo("waiting");
		assertThat(reportService.savedReports()).isEmpty();
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "PAUSED", "RUNNING", "PAUSED");
	}

	@Test
	void rejectedResumeContinuesWhenPlanIterationLimitIsReached() {
		PlanningNode planningNode = new PlanningNode();
		RecordingReportService reportService = new RecordingReportService();
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		GraphResearchRunner runner = newRunner(List.of(new CoordinatorNodeStub(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), planningNode, new RealPlanValidatorNodeAdapter(),
				new RealHumanFeedbackNodeAdapter(), new InformationNode(), new TeamNode(), new ResearchNodeStub(),
				new ProcessorNodeStub(), new ReporterNode()), reportService, sessionHistoryService,
				new RecordingSessionContextService());

		runner.runUntilPlanGate(new ResearchRequest("Explain workflow.", "thread-limit", 1,
				null, true, false, 1), "session-limit").collectList().block();
		var events = runner.resume("thread-limit", new ResumeDecision(false, "Try again.")).collectList().block();

		assertThat(events).isNotNull();
		assertThat(planningNode.runCount()).isEqualTo(1);
		assertThat(events).extracting(ResearchEvent::node).containsExactly("human_feedback", "information",
				"research_team", "researcher", "research_team", "processor", "research_team", "reporter", "__END__");
		assertThat(reportService.savedReports()).singleElement().satisfies(report -> {
			assertThat(report.threadId()).isEqualTo("thread-limit");
			assertThat(report.sessionId()).isEqualTo("session-limit");
		});
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "PAUSED", "RUNNING", "COMPLETED");
	}

	@Test
	void missingPausedStateReturnsHumanFeedbackError() {
		GraphResearchRunner runner = newRunner(List.of(new CoordinatorNodeStub(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), new PlanningNode(), new PlanValidatorNodeStub(),
				new HumanFeedbackNodeStub(), new InformationNode(), new TeamNode(), new ResearchNodeStub(),
				new ProcessorNodeStub(), new ReporterNode()), new RecordingReportService(),
				new RecordingSessionHistoryService(), new RecordingSessionContextService());

		var events = runner.resume("missing-thread", new ResumeDecision(true, null)).collectList().block();

		assertThat(events).isNotNull();
		assertThat(events).singleElement().satisfies(event -> {
			assertThat(event.node()).isEqualTo("human_feedback");
			assertThat(event.phase()).isEqualTo("error");
			assertThat(event.content()).contains("No paused research state found");
			assertThat(event.done()).isTrue();
		});
	}

	@Test
	void stopRemovesPausedGraphState() {
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		GraphResearchRunner runner = newRunner(List.of(new CoordinatorNodeStub(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), new PlanningNode(), new RealPlanValidatorNodeAdapter(),
				new RealHumanFeedbackNodeAdapter(), new InformationNode(), new TeamNode(), new ResearchNodeStub(),
				new ProcessorNodeStub(), new ReporterNode()), new RecordingReportService(), sessionHistoryService,
				new RecordingSessionContextService());

		runner.runUntilPlanGate(new ResearchRequest("Explain workflow.", "thread-stop-paused", 1),
				"session-stop-paused").collectList().block();

		assertThat(runner.stopAndRecord("thread-stop-paused").block()).isTrue();
		assertThat(runner.stopAndRecord("thread-stop-paused").block()).isFalse();
		var events = runner.resume("thread-stop-paused", new ResumeDecision(true, null)).collectList().block();

		assertThat(events).isNotNull();
		assertThat(events).singleElement().satisfies(event -> {
			assertThat(event.node()).isEqualTo("human_feedback");
			assertThat(event.phase()).isEqualTo("error");
			assertThat(event.content()).contains("No paused research state found");
		});
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "PAUSED", "STOPPED");
	}

	@Test
	void runChatCanBeStoppedWhileRunning() {
		Sinks.Empty<Void> releaseInformation = Sinks.empty();
		RecordingReportService reportService = new RecordingReportService();
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		GraphResearchRunner runner = newRunner(List.of(new CoordinatorNodeStub(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), new PlanningNode(), new PlanValidatorNodeStub(),
				new HumanFeedbackNodeStub(), new BlockingInformationNode(releaseInformation), new TeamNode(),
				new ResearchNodeStub(), new ProcessorNodeStub(), new ReporterNode()), reportService,
				sessionHistoryService, new RecordingSessionContextService());

		StepVerifier.create(runner.runChat(new ResearchRequest("Explain workflow.", "thread-stop-running", 1),
				"session-stop-running"))
			.expectNextMatches(event -> "coordinator".equals(event.node()))
			.expectNextMatches(event -> "rewrite_multi_query".equals(event.node()))
			.expectNextMatches(event -> "background_investigator".equals(event.node()))
			.expectNextMatches(event -> "planner".equals(event.node()))
			.expectNextMatches(event -> "plan_validator".equals(event.node()))
			.then(() -> assertThat(runner.stopAndRecord("thread-stop-running").block()).isTrue())
			.expectNextMatches(event -> event.done() && "__END__".equals(event.node())
					&& "stopped".equals(event.phase()))
			.verifyComplete();

		assertThat(runner.stopAndRecord("thread-stop-running").block()).isFalse();
		assertThat(reportService.savedReports()).isEmpty();
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "STOPPED");
		releaseInformation.tryEmitEmpty();
	}

	@Test
	void stopMissingThreadReturnsFalse() {
		GraphResearchRunner runner = newRunner(List.of(new CoordinatorNodeStub(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), new PlanningNode(), new PlanValidatorNodeStub(),
				new HumanFeedbackNodeStub(), new InformationNode(), new TeamNode(), new ResearchNodeStub(),
				new ProcessorNodeStub(), new ReporterNode()), new RecordingReportService(),
				new RecordingSessionHistoryService(), new RecordingSessionContextService());

		assertThat(runner.stopAndRecord("missing-thread").block()).isFalse();
		assertThat(runner.stopAndRecord("").block()).isFalse();
		assertThat(runner.stopAndRecord(null).block()).isFalse();
	}

	@Test
	void graphRunnerIsDefaultAndSimpleRemainsExplicitFallback() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(ResearchRunner.class);
			assertThat(context.getBean(ResearchRunner.class)).isInstanceOf(GraphResearchRunner.class);
			assertThat(context).doesNotHaveBean(SimpleResearchRunner.class);
		});

		contextRunner.withPropertyValues("mvp.research.runner=graph").run(context -> {
			assertThat(context).hasSingleBean(ResearchRunner.class);
			assertThat(context.getBean(ResearchRunner.class)).isInstanceOf(GraphResearchRunner.class);
			assertThat(context).doesNotHaveBean(SimpleResearchRunner.class);
		});

		contextRunner.withPropertyValues("mvp.research.runner=simple").run(context -> {
			assertThat(context).hasSingleBean(ResearchRunner.class);
			assertThat(context.getBean(ResearchRunner.class)).isInstanceOf(SimpleResearchRunner.class);
			assertThat(context).doesNotHaveBean(GraphResearchRunner.class);
		});
	}

	private static GraphResearchRunner newRunner(List<ResearchNode> nodes, ReportService reportService,
			SessionHistoryService sessionHistoryService, SessionContextService sessionContextService) {
		return new GraphResearchRunner(new ResearchGraphBuilder(nodes), reportService, sessionHistoryService,
				sessionContextService);
	}

	@Configuration(proxyBeanMethods = false)
	static class RunnerSelectionConfiguration {

		@Bean
		ReportService reportService() {
			return new RecordingReportService();
		}

		@Bean
		SessionHistoryService sessionHistoryService() {
			return new RecordingSessionHistoryService();
		}

		@Bean
		SessionContextService sessionContextService() {
			return new RecordingSessionContextService();
		}

		@Bean
		ResearchNode coordinatorNode() {
			return new CoordinatorNodeStub();
		}

		@Bean
		ResearchNode queryRewriteNode() {
			return new QueryRewriteNodeStub();
		}

		@Bean
		ResearchNode backgroundInvestigatorNode() {
			return new BackgroundInvestigatorNodeStub();
		}

		@Bean
		ResearchNode plannerNode() {
			return new PlanningNode();
		}

		@Bean
		ResearchNode planValidatorNode() {
			return new PlanValidatorNodeStub();
		}

		@Bean
		ResearchNode humanFeedbackNode() {
			return new HumanFeedbackNodeStub();
		}

		@Bean
		ResearchNode informationNode() {
			return new InformationNode();
		}

		@Bean
		ResearchNode researchTeamNode() {
			return new TeamNode();
		}

		@Bean
		ResearchNode researcherNode() {
			return new ResearchNodeStub();
		}

		@Bean
		ResearchNode processorNode() {
			return new ProcessorNodeStub();
		}

		@Bean
		ResearchNode reporterNode() {
			return new ReporterNode();
		}

	}

	private static class CoordinatorNodeStub implements ResearchNode {

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
			state.coordinatorDecision(new CoordinatorDecision(CoordinatorRoute.DEEP_RESEARCH, true, null,
					"Needs research."));
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "decision", "routed",
					state.coordinatorDecision()));
		}

	}

	private static class DirectAnswerCoordinatorNodeStub implements ResearchNode {

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
			state.coordinatorDecision(new CoordinatorDecision(CoordinatorRoute.DIRECT_ANSWER, false, "Direct answer",
					"Simple."));
			state.report("Direct answer");
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "decision", "routed",
					state.coordinatorDecision()));
		}

	}

	private static class QueryRewriteNodeStub implements ResearchNode {

		@Override
		public int order() {
			return 5;
		}

		@Override
		public String name() {
			return "rewrite_multi_query";
		}

		@Override
		public Flux<ResearchEvent> run(ResearchState state) {
			state.optimizedQueries(List.of(state.query(), state.query() + " architecture"));
			state.queryRewriteCompleted(true);
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "completed", "rewritten",
					state.optimizedQueries()));
		}

	}

	private static class BackgroundInvestigatorNodeStub implements ResearchNode {

		@Override
		public int order() {
			return 7;
		}

		@Override
		public String name() {
			return "background_investigator";
		}

		@Override
		public Flux<ResearchEvent> run(ResearchState state) {
			state.backgroundInvestigationContext("Stub current background context");
			state.backgroundInvestigationCompleted(true);
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "completed",
					"background investigated", state.backgroundInvestigationContext()));
		}

	}

	private static class PlanningNode implements ResearchNode {

		private String lastBackgroundContext;

		private String lastFeedback;

		private int runCount;

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
			state.recordPlanAttempt();
			state.plannerError(null);
			lastFeedback = state.planFeedback();
			lastBackgroundContext = state.backgroundContext();
			state.plan(validPlan());
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "completed", "planned", state.plan()));
		}

		String lastBackgroundContext() {
			return lastBackgroundContext;
		}

		String lastFeedback() {
			return lastFeedback;
		}

		int runCount() {
			return runCount;
		}

	}

	private static class RetryingPlanningNode extends PlanningNode {

		private final int failuresBeforeSuccess;

		RetryingPlanningNode(int failuresBeforeSuccess) {
			this.failuresBeforeSuccess = failuresBeforeSuccess;
		}

		@Override
		public Flux<ResearchEvent> run(ResearchState state) {
			super.runCount++;
			state.recordPlanAttempt();
			if (runCount() <= failuresBeforeSuccess) {
				state.plan(null);
				state.plannerError("planner failed");
				return Flux.just(ResearchEvent.message(state.threadId(), name(), "failed",
						"Plan generation failed", state.plannerError()));
			}
			state.plannerError(null);
			state.plan(validPlan());
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "completed", "planned", state.plan()));
		}

	}

	private static class PlanValidatorNodeStub implements ResearchNode {

		@Override
		public int order() {
			return 15;
		}

		@Override
		public String name() {
			return "plan_validator";
		}

		@Override
		public Flux<ResearchEvent> run(ResearchState state) {
			state.planValidatorDecision(new PlanValidatorDecision(PlanValidatorRoute.RESEARCH_TEAM, true,
					state.planIterations(), state.maxPlanIterations(), "Valid plan"));
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "decision", "validated",
					state.planValidatorDecision()));
		}

	}

	private static class RealPlanValidatorNodeAdapter implements ResearchNode {

		private final PlanValidatorNode delegate = new PlanValidatorNode();

		@Override
		public int order() {
			return delegate.order();
		}

		@Override
		public String name() {
			return delegate.name();
		}

		@Override
		public Flux<ResearchEvent> run(ResearchState state) {
			return delegate.run(state);
		}

	}

	private static class RealHumanFeedbackNodeAdapter implements ResearchNode {

		private final HumanFeedbackNode delegate = new HumanFeedbackNode();

		@Override
		public int order() {
			return delegate.order();
		}

		@Override
		public String name() {
			return delegate.name();
		}

		@Override
		public Flux<ResearchEvent> run(ResearchState state) {
			return delegate.run(state);
		}

	}

	private static class HumanFeedbackNodeStub implements ResearchNode {

		@Override
		public int order() {
			return 17;
		}

		@Override
		public String name() {
			return "human_feedback";
		}

		@Override
		public Flux<ResearchEvent> run(ResearchState state) {
			state.humanFeedbackDecision(new HumanFeedbackDecision(HumanFeedbackRoute.WAITING, null, null,
					state.planIterations(), state.maxPlanIterations(), "Waiting"));
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "waiting", "waiting", state.plan()));
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

	private static class BlockingInformationNode implements ResearchNode {

		private final Sinks.Empty<Void> release;

		BlockingInformationNode(Sinks.Empty<Void> release) {
			this.release = release;
		}

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
			return release.asMono()
				.thenReturn(ResearchEvent.message(state.threadId(), name(), "completed", "searched", List.of()))
				.flux();
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

	private static ResearchPlan validPlan() {
		return new ResearchPlan("Plan", true, "Think",
				List.of(new ResearchStep("Step", "Do work", false, StepType.RESEARCH, null,
						ResearchStep.STATUS_PENDING),
						new ResearchStep("Summarize", "Process findings", false, StepType.PROCESSING, null,
								ResearchStep.STATUS_PENDING)));
	}

	private static class RecordingReportService implements ReportService {

		private final List<ResearchReport> savedReports = new ArrayList<>();

		@Override
		public Mono<ResearchReport> saveCompletedReport(String threadId, String sessionId, String query, String report) {
			ResearchReport saved = new ResearchReport(threadId, sessionId, query, report, "COMPLETED", null,
					Instant.now(), Instant.now());
			savedReports.add(saved);
			return Mono.just(saved);
		}

		@Override
		public Mono<String> getReport(String threadId) {
			return Mono.empty();
		}

		@Override
		public Mono<Boolean> existsReport(String threadId) {
			return Mono.just(false);
		}

		@Override
		public Mono<Void> deleteReport(String threadId) {
			return Mono.empty();
		}

		@Override
		public Flux<ResearchReport> findBySessionId(String sessionId) {
			return Flux.empty();
		}

		List<ResearchReport> savedReports() {
			return savedReports;
		}

	}

	private static class RecordingSessionHistoryService implements SessionHistoryService {

		private final List<ResearchSessionHistory> histories = new ArrayList<>();

		@Override
		public Mono<ResearchSessionHistory> start(String threadId, String sessionId, String query) {
			return record(threadId, sessionId, query, "RUNNING", null, null);
		}

		@Override
		public Mono<ResearchSessionHistory> markRunning(String threadId) {
			return record(threadId, sessionId(threadId), query(threadId), "RUNNING", null, null);
		}

		@Override
		public Mono<ResearchSessionHistory> markPaused(String threadId) {
			return record(threadId, sessionId(threadId), query(threadId), "PAUSED", null, null);
		}

		@Override
		public Mono<ResearchSessionHistory> markCompleted(String threadId, String reportThreadId) {
			return record(threadId, sessionId(threadId), query(threadId), "COMPLETED", reportThreadId, null);
		}

		@Override
		public Mono<ResearchSessionHistory> markStopped(String threadId) {
			return record(threadId, sessionId(threadId), query(threadId), "STOPPED", null, null);
		}

		@Override
		public Mono<ResearchSessionHistory> markFailed(String threadId, String errorMessage) {
			return record(threadId, sessionId(threadId), query(threadId), "FAILED", null, errorMessage);
		}

		@Override
		public Flux<ResearchSessionHistory> findBySessionId(String sessionId) {
			return Flux.fromIterable(histories).filter(history -> sessionId.equals(history.sessionId()));
		}

		@Override
		public Mono<ResearchSessionHistory> findBySessionIdAndThreadId(String sessionId, String threadId) {
			return findBySessionId(sessionId).filter(history -> threadId.equals(history.threadId())).last();
		}

		@Override
		public Flux<ResearchSessionHistory> findRecentBySessionId(String sessionId, int count) {
			return findBySessionId(sessionId).take(count);
		}

		List<String> statuses() {
			return histories.stream().map(ResearchSessionHistory::status).toList();
		}

		private Mono<ResearchSessionHistory> record(String threadId, String sessionId, String query, String status,
				String reportThreadId, String errorMessage) {
			ResearchSessionHistory history = new ResearchSessionHistory(threadId, sessionId, query, status,
					reportThreadId, errorMessage, Instant.now(), Instant.now(),
					"COMPLETED".equals(status) ? Instant.now() : null,
					"STOPPED".equals(status) ? Instant.now() : null);
			histories.add(history);
			return Mono.just(history);
		}

		private String sessionId(String threadId) {
			return histories.stream()
				.filter(history -> threadId.equals(history.threadId()))
				.findFirst()
				.map(ResearchSessionHistory::sessionId)
				.orElse(threadId);
		}

		private String query(String threadId) {
			return histories.stream()
				.filter(history -> threadId.equals(history.threadId()))
				.findFirst()
				.map(ResearchSessionHistory::query)
				.orElse("");
		}

	}

	private static class RecordingSessionContextService implements SessionContextService {

		private final Map<String, String> contexts = new HashMap<>();

		void context(String sessionId, String context) {
			contexts.put(sessionId, context);
		}

		@Override
		public Flux<SessionContextReport> findRecentCompletedReports(String sessionId, String currentThreadId) {
			return Flux.empty();
		}

		@Override
		public Mono<String> formatRecentCompletedReports(String sessionId, String currentThreadId) {
			return Mono.just(contexts.getOrDefault(sessionId, ""));
		}

	}

}
