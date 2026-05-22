package top.lanshan.manmu.runner;

import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchTeamRoute;
import top.lanshan.manmu.model.PlanValidatorRoute;
import top.lanshan.manmu.model.HumanFeedbackRoute;
import top.lanshan.manmu.node.ResearchNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import top.lanshan.manmu.report.ReportService;
import top.lanshan.manmu.sessioncontext.SessionContextService;
import top.lanshan.manmu.sessionhistory.SessionHistoryService;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(prefix = "mvp.research", name = "runner", havingValue = "simple", matchIfMissing = true)
public class SimpleResearchRunner implements ResearchRunner {

	private final List<ResearchNode> nodes;

	private final ResearchNode coordinatorNode;

	private final ResearchNode plannerNode;

	private final ResearchNode planValidatorNode;

	private final ResearchNode humanFeedbackNode;

	private final ResearchNode queryRewriteNode;

	private final ResearchNode backgroundInvestigatorNode;

	private final ResearchNode informationNode;

	private final ResearchNode researchTeamNode;

	private final ResearchNode researcherNode;

	private final ResearchNode processorNode;

	private final ResearchNode reporterNode;

	private final ReportService reportService;

	private final SessionHistoryService sessionHistoryService;

	private final SessionContextService sessionContextService;

	private final Map<String, ResearchState> pausedStates = new ConcurrentHashMap<>();

	private final Map<String, RunningResearch> runningResearch = new ConcurrentHashMap<>();

	public SimpleResearchRunner(List<ResearchNode> nodes, ReportService reportService,
			SessionHistoryService sessionHistoryService, SessionContextService sessionContextService) {
		this.nodes = nodes.stream().sorted(Comparator.comparingInt(ResearchNode::order)).toList();
		this.coordinatorNode = requiredNode("coordinator");
		this.queryRewriteNode = requiredNode("rewrite_multi_query");
		this.backgroundInvestigatorNode = requiredNode("background_investigator");
		this.plannerNode = requiredNode("planner");
		this.planValidatorNode = requiredNode("plan_validator");
		this.humanFeedbackNode = requiredNode("human_feedback");
		this.informationNode = requiredNode("information");
		this.researchTeamNode = requiredNode("research_team");
		this.researcherNode = requiredNode("researcher");
		this.processorNode = requiredNode("processor");
		this.reporterNode = requiredNode("reporter");
		this.reportService = reportService;
		this.sessionHistoryService = sessionHistoryService;
		this.sessionContextService = sessionContextService;
	}

	@Override
	public Flux<ResearchEvent> run(ResearchRequest request) {
		ResearchState state = ResearchState.from(request);
		return startHistoryThenRun(state, runToCompletion(state));
	}

	@Override
	public Flux<ResearchEvent> runChat(ResearchRequest request, String sessionId) {
		ResearchState state = ResearchState.from(request, sessionId);
		return withRunningStop(state.threadId(), startHistoryThenRun(state, runToCompletion(state)));
	}

	@Override
	public Flux<ResearchEvent> runUntilPlanGate(ResearchRequest request, String sessionId) {
		ResearchState state = ResearchState.from(request, sessionId);
		state.autoAcceptedPlan(false);

		return withRunningStop(state.threadId(), startHistoryThenRun(state, runCoordinator(state)
			.concatWith(Flux.defer(() -> {
				if (state.directAnswerRoute()) {
					return saveCompletedReport(state);
				}
				return runPlanValidation(state).concatWith(Flux.defer(() -> routeValidatedPlan(state)));
			}))
			.subscribeOn(Schedulers.boundedElastic())));
	}

	@Override
	public Flux<ResearchEvent> resume(String threadId, ResumeDecision decision) {
		ResearchState state = pausedStates.remove(threadId);
		if (state == null) {
			return Flux.just(ResearchEvent.error(threadId, "human_feedback",
					new IllegalArgumentException("No paused research state found for thread: " + threadId)));
		}
		if (decision.accepted()) {
			state.humanFeedback(true, null);
			return withRunningStop(state.threadId(), markRunningThenRun(state,
					runHumanFeedbackRoute(state).subscribeOn(Schedulers.boundedElastic())));
		}
		state.humanFeedback(false, decision.feedbackContent());
		return withRunningStop(state.threadId(),
				markRunningThenRun(state, runHumanFeedbackRoute(state).subscribeOn(Schedulers.boundedElastic())));
	}

