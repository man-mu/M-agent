package top.lanshan.manmu.runner;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.lanshan.manmu.graph.ResearchGraphBuilder;
import top.lanshan.manmu.graph.ResearchGraphState;
import top.lanshan.manmu.graph.ResearchGraphStateKeys;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.report.ReportService;
import top.lanshan.manmu.sessioncontext.SessionContextService;
import top.lanshan.manmu.sessionhistory.SessionHistoryService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@ConditionalOnProperty(prefix = "mvp.research", name = "runner", havingValue = "graph")
public class GraphResearchRunner implements ResearchRunner {

	private final CompiledGraph graph;

	private final ReportService reportService;

	private final SessionHistoryService sessionHistoryService;

	public GraphResearchRunner(ResearchGraphBuilder graphBuilder, ReportService reportService,
			SessionHistoryService sessionHistoryService, SessionContextService sessionContextService) {
		this.graph = Objects.requireNonNull(graphBuilder, "graphBuilder must not be null")
			.buildAutoResearchGraph(sessionContextService);
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
		return startHistoryThenRun(ResearchState.from(request, sessionId));
	}

	@Override
	public Flux<ResearchEvent> runUntilPlanGate(ResearchRequest request, String sessionId) {
		ResearchState state = ResearchState.from(request, sessionId);
		return Flux.just(ResearchEvent.error(state.threadId(), "runner",
				new UnsupportedOperationException("GraphResearchRunner does not support plan gate yet")));
	}

	@Override
	public Flux<ResearchEvent> resume(String threadId, ResumeDecision decision) {
		return Flux.just(ResearchEvent.error(threadId, "human_feedback",
				new UnsupportedOperationException("GraphResearchRunner does not support resume yet")));
	}

	@Override
	public Mono<Boolean> stopAndRecord(String threadId) {
		return Mono.just(false);
	}

	private Flux<ResearchEvent> startHistoryThenRun(ResearchState state) {
		return sessionHistoryService.start(state.threadId(), state.sessionId(), state.query())
			.thenMany(runAutoResearchGraph(state))
			.onErrorResume(error -> markFailedThenReturnError(state, error));
	}

	private Flux<ResearchEvent> runAutoResearchGraph(ResearchState state) {
		Map<String, Object> graphState = ResearchGraphState.from(state);
		List<ResearchEvent> emittedEvents = new ArrayList<>();
		return graph.fluxStream(graphState)
			.concatMap(output -> emitNewEvents(output, emittedEvents))
			.concatWith(Flux.defer(() -> saveCompletedReport(state)))
			.subscribeOn(Schedulers.boundedElastic());
	}

	private Flux<ResearchEvent> emitNewEvents(NodeOutput output, List<ResearchEvent> emittedEvents) {
		if (!output.state().data().containsKey(ResearchGraphStateKeys.EVENTS)) {
			return Flux.empty();
		}
		List<ResearchEvent> currentEvents = ResearchGraphState.events(output.state().data());
		if (currentEvents.size() < emittedEvents.size()) {
			throw new IllegalStateException("Graph event state shrank while running node " + output.node());
		}
		List<ResearchEvent> newEvents = List.copyOf(currentEvents.subList(emittedEvents.size(), currentEvents.size()));
		emittedEvents.addAll(newEvents);
		return Flux.fromIterable(newEvents);
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

}
