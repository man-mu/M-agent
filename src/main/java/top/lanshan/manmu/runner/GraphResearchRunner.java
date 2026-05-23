package top.lanshan.manmu.runner;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import top.lanshan.manmu.graph.ResearchGraphBuilder;
import top.lanshan.manmu.graph.ResearchGraphState;
import top.lanshan.manmu.graph.ResearchGraphStateKeys;
import top.lanshan.manmu.model.HumanFeedbackRoute;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.report.ReportService;
import top.lanshan.manmu.sessioncontext.SessionContextService;
import top.lanshan.manmu.sessionhistory.SessionHistoryService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class GraphResearchRunner implements ResearchRunner {

	private final CompiledGraph graph;

	private final CompiledGraph planGateGraph;

	private final CompiledGraph resumeGraph;

	private final ReportService reportService;

	private final SessionHistoryService sessionHistoryService;

	private final Map<String, Map<String, Object>> pausedStates = new ConcurrentHashMap<>();

	private final Map<String, RunningResearch> runningResearch = new ConcurrentHashMap<>();

	public GraphResearchRunner(ResearchGraphBuilder graphBuilder, ReportService reportService,
			SessionHistoryService sessionHistoryService, SessionContextService sessionContextService) {
		ResearchGraphBuilder requiredGraphBuilder = Objects.requireNonNull(graphBuilder, "graphBuilder must not be null");
		this.graph = requiredGraphBuilder.buildAutoResearchGraph(sessionContextService);
		this.planGateGraph = requiredGraphBuilder.buildPlanGateResearchGraph(sessionContextService);
		this.resumeGraph = requiredGraphBuilder.buildResumeGraph(sessionContextService);
		this.reportService = Objects.requireNonNull(reportService, "reportService must not be null");
		this.sessionHistoryService = Objects.requireNonNull(sessionHistoryService,
				"sessionHistoryService must not be null");
		Objects.requireNonNull(sessionContextService, "sessionContextService must not be null");
	}

	@Override
	public Flux<ResearchEvent> run(ResearchRequest request) {
		return startHistoryThenRun(ResearchState.from(request));
	}

	@Override
	public Flux<ResearchEvent> runChat(ResearchRequest request, String sessionId) {
		ResearchState state = ResearchState.from(request, sessionId);
		return withRunningStop(state.threadId(), startHistoryThenRun(state));
	}

	@Override
	public Flux<ResearchEvent> runUntilPlanGate(ResearchRequest request, String sessionId) {
		ResearchState state = ResearchState.from(request, sessionId);
		state.autoAcceptedPlan(false);
		return withRunningStop(state.threadId(), sessionHistoryService.start(state.threadId(), state.sessionId(), state.query())
			.thenMany(runPlanGateGraph(state))
			.onErrorResume(error -> markFailedThenReturnError(state, error)));
	}

	@Override
	public Flux<ResearchEvent> resume(String threadId, ResumeDecision decision) {
		Map<String, Object> graphState = pausedStates.get(threadId);
		if (graphState == null) {
			return Flux.just(ResearchEvent.error(threadId, "human_feedback",
					new IllegalArgumentException("No paused research state found for thread: " + threadId)));
		}
		graphState = mutableGraphState(graphState);
		ResearchState state = ResearchGraphState.researchState(graphState);
		pausedStates.remove(threadId);
		state.humanFeedback(decision.accepted(), decision.feedbackContent());
		ResearchGraphState.setResearchState(graphState, state);
		return withRunningStop(state.threadId(), sessionHistoryService.markRunning(state.threadId())
			.thenMany(runResumeGraph(graphState))
			.onErrorResume(error -> markFailedThenReturnError(state, error)));
	}

	@Override
	public Mono<Boolean> stopAndRecord(String threadId) {
		if (threadId == null || threadId.isBlank()) {
			return Mono.just(false);
		}
		RunningResearch running = runningResearch.remove(threadId);
		if (running != null) {
			pausedStates.remove(threadId);
			running.stop();
			return sessionHistoryService.markStopped(threadId).thenReturn(true).defaultIfEmpty(true);
		}
		boolean stopped = pausedStates.remove(threadId) != null;
		if (stopped) {
			return sessionHistoryService.markStopped(threadId).thenReturn(true).defaultIfEmpty(true);
		}
		return Mono.just(false);
	}

	private Flux<ResearchEvent> startHistoryThenRun(ResearchState state) {
		return sessionHistoryService.start(state.threadId(), state.sessionId(), state.query())
			.thenMany(runAutoResearchGraph(state))
			.onErrorResume(error -> markFailedThenReturnError(state, error));
	}

	private Flux<ResearchEvent> runAutoResearchGraph(ResearchState state) {
		Map<String, Object> graphState = ResearchGraphState.from(state);
		LatestGraphState latestGraphState = new LatestGraphState(graphState);
		List<ResearchEvent> emittedEvents = new ArrayList<>();
		return graph.fluxStream(graphState)
			.concatMap(output -> {
				latestGraphState.update(output);
				return emitNewEvents(output, emittedEvents);
			})
			.concatWith(Flux.defer(
					() -> saveCompletedReport(ResearchGraphState.researchState(latestGraphState.graphState()))))
			.subscribeOn(Schedulers.boundedElastic());
	}

	private Flux<ResearchEvent> runPlanGateGraph(ResearchState state) {
		Map<String, Object> graphState = ResearchGraphState.from(state);
		LatestGraphState latestGraphState = new LatestGraphState(graphState);
		List<ResearchEvent> emittedEvents = new ArrayList<>();
		return planGateGraph.fluxStream(graphState)
			.concatMap(output -> {
				latestGraphState.update(output);
				return emitNewEvents(output, emittedEvents);
			})
			.concatWith(Flux.defer(() -> handlePlanGateCompletion(latestGraphState.graphState())))
			.subscribeOn(Schedulers.boundedElastic());
	}

	private Flux<ResearchEvent> runResumeGraph(Map<String, Object> graphState) {
		LatestGraphState latestGraphState = new LatestGraphState(graphState);
		List<ResearchEvent> emittedEvents = new ArrayList<>(ResearchGraphState.events(graphState));
		return resumeGraph.fluxStream(graphState)
			.concatMap(output -> {
				latestGraphState.update(output);
				return emitNewEvents(output, emittedEvents);
			})
			.concatWith(Flux.defer(() -> handleResumeCompletion(latestGraphState.graphState())))
			.subscribeOn(Schedulers.boundedElastic());
	}

	private Flux<ResearchEvent> emitNewEvents(NodeOutput output, List<ResearchEvent> emittedEvents) {
		Map<String, Object> graphState = graphStateData(output);
		if (graphState == null || !graphState.containsKey(ResearchGraphStateKeys.EVENTS)) {
			return Flux.empty();
		}
		List<ResearchEvent> currentEvents = ResearchGraphState.events(graphState);
		List<ResearchEvent> newEvents = currentEvents.stream()
			.filter(event -> !emittedEvents.contains(event))
			.toList();
		emittedEvents.addAll(newEvents);
		return Flux.fromIterable(newEvents);
	}

	private Flux<ResearchEvent> handlePlanGateCompletion(Map<String, Object> graphState) {
		ResearchState state = ResearchGraphState.researchState(graphState);
		if (state.directAnswerRoute()) {
			return saveCompletedReport(state);
		}
		if (waitingForHumanFeedback(graphState)) {
			pausedStates.put(state.threadId(), mutableGraphState(graphState));
			return sessionHistoryService.markPaused(state.threadId()).thenMany(Flux.empty());
		}
		return saveCompletedReport(state);
	}

	private Flux<ResearchEvent> handleResumeCompletion(Map<String, Object> graphState) {
		ResearchState state = ResearchGraphState.researchState(graphState);
		if (waitingForHumanFeedback(graphState)) {
			pausedStates.put(state.threadId(), mutableGraphState(graphState));
			return sessionHistoryService.markPaused(state.threadId()).thenMany(Flux.empty());
		}
		return saveCompletedReport(state);
	}

	private Map<String, Object> mutableGraphState(Map<String, Object> graphState) {
		Map<String, Object> copy = new LinkedHashMap<>(graphState);
		copy.put(ResearchGraphStateKeys.EVENTS, new ArrayList<>(ResearchGraphState.events(graphState)));
		return copy;
	}

	private static Map<String, Object> graphStateData(NodeOutput output) {
		if (output == null || output.state() == null || output.state().data() == null) {
			return null;
		}
		return output.state().data();
	}

	private String lastEventNode(Map<String, Object> graphState) {
		List<ResearchEvent> events = ResearchGraphState.events(graphState);
		if (events.isEmpty()) {
			return null;
		}
		return events.get(events.size() - 1).node();
	}

	private boolean waitingForHumanFeedback(Map<String, Object> graphState) {
		return ResearchGraphState.humanFeedbackRoute(graphState)
			.filter(HumanFeedbackRoute.WAITING::equals)
			.isPresent()
				|| ResearchGraphBuilder.HUMAN_FEEDBACK.equals(lastEventNode(graphState));
	}

	private Flux<ResearchEvent> saveCompletedReport(ResearchState state) {
		return reportService.saveCompletedReport(state.threadId(), state.sessionId(), state.query(), state.report())
			.flatMap(report -> sessionHistoryService.markCompleted(state.threadId(), report.threadId()))
			.thenReturn(ResearchEvent.done(state.threadId(), "Research workflow completed", state.report()))
			.flux();
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
				previous.stop();
			}
			return events.takeUntilOther(running.stopSignal().asMono())
				.concatWith(Flux.defer(() -> running.isStopped()
						? Flux.just(ResearchEvent.stopped(threadId, "Research workflow stopped")) : Flux.empty()))
				.doFinally(signal -> runningResearch.remove(threadId, running));
		});
	}

	private static final class LatestGraphState {

		private Map<String, Object> graphState;

		private LatestGraphState(Map<String, Object> graphState) {
			this.graphState = graphState;
		}

		private void update(NodeOutput output) {
			Map<String, Object> data = graphStateData(output);
			if (data != null) {
				this.graphState = data;
			}
		}

		private Map<String, Object> graphState() {
			return graphState;
		}

	}

	private record RunningResearch(Sinks.Empty<Void> stopSignal, AtomicBoolean stopped) {

		RunningResearch() {
			this(Sinks.empty(), new AtomicBoolean(false));
		}

		void stop() {
			stopped.set(true);
			stopSignal.tryEmitEmpty();
		}

		boolean isStopped() {
			return stopped.get();
		}

	}

}