	public boolean stop(String threadId) {
		return stopAndRecord(threadId).blockOptional().orElse(false);
	}

	@Override
	public Mono<Boolean> stopAndRecord(String threadId) {
		if (threadId == null || threadId.isBlank()) {
			return Mono.just(false);
		}
		RunningResearch running = runningResearch.remove(threadId);
		if (running != null) {
			pausedStates.remove(threadId);
			running.stop(threadId);
			return sessionHistoryService.markStopped(threadId).thenReturn(true).defaultIfEmpty(true);
		}
		boolean stopped = pausedStates.remove(threadId) != null;
		if (stopped) {
			return sessionHistoryService.markStopped(threadId).thenReturn(true).defaultIfEmpty(true);
		}
		return Mono.just(false);
	}

	private Flux<ResearchEvent> runToCompletion(ResearchState state) {
		return Flux.concat(runCoordinator(state), Flux.defer(() -> {
			if (state.directAnswerRoute()) {
				return saveCompletedReport(state);
			}
			return runPlanValidation(state).concatWith(Flux.defer(() -> routeValidatedPlanWithSave(state)));
		})).subscribeOn(Schedulers.boundedElastic());
	}

	private Flux<ResearchEvent> runCoordinator(ResearchState state) {
		return coordinatorNode.run(state);
	}

	private Flux<ResearchEvent> runPlanner(ResearchState state) {
		Flux<ResearchEvent> rewriteEvents = state.queryRewriteCompleted() ? Flux.empty() : queryRewriteNode.run(state);
		Flux<ResearchEvent> backgroundInvestigationEvents = state.backgroundInvestigationCompleted() ? Flux.empty()
				: backgroundInvestigatorNode.run(state);
		return rewriteEvents.concatWith(backgroundInvestigationEvents)
			.concatWith(loadBackgroundContext(state)
				.thenMany(Flux.defer(() -> plannerNode.run(state)).subscribeOn(Schedulers.boundedElastic())));
	}

	private Flux<ResearchEvent> runPlanValidation(ResearchState state) {
		return runPlanner(state).concatWith(planValidatorNode.run(state)).concatWith(Flux.defer(() -> {
			PlanValidatorRoute route = state.planValidatorDecision().nextRoute();
			if (PlanValidatorRoute.PLANNER.equals(route)) {
				return runPlanValidation(state);
			}
			if (PlanValidatorRoute.FAILED.equals(route)) {
				return Flux.error(new IllegalStateException("Planner did not produce a valid plan after "
						+ state.planIterations() + " attempt(s): " + state.planValidatorDecision().reason()));
			}
			return Flux.empty();
		}));
	}

	private Flux<ResearchEvent> routeValidatedPlan(ResearchState state) {
		PlanValidatorRoute route = state.planValidatorDecision().nextRoute();
		return switch (route) {
			case RESEARCH_TEAM -> resumeExecutionWithoutSaving(state);
			case HUMAN_FEEDBACK -> pauseForHumanFeedback(state);
			case PLANNER -> Flux.error(new IllegalStateException("Plan validator retry route was not exhausted"));
			case FAILED -> Flux.error(new IllegalStateException(state.planValidatorDecision().reason()));
		};
	}

	private Flux<ResearchEvent> routeValidatedPlanWithSave(ResearchState state) {
		PlanValidatorRoute route = state.planValidatorDecision().nextRoute();
		return switch (route) {
			case RESEARCH_TEAM -> resumeExecution(state);
			case HUMAN_FEEDBACK -> pauseForHumanFeedback(state);
			case PLANNER -> Flux.error(new IllegalStateException("Plan validator retry route was not exhausted"));
			case FAILED -> Flux.error(new IllegalStateException(state.planValidatorDecision().reason()));
		};
	}

	private Flux<ResearchEvent> pauseForHumanFeedback(ResearchState state) {
		return Flux.defer(() -> {
			pausedStates.put(state.threadId(), state);
			return sessionHistoryService.markPaused(state.threadId())
				.thenMany(humanFeedbackNode.run(state));
		});
	}

	private Flux<ResearchEvent> runHumanFeedbackRoute(ResearchState state) {
		return humanFeedbackNode.run(state).concatWith(Flux.defer(() -> routeHumanFeedback(state)));
	}

