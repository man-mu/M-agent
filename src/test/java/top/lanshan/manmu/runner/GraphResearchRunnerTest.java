package top.lanshan.manmu.runner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import top.lanshan.manmu.agent.ProcessorAgent;
import top.lanshan.manmu.agent.ResearcherAgent;
import top.lanshan.manmu.config.AdvancedExecutionProperties;
import top.lanshan.manmu.graph.ResearchGraphBuilder;
import top.lanshan.manmu.memory.ConversationMemoryService;
import top.lanshan.manmu.memory.ConversationMessageRecord;
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
import top.lanshan.manmu.model.ResearchStreamEventType;
import top.lanshan.manmu.model.ResearchTeamDecision;
import top.lanshan.manmu.model.ResearchTeamRoute;
import top.lanshan.manmu.model.StepExecutionStatus;
import top.lanshan.manmu.model.StepType;
import top.lanshan.manmu.node.CoderNode;
import top.lanshan.manmu.node.HumanFeedbackNode;
import top.lanshan.manmu.node.ParallelExecutorNode;
import top.lanshan.manmu.node.PlanValidatorNode;
import top.lanshan.manmu.node.ResearchNode;
import top.lanshan.manmu.node.ResearchTeamNode;
import top.lanshan.manmu.node.ResearcherNode;
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
				GraphResearchRunner.class);

	@Test
	void directAnswerRouteSkipsResearchNodesAndSavesReport() {
		RecordingReportService reportService = new RecordingReportService();
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		GraphResearchRunner runner = newRunner(List.of(new DirectAnswerCoordinatorNodeStub(),
				new PlanValidatorNodeStub(), new HumanFeedbackNodeStub(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), new PlanningNode(), new InformationNode(), new TeamNode(),
				new ParallelExecutorNodeStub(), new ReporterNode()), reportService, sessionHistoryService,
				new RecordingSessionContextService());

		var events = runner.run(new ResearchRequest("Say hello.", "thread-direct", 2, null, false))
			.collectList()
			.block();

		assertThat(events).isNotNull();
		assertThat(events).as("events: %s", events).extracting(ResearchEvent::node)
			.containsExactly("coordinator", "__END__");
		assertThat(events).extracting(ResearchEvent::sequence).containsExactly(1L, 2L);
		assertThat(events).extracting(ResearchEvent::eventType)
			.containsExactly(ResearchStreamEventType.NODE_DELTA, ResearchStreamEventType.GRAPH_COMPLETED);
		assertThat(events.get(0)).satisfies(event -> {
			assertThat(event.nodeName()).isEqualTo("coordinator");
			assertThat(event.nodeType()).isEqualTo("coordinator");
			assertThat(event.status()).isEqualTo("decision");
			assertThat(event.displayTitle()).isEqualTo("Coordinator");
		});
		assertThat(reportService.savedReports()).singleElement().satisfies(report -> {
			assertThat(report.threadId()).isEqualTo("thread-direct");
			assertThat(report.sessionId()).isEqualTo("thread-direct");
			assertThat(report.report()).isEqualTo("Direct answer");
		});
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "COMPLETED");
	}

	@Test
	void runChatSavesUserAndAssistantConversationMessages() {
		RecordingConversationMemoryService memoryService = new RecordingConversationMemoryService();
		GraphResearchRunner runner = newRunner(List.of(new DirectAnswerCoordinatorNodeStub(),
				new PlanValidatorNodeStub(), new HumanFeedbackNodeStub(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), new PlanningNode(), new InformationNode(), new TeamNode(),
				new ParallelExecutorNodeStub(), new ReporterNode()), new RecordingReportService(),
				new RecordingSessionHistoryService(), new RecordingSessionContextService(), memoryService);

		var events = runner.runChat(new ResearchRequest("Say hello.", "thread-memory", 2, null, false),
				"session-memory").collectList().block();

		assertThat(events).isNotNull();
		assertThat(events).extracting(ResearchEvent::node).containsExactly("coordinator", "__END__");
		assertThat(memoryService.messages()).extracting(ConversationMessageRecord::role)
			.containsExactly("USER", "ASSISTANT");
		assertThat(memoryService.messages()).first().satisfies(message -> {
			assertThat(message.sessionId()).isEqualTo("session-memory");
			assertThat(message.threadId()).isEqualTo("thread-memory");
			assertThat(message.content()).isEqualTo("Say hello.");
		});
		assertThat(memoryService.messages()).last().satisfies(message -> {
			assertThat(message.sessionId()).isEqualTo("session-memory");
			assertThat(message.threadId()).isEqualTo("thread-memory");
			assertThat(message.content()).isEqualTo("Direct answer");
		});
	}

	@Test
	void conversationMemoryFailureDoesNotPreventCompletionEvent() {
		GraphResearchRunner runner = newRunner(List.of(new DirectAnswerCoordinatorNodeStub(),
				new PlanValidatorNodeStub(), new HumanFeedbackNodeStub(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), new PlanningNode(), new InformationNode(), new TeamNode(),
				new ParallelExecutorNodeStub(), new ReporterNode()), new RecordingReportService(),
				new RecordingSessionHistoryService(), new RecordingSessionContextService(),
				new FailingConversationMemoryService());

		var events = runner.runChat(new ResearchRequest("Say hello.", "thread-memory-failure", 2, null, false),
				"session-memory-failure").collectList().block();

		assertThat(events).isNotNull();
		assertThat(events).extracting(ResearchEvent::node).containsExactly("coordinator", "__END__");
		assertThat(events.get(1)).satisfies(event -> {
			assertThat(event.done()).isTrue();
			assertThat(event.payload()).isEqualTo("Direct answer");
		});
	}

	@Test
	void defaultAdvancedExecutionRoutesThroughParallelExecutorAndNamedExecutors() {
		RecordingReportService reportService = new RecordingReportService();
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		GraphResearchRunner runner = newRunner(List.of(new CoordinatorNodeStub(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), new PlanningNode(), new PlanValidatorNodeStub(),
				new HumanFeedbackNodeStub(), new InformationNode(), new ResearchTeamNode(),
				new ParallelExecutorNode(new AdvancedExecutionProperties()),
				new ResearcherNode((query, step, searchContext) -> "Researched " + step.id(), "0"),
				new ResearcherNode((query, step, searchContext) -> "Researched " + step.id(), "1"),
				new CoderNode((state, step) -> "Processed " + step.id(), "0"), new ReporterNode()), reportService,
				sessionHistoryService, new RecordingSessionContextService());

		var events = runner.run(new ResearchRequest("Explain workflow.", "thread-advanced", 1))
			.collectList()
			.block();

		assertThat(events).isNotNull();
		assertThat(events).as("events: %s", events)
			.extracting(ResearchEvent::node)
			.containsSubsequence("research_team", "parallel_executor", "researcher_0", "research_team",
					"parallel_executor", "coder_0", "research_team", "reporter", "__END__");
		assertThat(events).extracting(ResearchEvent::node).doesNotContain("researcher", "processor");
		assertThat(events).anySatisfy(event -> {
			assertThat(event.node()).isEqualTo("parallel_executor");
			assertThat(event.status()).isEqualTo(StepExecutionStatus.assigned("researcher_0"));
		});
		assertThat(events).anySatisfy(event -> {
			assertThat(event.node()).isEqualTo("parallel_executor");
			assertThat(event.status()).isEqualTo(StepExecutionStatus.assigned("coder_0"));
		});
		assertThat(events).anySatisfy(event -> {
			assertThat(event.nodeName()).isEqualTo("researcher_0");
			assertThat(event.nodeType()).isEqualTo("researcher");
			assertThat(event.executorId()).isEqualTo(0);
			assertThat(event.status()).isEqualTo(StepExecutionStatus.completed("researcher_0"));
		});
		assertThat(events).anySatisfy(event -> {
			assertThat(event.nodeName()).isEqualTo("coder_0");
			assertThat(event.nodeType()).isEqualTo("coder");
			assertThat(event.executorId()).isEqualTo(0);
			assertThat(event.status()).isEqualTo(StepExecutionStatus.completed("coder_0"));
		});
		assertThat(reportService.savedReports()).singleElement().satisfies(report -> {
			assertThat(report.threadId()).isEqualTo("thread-advanced");
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
				new HumanFeedbackNodeStub(), new InformationNode(), new ResearchTeamNode(),
				new ParallelExecutorNode(new AdvancedExecutionProperties()),
				new ResearcherNode((query, step, searchContext) -> "Researched " + step.id(), "0"),
				new CoderNode((state, step) -> "Processed " + step.id(), "0"), new ReporterNode()), reportService,
				sessionHistoryService, new RecordingSessionContextService());

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
		RecordingConversationMemoryService memoryService = new RecordingConversationMemoryService();
		GraphResearchRunner runner = newRunner(List.of(new CoordinatorNodeStub(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), new PlanningNode(), new RealPlanValidatorNodeAdapter(),
				new RealHumanFeedbackNodeAdapter(), new InformationNode(), new ResearchTeamNode(),
				new ParallelExecutorNodeStub(), new ReporterNode()), reportService, sessionHistoryService,
				new RecordingSessionContextService(), memoryService);

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
			assertThat(event.sequence()).isEqualTo(6);
			assertThat(event.eventType()).isEqualTo(ResearchStreamEventType.HUMAN_FEEDBACK_WAITING);
		});
		assertThat(reportService.savedReports()).isEmpty();
		assertThat(memoryService.messages()).singleElement().satisfies(message -> {
			assertThat(message.role()).isEqualTo("USER");
			assertThat(message.sessionId()).isEqualTo("session-pause");
			assertThat(message.threadId()).isEqualTo("thread-pause");
			assertThat(message.content()).isEqualTo("Explain workflow.");
		});
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "PAUSED");
	}

	@Test
	void acceptedResumeContinuesThroughParallelExecutorAndSavesReport() {
		PlanningNode planningNode = new PlanningNode();
		RecordingReportService reportService = new RecordingReportService();
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		GraphResearchRunner runner = newRunner(List.of(new CoordinatorNodeStub(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), planningNode, new RealPlanValidatorNodeAdapter(),
				new RealHumanFeedbackNodeAdapter(), new InformationNode(), new ResearchTeamNode(),
				new ParallelExecutorNode(new AdvancedExecutionProperties()),
				new ResearcherNode((query, step, searchContext) -> "Researched " + step.id(), "0"),
				new CoderNode((state, step) -> "Processed " + step.id(), "0"), new ReporterNode()), reportService,
				sessionHistoryService, new RecordingSessionContextService());

		runner.runUntilPlanGate(new ResearchRequest("Explain workflow.", "thread-resume", 1),
				"session-resume")
			.collectList()
			.block();
		var events = runner.resume("thread-resume", new ResumeDecision(true, null)).collectList().block();

		assertThat(events).isNotNull();
		assertThat(planningNode.runCount()).isEqualTo(1);
		assertThat(events).as("events: %s", events)
			.extracting(ResearchEvent::node)
			.containsSubsequence("human_feedback", "information", "research_team", "parallel_executor",
					"researcher_0", "research_team", "parallel_executor", "coder_0", "research_team",
					"reporter", "__END__");
		assertThat(events).extracting(ResearchEvent::node).doesNotContain("researcher", "processor");
		assertThat(events.get(0).eventType()).isEqualTo(ResearchStreamEventType.HUMAN_FEEDBACK_ACCEPTED);
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
		RecordingConversationMemoryService memoryService = new RecordingConversationMemoryService();
		GraphResearchRunner runner = newRunner(List.of(new CoordinatorNodeStub(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), planningNode, new RealPlanValidatorNodeAdapter(),
				new RealHumanFeedbackNodeAdapter(), new InformationNode(), new ResearchTeamNode(),
				new ParallelExecutorNodeStub(), new ReporterNode()), reportService, sessionHistoryService,
				new RecordingSessionContextService(), memoryService);

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
		assertThat(rejectedEvents).extracting(ResearchEvent::node)
			.doesNotContain("parallel_executor", "researcher_0", "coder_0");
		assertThat(rejectedEvents.get(0).eventType()).isEqualTo(ResearchStreamEventType.HUMAN_FEEDBACK_REJECTED);
		assertThat(rejectedEvents.get(3).eventType()).isEqualTo(ResearchStreamEventType.HUMAN_FEEDBACK_WAITING);
		assertThat(reportService.savedReports()).isEmpty();
		assertThat(memoryService.messages()).extracting(ConversationMessageRecord::role)
			.containsExactly("USER", "USER");
		assertThat(memoryService.messages()).last().satisfies(message -> {
			assertThat(message.sessionId()).isEqualTo("session-reject");
			assertThat(message.threadId()).isEqualTo("thread-reject");
			assertThat(message.content()).isEqualTo("User feedback: Focus on risks.");
		});
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "PAUSED", "RUNNING", "PAUSED");
	}

	@Test
	void rejectedResumeContinuesWhenPlanIterationLimitIsReached() {
		PlanningNode planningNode = new PlanningNode();
		RecordingReportService reportService = new RecordingReportService();
		RecordingSessionHistoryService sessionHistoryService = new RecordingSessionHistoryService();
		GraphResearchRunner runner = newRunner(List.of(new CoordinatorNodeStub(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), planningNode, new RealPlanValidatorNodeAdapter(),
				new RealHumanFeedbackNodeAdapter(), new InformationNode(), new ResearchTeamNode(),
				new ParallelExecutorNode(new AdvancedExecutionProperties()),
				new ResearcherNode((query, step, searchContext) -> "Researched " + step.id(), "0"),
				new CoderNode((state, step) -> "Processed " + step.id(), "0"), new ReporterNode()), reportService,
				sessionHistoryService, new RecordingSessionContextService());

		runner.runUntilPlanGate(new ResearchRequest("Explain workflow.", "thread-limit", 1,
				null, true, false, 1), "session-limit").collectList().block();
		var events = runner.resume("thread-limit", new ResumeDecision(false, "Try again.")).collectList().block();

		assertThat(events).isNotNull();
		assertThat(planningNode.runCount()).isEqualTo(1);
		assertThat(events).extracting(ResearchEvent::node)
			.containsSubsequence("human_feedback", "information", "research_team", "parallel_executor",
					"researcher_0", "research_team", "parallel_executor", "coder_0", "research_team",
					"reporter", "__END__");
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
				new HumanFeedbackNodeStub(), new InformationNode(), new ResearchTeamNode(),
				new ParallelExecutorNodeStub(), new ReporterNode()), new RecordingReportService(),
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
				new RealHumanFeedbackNodeAdapter(), new InformationNode(), new ResearchTeamNode(),
				new ParallelExecutorNodeStub(), new ReporterNode()), new RecordingReportService(), sessionHistoryService,
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
				new HumanFeedbackNodeStub(), new BlockingInformationNode(releaseInformation), new ResearchTeamNode(),
				new ParallelExecutorNodeStub(), new ReporterNode()), reportService,
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
					&& "stopped".equals(event.phase()) && event.sequence() == 6
					&& ResearchStreamEventType.GRAPH_STOPPED.equals(event.eventType()))
			.verifyComplete();

		assertThat(runner.stopAndRecord("thread-stop-running").block()).isFalse();
		assertThat(reportService.savedReports()).isEmpty();
		assertThat(sessionHistoryService.statuses()).containsExactly("RUNNING", "STOPPED");
		releaseInformation.tryEmitEmpty();
	}

	@Test
	void emitsNodeEventsBeforeBlockingNodeCompletes() {
		Sinks.Empty<Void> releaseInformation = Sinks.empty();
		GraphResearchRunner runner = newRunner(List.of(new CoordinatorNodeStub(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), new CompletedPlanningNode(), new RealPlanValidatorNodeAdapter(),
				new HumanFeedbackNodeStub(), new StreamingBlockingInformationNode(releaseInformation),
				new ResearchTeamNode(), new ParallelExecutorNodeStub(), new ReporterNode()),
				new RecordingReportService(), new RecordingSessionHistoryService(), new RecordingSessionContextService());

		StepVerifier.create(runner.runChat(new ResearchRequest("Explain workflow.", "thread-stream-live", 1),
				"session-stream-live"))
			.expectNextMatches(event -> "coordinator".equals(event.node()))
			.expectNextMatches(event -> "rewrite_multi_query".equals(event.node()))
			.expectNextMatches(event -> "background_investigator".equals(event.node()))
			.expectNextMatches(event -> "planner".equals(event.node()))
			.expectNextMatches(event -> "plan_validator".equals(event.node()))
			.expectNextMatches(event -> "information".equals(event.node()) && "started".equals(event.phase()))
			.then(() -> releaseInformation.tryEmitEmpty())
			.expectNextMatches(event -> "information".equals(event.node()) && "completed".equals(event.phase()))
			.expectNextMatches(event -> "research_team".equals(event.node()))
			.expectNextMatches(event -> "reporter".equals(event.node()))
			.expectNextMatches(event -> "__END__".equals(event.node()) && event.done())
			.verifyComplete();
	}

	@Test
	void resumeEmitsNodeEventsBeforeBlockingNodeCompletes() {
		Sinks.Empty<Void> releaseInformation = Sinks.empty();
		GraphResearchRunner runner = newRunner(List.of(new CoordinatorNodeStub(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), new PlanningNode(), new RealPlanValidatorNodeAdapter(),
				new RealHumanFeedbackNodeAdapter(), new StreamingBlockingInformationNode(releaseInformation),
				new ResearchTeamNode(), new ParallelExecutorNodeStub(), new ReporterNode()),
				new RecordingReportService(), new RecordingSessionHistoryService(), new RecordingSessionContextService());

		runner.runUntilPlanGate(new ResearchRequest("Explain workflow.", "thread-resume-stream-live", 1),
				"session-resume-stream-live").collectList().block();

		StepVerifier.create(runner.resume("thread-resume-stream-live", new ResumeDecision(true, null)))
			.expectNextMatches(event -> "human_feedback".equals(event.node())
					&& ResearchStreamEventType.HUMAN_FEEDBACK_ACCEPTED.equals(event.eventType()))
			.expectNextMatches(event -> "information".equals(event.node()) && "started".equals(event.phase()))
			.then(() -> releaseInformation.tryEmitEmpty())
			.expectNextMatches(event -> "information".equals(event.node()) && "completed".equals(event.phase()))
			.thenCancel()
			.verify();
	}

	@Test
	void stopMissingThreadReturnsFalse() {
		GraphResearchRunner runner = newRunner(List.of(new CoordinatorNodeStub(), new QueryRewriteNodeStub(),
				new BackgroundInvestigatorNodeStub(), new PlanningNode(), new PlanValidatorNodeStub(),
				new HumanFeedbackNodeStub(), new InformationNode(), new ResearchTeamNode(),
				new ParallelExecutorNodeStub(), new ReporterNode()), new RecordingReportService(),
				new RecordingSessionHistoryService(), new RecordingSessionContextService());

		assertThat(runner.stopAndRecord("missing-thread").block()).isFalse();
		assertThat(runner.stopAndRecord("").block()).isFalse();
		assertThat(runner.stopAndRecord(null).block()).isFalse();
	}

	@Test
	void graphRunnerIsAlwaysRegisteredAsTheOnlyRunner() {
		contextRunner.run(context -> {
			assertThat(context).hasSingleBean(ResearchRunner.class);
			assertThat(context.getBean(ResearchRunner.class)).isInstanceOf(GraphResearchRunner.class);
		});
	}

	private static GraphResearchRunner newRunner(List<ResearchNode> nodes, ReportService reportService,
			SessionHistoryService sessionHistoryService, SessionContextService sessionContextService) {
		return newRunner(nodes, reportService, sessionHistoryService, sessionContextService,
				new RecordingConversationMemoryService());
	}

	private static GraphResearchRunner newRunner(List<ResearchNode> nodes, ReportService reportService,
			SessionHistoryService sessionHistoryService, SessionContextService sessionContextService,
			ConversationMemoryService conversationMemoryService) {
		ResearcherAgent researcherAgent = (query, step, searchContext) -> "Researched " + step.id();
		ProcessorAgent processorAgent = (state, step) -> "Processed " + step.id();
		return new GraphResearchRunner(
				new ResearchGraphBuilder(nodes, new AdvancedExecutionProperties(), researcherAgent, processorAgent),
				reportService, sessionHistoryService, sessionContextService, conversationMemoryService);
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
		ConversationMemoryService conversationMemoryService() {
			return new RecordingConversationMemoryService();
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
		ResearchNode parallelExecutorNode() {
			return new ParallelExecutorNode(new AdvancedExecutionProperties());
		}

		@Bean
		ResearchNode researchTeamNode() {
			return new ResearchTeamNode();
		}

		@Bean
		ResearchNode reporterNode() {
			return new ReporterNode();
		}

		@Bean
		ResearcherAgent researcherAgent() {
			return (query, step, searchContext) -> "Researched " + step.id();
		}

		@Bean
		ProcessorAgent processorAgent() {
			return (state, step) -> "Processed " + step.id();
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

	private static class CompletedPlanningNode implements ResearchNode {

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
			state.recordPlanAttempt();
			state.plannerError(null);
			ResearchStep completedStep = new ResearchStep("Finished", "Already done.", true, StepType.RESEARCH,
					"Done", ResearchStep.STATUS_COMPLETED);
			completedStep.id("step-completed");
			state.plan(new ResearchPlan("Completed plan", true, "Already complete", List.of(completedStep)));
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

	private static class StreamingBlockingInformationNode implements ResearchNode {

		private final Sinks.Empty<Void> release;

		StreamingBlockingInformationNode(Sinks.Empty<Void> release) {
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
			return Flux.concat(Flux.just(ResearchEvent.message(state.threadId(), name(), "started",
					"started", null)), release.asMono()
				.thenReturn(ResearchEvent.message(state.threadId(), name(), "completed", "searched", List.of()))
				.flux());
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
			if (!researchCompleted || !processingCompleted) {
				state.researchTeamDecision(new ResearchTeamDecision(ResearchTeamRoute.PARALLEL_EXECUTOR, StepType.RESEARCH,
						2, researchCompleted ? 1 : 0, 0, researchCompleted ? 1 : 2));
			}
			else {
				state.researchTeamDecision(new ResearchTeamDecision(ResearchTeamRoute.REPORTER, null, 2, 2, 0, 0));
			}
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "decision", "routed",
					state.researchTeamDecision()));
		}

	}

	private static class ParallelExecutorNodeStub implements ResearchNode {

		@Override
		public int order() {
			return 35;
		}

		@Override
		public String name() {
			return "parallel_executor";
		}

		@Override
		public Flux<ResearchEvent> run(ResearchState state) {
			return Flux.just(ResearchEvent.message(state.threadId(), name(), "completed", "stubbed", null));
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
		ResearchStep research = new ResearchStep("Step", "Do work", false, StepType.RESEARCH, null,
				ResearchStep.STATUS_PENDING);
		research.id("step-1");
		ResearchStep processing = new ResearchStep("Summarize", "Process findings", false, StepType.PROCESSING, null,
				ResearchStep.STATUS_PENDING);
		processing.id("step-2");
		return new ResearchPlan("Plan", true, "Think", List.of(research, processing));
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

	private static class RecordingConversationMemoryService implements ConversationMemoryService {

		private final List<ConversationMessageRecord> messages = new ArrayList<>();

		@Override
		public Mono<ConversationMessageRecord> saveMessage(String sessionId, String threadId, String role,
				String content) {
			ConversationMessageRecord message = new ConversationMessageRecord(sessionId, threadId, role, content,
					Instant.now());
			messages.add(message);
			return Mono.just(message);
		}

		@Override
		public Flux<ConversationMessageRecord> findBySessionId(String sessionId) {
			return Flux.fromIterable(messages).filter(message -> sessionId.equals(message.sessionId()));
		}

		@Override
		public Mono<String> formatConversationHistory(String sessionId) {
			return findBySessionId(sessionId).map(ConversationMessageRecord::content).collectList()
				.map(items -> String.join("\n", items));
		}

		List<ConversationMessageRecord> messages() {
			return messages;
		}

	}

	private static class FailingConversationMemoryService implements ConversationMemoryService {

		@Override
		public Mono<ConversationMessageRecord> saveMessage(String sessionId, String threadId, String role,
				String content) {
			return Mono.error(new IllegalStateException("memory unavailable"));
		}

		@Override
		public Flux<ConversationMessageRecord> findBySessionId(String sessionId) {
			return Flux.empty();
		}

		@Override
		public Mono<String> formatConversationHistory(String sessionId) {
			return Mono.just("");
		}

	}

}
