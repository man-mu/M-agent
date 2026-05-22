package top.lanshan.manmu.runner;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.ResearchTeamDecision;
import top.lanshan.manmu.model.ResearchTeamRoute;
import top.lanshan.manmu.model.StepType;
import top.lanshan.manmu.model.CoordinatorDecision;
import top.lanshan.manmu.model.CoordinatorRoute;
import top.lanshan.manmu.model.PlanValidatorDecision;
import top.lanshan.manmu.model.PlanValidatorRoute;
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

class SimpleResearchRunnerTest {

	@Test
	void routesThroughInformationResearcherProcessorAndReporter() {
		RecordingReportService reportService = new RecordingReportService();
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		SimpleResearchRunner runner = newRunner(List.of(new QueryRewriteNodeStub(), new BackgroundInvestigatorNodeStub(),
				new PlanningNode(), new InformationNode(), new TeamNode(), new ResearchNodeStub(),
				new ProcessorNodeStub(), new ReporterNode()), reportService, sessionHistoryService);

		var events = runner.run(new ResearchRequest("Explain workflow.", "thread-1", 1)).collectList().block();

		assertThat(events).isNotNull();
		assertThat(events).extracting(ResearchEvent::node)
			.containsExactly("coordinator", "rewrite_multi_query", "background_investigator", "planner",
					"plan_validator", "information", "research_team", "researcher", "research_team", "processor",
					"research_team", "reporter", "__END__");
		assertThat(reportService.savedReports()).singleElement().satisfies(report -> {
			assertThat(report.threadId()).isEqualTo("thread-1");
			assertThat(report.sessionId()).isEqualTo("thread-1");
			assertThat(report.query()).isEqualTo("Explain workflow.");
			assertThat(report.report()).isEqualTo("done");
		});
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "COMPLETED");
		assertThat(sessionHistoryService.histories()).last().satisfies(history -> {
			assertThat(history.threadId()).isEqualTo("thread-1");
			assertThat(history.sessionId()).isEqualTo("thread-1");
			assertThat(history.reportThreadId()).isEqualTo("thread-1");
		});
	}

	@Test
	void runChatCanBeStoppedWhileRunning() {
		Sinks.Empty<Void> releaseInformation = Sinks.empty();
		RecordingReportService reportService = new RecordingReportService();
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		SimpleResearchRunner runner = newRunner(List.of(new PlanningNode(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), new BlockingInformationNode(releaseInformation), new TeamNode(),
				new ResearchNodeStub(), new ProcessorNodeStub(), new ReporterNode()), reportService,
				sessionHistoryService);

		StepVerifier
			.create(runner.runChat(new ResearchRequest("Explain workflow.", "thread-running-stop", 1),
					"session-running-stop"))
			.expectNextMatches(event -> "coordinator".equals(event.node()))
			.expectNextMatches(event -> "rewrite_multi_query".equals(event.node()))
			.expectNextMatches(event -> "background_investigator".equals(event.node()))
			.expectNextMatches(event -> "planner".equals(event.node()))
			.expectNextMatches(event -> "plan_validator".equals(event.node()))
			.then(() -> assertThat(runner.stop("thread-running-stop")).isTrue())
			.expectNextMatches(event -> event.done() && "__END__".equals(event.node()) && "stopped".equals(event.phase()))
			.verifyComplete();

		assertThat(runner.stop("thread-running-stop")).isFalse();
		assertThat(reportService.savedReports()).isEmpty();
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "STOPPED");
		releaseInformation.tryEmitEmpty();
	}

	@Test
	void runChatCleansUpAfterNormalCompletion() {
		RecordingReportService reportService = new RecordingReportService();
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		SimpleResearchRunner runner = newRunner(List.of(new QueryRewriteNodeStub(), new BackgroundInvestigatorNodeStub(),
				new PlanningNode(), new InformationNode(), new TeamNode(), new ResearchNodeStub(),
				new ProcessorNodeStub(), new ReporterNode()), reportService, sessionHistoryService);

		var events = runner.runChat(new ResearchRequest("Explain workflow.", "thread-normal-cleanup", 1),
				"session-normal-cleanup")
			.collectList()
			.block();

		assertThat(events).isNotNull();
		assertThat(events).extracting(ResearchEvent::node).last().isEqualTo("__END__");
		assertThat(events).extracting(ResearchEvent::phase).last().isEqualTo("completed");
		assertThat(runner.stop("thread-normal-cleanup")).isFalse();
		assertThat(reportService.savedReports()).singleElement().satisfies(report -> {
			assertThat(report.threadId()).isEqualTo("thread-normal-cleanup");
			assertThat(report.sessionId()).isEqualTo("session-normal-cleanup");
		});
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "COMPLETED");
	}

	@Test
	void pausesAfterPlannerWhenPlanGateIsRequested() {
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		SimpleResearchRunner runner = newRunner(List.of(new QueryRewriteNodeStub(), new BackgroundInvestigatorNodeStub(),
				new PlanningNode(), new InformationNode(), new TeamNode(), new ResearchNodeStub(),
				new ProcessorNodeStub(), new ReporterNode()), new RecordingReportService(), sessionHistoryService);

		var events = runner.runUntilPlanGate(new ResearchRequest("Explain workflow.", "thread-2", 1), "session-2")
			.collectList()
			.block();

		assertThat(events).isNotNull();
		assertThat(events).extracting(ResearchEvent::node).containsExactly("coordinator", "rewrite_multi_query",
				"background_investigator", "planner", "plan_validator", "human_feedback");
		assertThat(events.get(5).phase()).isEqualTo("waiting");
		assertThat(events.get(5).payload()).isInstanceOf(ResearchPlan.class);
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "PAUSED");
	}

	@Test
	void acceptedResumeContinuesFromInformationWithoutReplanning() {
		PlanningNode planningNode = new PlanningNode();
		RecordingReportService reportService = new RecordingReportService();
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		SimpleResearchRunner runner = newRunner(List.of(new QueryRewriteNodeStub(), new BackgroundInvestigatorNodeStub(),
				planningNode, new InformationNode(), new TeamNode(), new ResearchNodeStub(), new ProcessorNodeStub(),
				new ReporterNode()), reportService, sessionHistoryService);

		runner.runUntilPlanGate(new ResearchRequest("Explain workflow.", "thread-3", 1), "session-3")
			.collectList()
			.block();
		var events = runner.resume("thread-3", new ResumeDecision(true, null)).collectList().block();

		assertThat(events).isNotNull();
		assertThat(planningNode.runCount()).isEqualTo(1);
		assertThat(events).extracting(ResearchEvent::node)
			.containsExactly("information", "research_team", "researcher", "research_team", "processor",
					"research_team", "reporter", "__END__");
		assertThat(reportService.savedReports()).singleElement().satisfies(report -> {
			assertThat(report.threadId()).isEqualTo("thread-3");
			assertThat(report.sessionId()).isEqualTo("session-3");
		});
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "PAUSED", "RUNNING", "COMPLETED");
	}

	@Test
	void acceptedResumeCanBeStoppedWhileRunning() {
		Sinks.Empty<Void> releaseInformation = Sinks.empty();
		PlanningNode planningNode = new PlanningNode();
		RecordingReportService reportService = new RecordingReportService();
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		SimpleResearchRunner runner = newRunner(List.of(new QueryRewriteNodeStub(), new BackgroundInvestigatorNodeStub(),
				planningNode, new BlockingInformationNode(releaseInformation), new TeamNode(), new ResearchNodeStub(),
				new ProcessorNodeStub(), new ReporterNode()), reportService, sessionHistoryService);

		runner.runUntilPlanGate(new ResearchRequest("Explain workflow.", "thread-resume-stop", 1),
				"session-resume-stop")
			.collectList()
			.block();

		StepVerifier.create(runner.resume("thread-resume-stop", new ResumeDecision(true, null)))
			.then(() -> assertThat(runner.stop("thread-resume-stop")).isTrue())
			.expectNextMatches(event -> event.done() && "__END__".equals(event.node()) && "stopped".equals(event.phase()))
			.verifyComplete();

		assertThat(planningNode.runCount()).isEqualTo(1);
		assertThat(runner.stop("thread-resume-stop")).isFalse();
		assertThat(reportService.savedReports()).isEmpty();
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "PAUSED", "RUNNING", "STOPPED");
		releaseInformation.tryEmitEmpty();
	}

	@Test
	void rejectedResumeReplansWithFeedbackAndWaitsAgain() {
		PlanningNode planningNode = new PlanningNode();
		RecordingReportService reportService = new RecordingReportService();
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		SimpleResearchRunner runner = newRunner(List.of(new QueryRewriteNodeStub(), new BackgroundInvestigatorNodeStub(),
				planningNode, new InformationNode(), new TeamNode(), new ResearchNodeStub(), new ProcessorNodeStub(),
				new ReporterNode()), reportService, sessionHistoryService);

		runner.runUntilPlanGate(new ResearchRequest("Explain workflow.", "thread-4", 1), "session-4")
			.collectList()
			.block();
		var events = runner.resume("thread-4", new ResumeDecision(false, "Focus on risks.")).collectList().block();

		assertThat(events).isNotNull();
		assertThat(planningNode.runCount()).isEqualTo(2);
		assertThat(planningNode.lastFeedback()).isEqualTo("Focus on risks.");
		assertThat(events).extracting(ResearchEvent::node).containsExactly("planner", "plan_validator",
				"human_feedback");
		assertThat(events.get(2).phase()).isEqualTo("waiting");
		assertThat(reportService.savedReports()).isEmpty();
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "PAUSED", "RUNNING", "PAUSED");
	}

	@Test
	void missingPausedStateReturnsHumanFeedbackError() {
		SimpleResearchRunner runner = newRunner(List.of(new QueryRewriteNodeStub(), new BackgroundInvestigatorNodeStub(),
				new PlanningNode(), new InformationNode(), new TeamNode(), new ResearchNodeStub(),
				new ProcessorNodeStub(), new ReporterNode()), new RecordingReportService(),
				new RecordingSessionHistoryService());

		var events = runner.resume("missing-thread", new ResumeDecision(true, null)).collectList().block();

		assertThat(events).isNotNull();
		assertThat(events).singleElement().satisfies(event -> {
			assertThat(event.node()).isEqualTo("human_feedback");
			assertThat(event.phase()).isEqualTo("error");
			assertThat(event.done()).isTrue();
		});
	}

	@Test
	void stopRemovesPausedState() {
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		SimpleResearchRunner runner = newRunner(List.of(new QueryRewriteNodeStub(), new BackgroundInvestigatorNodeStub(),
				new PlanningNode(), new InformationNode(), new TeamNode(), new ResearchNodeStub(),
				new ProcessorNodeStub(), new ReporterNode()), new RecordingReportService(), sessionHistoryService);

		runner.runUntilPlanGate(new ResearchRequest("Explain workflow.", "thread-stop", 1), "session-stop")
			.collectList()
			.block();

		assertThat(runner.stop("thread-stop")).isTrue();
		var events = runner.resume("thread-stop", new ResumeDecision(true, null)).collectList().block();

		assertThat(events).isNotNull();
		assertThat(events).singleElement().satisfies(event -> {
			assertThat(event.node()).isEqualTo("human_feedback");
			assertThat(event.phase()).isEqualTo("error");
			assertThat(event.content()).contains("No paused research state found");
		});
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "PAUSED", "STOPPED");
	}

	@Test
	void stopMissingThreadReturnsFalse() {
		SimpleResearchRunner runner = newRunner(List.of(new QueryRewriteNodeStub(), new BackgroundInvestigatorNodeStub(),
				new PlanningNode(), new InformationNode(), new TeamNode(), new ResearchNodeStub(),
				new ProcessorNodeStub(), new ReporterNode()), new RecordingReportService(),
				new RecordingSessionHistoryService());

		assertThat(runner.stop("missing-thread")).isFalse();
	}

	@Test
	void failedWorkflowMarksSessionHistoryFailed() {
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		SimpleResearchRunner runner = newRunner(List.of(new QueryRewriteNodeStub(), new BackgroundInvestigatorNodeStub(),
				new FailingPlanningNode(), new InformationNode(), new TeamNode(), new ResearchNodeStub(),
				new ProcessorNodeStub(), new ReporterNode()), new RecordingReportService(), sessionHistoryService);

		var events = runner.run(new ResearchRequest("Explain workflow.", "thread-failed", 1)).collectList().block();

		assertThat(events).isNotNull();
		assertThat(events).extracting(ResearchEvent::node)
			.containsExactly("coordinator", "rewrite_multi_query", "background_investigator", "runner");
		assertThat(events.get(3)).satisfies(event -> {
			assertThat(event.node()).isEqualTo("runner");
			assertThat(event.phase()).isEqualTo("error");
			assertThat(event.content()).contains("planner failed");
		});
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "FAILED");
		assertThat(sessionHistoryService.histories()).last().satisfies(history -> {
			assertThat(history.threadId()).isEqualTo("thread-failed");
			assertThat(history.errorMessage()).contains("planner failed");
		});
	}

	@Test
	void invalidPlanIsRetriedBeforeResearchContinues() {
		RetryingPlanningNode planningNode = new RetryingPlanningNode(1);
		RecordingReportService reportService = new RecordingReportService();
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		SimpleResearchRunner runner = new SimpleResearchRunner(List.of(new CoordinatorNodeStub(),
				new QueryRewriteNodeStub(), new BackgroundInvestigatorNodeStub(), planningNode,
				new RealPlanValidatorNodeAdapter(), new InformationNode(), new TeamNode(), new ResearchNodeStub(),
				new ProcessorNodeStub(), new ReporterNode()), reportService, sessionHistoryService,
				new RecordingSessionContextService());

		var events = runner.run(new ResearchRequest("Explain workflow.", "thread-retry", 1))
			.collectList()
			.block();

		assertThat(events).isNotNull();
		assertThat(planningNode.runCount()).isEqualTo(2);
		assertThat(events).extracting(ResearchEvent::node).containsSubsequence("planner", "plan_validator",
				"planner", "plan_validator", "information", "__END__");
		assertThat(reportService.savedReports()).singleElement().satisfies(report -> {
			assertThat(report.threadId()).isEqualTo("thread-retry");
			assertThat(report.report()).isEqualTo("done");
		});
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "COMPLETED");
	}

	@Test
	void invalidPlanFailureMarksSessionHistoryFailedAfterRetryLimit() {
		RetryingPlanningNode planningNode = new RetryingPlanningNode(5);
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		SimpleResearchRunner runner = new SimpleResearchRunner(List.of(new CoordinatorNodeStub(),
				new QueryRewriteNodeStub(), new BackgroundInvestigatorNodeStub(), planningNode,
				new RealPlanValidatorNodeAdapter(), new InformationNode(), new TeamNode(), new ResearchNodeStub(),
				new ProcessorNodeStub(), new ReporterNode()), new RecordingReportService(), sessionHistoryService,
				new RecordingSessionContextService());

		var events = runner.run(new ResearchRequest("Explain workflow.", "thread-retry-failed", 1,
				null, true, true, 2)).collectList().block();

		assertThat(events).isNotNull();
		assertThat(planningNode.runCount()).isEqualTo(2);
		assertThat(events).extracting(ResearchEvent::node).containsExactly("coordinator", "rewrite_multi_query",
				"background_investigator", "planner", "plan_validator", "planner", "plan_validator", "runner");
		assertThat(events.get(events.size() - 1).content()).contains("Planner did not produce a valid plan after 2");
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "FAILED");
	}

	@Test
	void loadsBackgroundContextBeforePlanning() {
		PlanningNode planningNode = new PlanningNode();
		RecordingSessionContextService sessionContextService = new RecordingSessionContextService();
		sessionContextService.context("session-context", "Prior report context");
		SimpleResearchRunner runner = new SimpleResearchRunner(List.of(new CoordinatorNodeStub(),
				new PlanValidatorNodeStub(), new QueryRewriteNodeStub(), new BackgroundInvestigatorNodeStub(),
				planningNode, new InformationNode(), new TeamNode(), new ResearchNodeStub(),
				new ProcessorNodeStub(), new ReporterNode()), new RecordingReportService(),
				new RecordingSessionHistoryService(), sessionContextService);

		runner.runChat(new ResearchRequest("Continue the investigation.", "thread-context", 1), "session-context")
			.collectList()
			.block();

		assertThat(sessionContextService.lookups()).containsExactly("session-context/thread-context");
		assertThat(planningNode.lastBackgroundContext()).isEqualTo("Prior report context");
	}

	@Test
	void rewritesQueryBeforeLoadingBackgroundContextAndPlanning() {
		PlanningNode planningNode = new PlanningNode();
		QueryRewriteNodeStub queryRewriteNode = new QueryRewriteNodeStub();
		RecordingSessionContextService sessionContextService = new RecordingSessionContextService();
		SimpleResearchRunner runner = new SimpleResearchRunner(List.of(new CoordinatorNodeStub(),
				new PlanValidatorNodeStub(), queryRewriteNode, new BackgroundInvestigatorNodeStub(), planningNode,
				new InformationNode(), new TeamNode(), new ResearchNodeStub(), new ProcessorNodeStub(),
				new ReporterNode()), new RecordingReportService(), new RecordingSessionHistoryService(),
				sessionContextService);

		var events = runner.run(new ResearchRequest("Explain workflow.", "thread-order", 1)).collectList().block();

		assertThat(events).isNotNull();
		assertThat(events).extracting(ResearchEvent::node)
			.startsWith("coordinator", "rewrite_multi_query", "background_investigator", "planner",
					"plan_validator");
		assertThat(queryRewriteNode.lastOptimizeQueryNum()).isEqualTo(3);
		assertThat(planningNode.lastOptimizedQueries()).containsExactly("Explain workflow.",
				"Explain workflow. architecture");
		assertThat(planningNode.lastBackgroundInvestigationContext()).isEqualTo("Stub current background context");
	}

	private static SimpleResearchRunner newRunner(List<ResearchNode> nodes, ReportService reportService,
			SessionHistoryService sessionHistoryService) {
		List<ResearchNode> nodesWithCoordinator = new ArrayList<>();
		nodesWithCoordinator.add(new CoordinatorNodeStub());
		nodesWithCoordinator.add(new PlanValidatorNodeStub());
		nodesWithCoordinator.addAll(nodes);
		return new SimpleResearchRunner(nodesWithCoordinator, reportService, sessionHistoryService,
				new RecordingSessionContextService());
	}

	@Test
	void directAnswerRouteSkipsResearchNodesAndSavesReport() {
		RecordingReportService reportService = new RecordingReportService();
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		SimpleResearchRunner runner = new SimpleResearchRunner(List.of(new DirectAnswerCoordinatorNodeStub(),
				new PlanValidatorNodeStub(), new QueryRewriteNodeStub(), new BackgroundInvestigatorNodeStub(),
				new PlanningNode(), new InformationNode(), new TeamNode(), new ResearchNodeStub(),
				new ProcessorNodeStub(), new ReporterNode()), reportService, sessionHistoryService,
				new RecordingSessionContextService());

		var events = runner.run(new ResearchRequest("Say hello.", "thread-direct", 2, null, false))
			.collectList()
			.block();

		assertThat(events).isNotNull();
		assertThat(events).extracting(ResearchEvent::node).containsExactly("coordinator", "__END__");
		assertThat(reportService.savedReports()).singleElement().satisfies(report -> {
			assertThat(report.threadId()).isEqualTo("thread-direct");
			assertThat(report.report()).isEqualTo("Direct answer");
		});
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "COMPLETED");
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
			PlanValidatorRoute route = state.autoAcceptedPlan() ? PlanValidatorRoute.RESEARCH_TEAM
					: PlanValidatorRoute.HUMAN_FEEDBACK;
			state.planValidatorDecision(new PlanValidatorDecision(route, true, state.planIterations(),
					state.maxPlanIterations(), "Valid plan"));
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "decision", "validated",
					state.planValidatorDecision()));
		}

	}

	private static class RealPlanValidatorNodeAdapter implements ResearchNode {

		private final top.lanshan.manmu.node.PlanValidatorNode delegate = new top.lanshan.manmu.node.PlanValidatorNode();

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

	private static class PlanningNode implements ResearchNode {

		private int runCount;

		private String lastFeedback;

		private String lastBackgroundContext;

		private String lastBackgroundInvestigationContext;

		private List<String> lastOptimizedQueries = List.of();

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
			lastBackgroundContext = state.backgroundContext();
			lastBackgroundInvestigationContext = state.backgroundInvestigationContext();
			lastOptimizedQueries = state.optimizedQueries();
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

		String lastBackgroundContext() {
			return lastBackgroundContext;
		}

		String lastBackgroundInvestigationContext() {
			return lastBackgroundInvestigationContext;
		}

		List<String> lastOptimizedQueries() {
			return lastOptimizedQueries;
		}

	}

	private static class RetryingPlanningNode implements ResearchNode {

		private final int failuresBeforeSuccess;

		private int runCount;

		RetryingPlanningNode(int failuresBeforeSuccess) {
			this.failuresBeforeSuccess = failuresBeforeSuccess;
		}

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
			if (runCount <= failuresBeforeSuccess) {
				state.plan(null);
				state.plannerError("planner failed");
				return Flux.just(ResearchEvent.message(state.threadId(), name(), "failed",
						"Plan generation failed", state.plannerError()));
			}
			state.plannerError(null);
			state.plan(new ResearchPlan("Plan", true, "Think", List.of(new ResearchStep("Step", "Do work", false,
					StepType.RESEARCH, null, ResearchStep.STATUS_PENDING),
					new ResearchStep("Summarize", "Process findings", false, StepType.PROCESSING, null,
							ResearchStep.STATUS_PENDING))));
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "completed", "planned", state.plan()));
		}

		int runCount() {
			return runCount;
		}

	}

	private static class QueryRewriteNodeStub implements ResearchNode {

		private int lastOptimizeQueryNum;

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
			lastOptimizeQueryNum = state.optimizeQueryNum();
			state.optimizedQueries(List.of(state.query(), state.query() + " architecture"));
			state.queryRewriteCompleted(true);
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "completed", "rewritten",
					state.optimizedQueries()));
		}

		int lastOptimizeQueryNum() {
			return lastOptimizeQueryNum;
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

	private static class FailingPlanningNode extends PlanningNode {

		@Override
		public Flux<ResearchEvent> run(ResearchState state) {
			return Flux.error(new IllegalStateException("planner failed"));
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
			return Flux.fromIterable(histories)
				.filter(history -> sessionId.equals(history.sessionId()) && threadId.equals(history.threadId()))
				.last();
		}

		@Override
		public Flux<ResearchSessionHistory> findRecentBySessionId(String sessionId, int count) {
			return findBySessionId(sessionId).take(count);
		}

		List<ResearchSessionHistory> histories() {
			return histories;
		}

		List<String> statuses() {
			return histories.stream().map(ResearchSessionHistory::status).toList();
		}

		private Mono<ResearchSessionHistory> record(String threadId, String sessionId, String query, String status,
				String reportThreadId, String errorMessage) {
			ResearchSessionHistory history = new ResearchSessionHistory(threadId, sessionId, query, status, reportThreadId,
					errorMessage, Instant.now(), Instant.now(), "COMPLETED".equals(status) ? Instant.now() : null,
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

		private final List<String> lookups = new ArrayList<>();

		void context(String sessionId, String context) {
			contexts.put(sessionId, context);
		}

		@Override
		public Flux<SessionContextReport> findRecentCompletedReports(String sessionId, String currentThreadId) {
			return Flux.empty();
		}

		@Override
		public Mono<String> formatRecentCompletedReports(String sessionId, String currentThreadId) {
			lookups.add(sessionId + "/" + currentThreadId);
			return Mono.just(contexts.getOrDefault(sessionId, ""));
		}

		List<String> lookups() {
			return lookups;
		}

	}

}