	private Flux<ResearchEvent> routeHumanFeedback(ResearchState state) {
		HumanFeedbackRoute route = state.humanFeedbackDecision().nextRoute();
		return switch (route) {
			case RESEARCH_TEAM -> resumeExecution(state);
			case PLANNER -> runPlanValidation(state).concatWith(Flux.defer(() -> routeValidatedPlan(state)));
			case WAITING -> pauseForHumanFeedback(state);
		};
	}

	private Mono<Void> loadBackgroundContext(ResearchState state) {
		return sessionContextService.formatRecentCompletedReports(state.sessionId(), state.threadId())
			.doOnNext(state::backgroundContext)
			.then();
	}

	private Flux<ResearchEvent> resumeExecution(ResearchState state) {
		return resumeExecutionWithoutSaving(state)
			.concatWith(Flux.defer(() -> saveCompletedReport(state)));
	}

	private Flux<ResearchEvent> resumeExecutionWithoutSaving(ResearchState state) {
		return Flux.concat(informationNode.run(state),
				Flux.defer(() -> researchLoop(state, state.plan().steps().size() + 1)), reporterNode.run(state));
	}

	private Flux<ResearchEvent> saveCompletedReport(ResearchState state) {
		return reportService.saveCompletedReport(state.threadId(), state.sessionId(), state.query(), state.report())
			.flatMap(report -> sessionHistoryService.markCompleted(state.threadId(), report.threadId()))
			.thenReturn(ResearchEvent.done(state.threadId(), "Research workflow completed", state.report()))
			.flux();
	}

	private Flux<ResearchEvent> startHistoryThenRun(ResearchState state, Flux<ResearchEvent> events) {
		return sessionHistoryService.start(state.threadId(), state.sessionId(), state.query())
			.thenMany(events)
			.onErrorResume(error -> markFailedThenReturnError(state, error));
	}

	private Flux<ResearchEvent> markRunningThenRun(ResearchState state, Flux<ResearchEvent> events) {
		return sessionHistoryService.markRunning(state.threadId())
			.thenMany(events)
			.onErrorResume(error -> markFailedThenReturnError(state, error));
	}

	private Flux<ResearchEvent> markFailedThenReturnError(ResearchState state, Throwable error) {
		return sessionHistoryService.markFailed(state.threadId(), errorMessage(error))
			.onErrorResume(markError -> Mono.empty())
			.thenMany(Flux.just(ResearchEvent.error(state.threadId(), "runner", error)));
	}

	private String errorMessage(Throwable error) {
		if (error == null) {
			return "Unknown error";
		}
		if (error.getMessage() != null && !error.getMessage().isBlank()) {
			return error.getMessage();
		}
		return error.getClass().getSimpleName();
	}

	private Flux<ResearchEvent> withRunningStop(String threadId, Flux<ResearchEvent> events) {
		return Flux.defer(() -> {
			RunningResearch running = new RunningResearch();
			RunningResearch previous = runningResearch.put(threadId, running);
			if (previous != null) {
				previous.stop(threadId);
			}
			return events.takeUntilOther(running.stopSignal().asMono())
				.concatWith(Flux.defer(() -> running.isStopped()
						? Flux.just(ResearchEvent.stopped(threadId, "Research workflow stopped")) : Flux.empty()))
				.doFinally(signal -> runningResearch.remove(threadId, running));
		});
	}

	private Flux<ResearchEvent> researchLoop(ResearchState state, int remainingCycles) {
		if (remainingCycles < 1) {
			return Flux.error(new IllegalStateException("Research team did not reach reporter route"));
		}
		return researchTeamNode.run(state).concatWith(Flux.defer(() -> {
			if (state.researchTeamDecision().nextRoute() == ResearchTeamRoute.REPORTER) {
				return Flux.empty();
			}
			ResearchNode executorNode = switch (state.researchTeamDecision().nextRoute()) {
				case RESEARCHER -> researcherNode;
				case PROCESSOR -> processorNode;
				case REPORTER -> reporterNode;
			};
			return executorNode.run(state).concatWith(researchLoop(state, remainingCycles - 1));
		}));
	}

	private ResearchNode requiredNode(String name) {
		return nodes.stream()
			.filter(node -> name.equals(node.name()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Missing research node: " + name));
	}

	private record RunningResearch(Sinks.Empty<Void> stopSignal, AtomicBoolean stopped) {

		RunningResearch() {
			this(Sinks.empty(), new AtomicBoolean(false));
		}

		void stop(String threadId) {
			stopped.set(true);
			stopSignal.tryEmitEmpty();
		}

		boolean isStopped() {
			return stopped.get();
		}

	}

}
