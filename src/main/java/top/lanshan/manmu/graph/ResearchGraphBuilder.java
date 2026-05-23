package top.lanshan.manmu.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import top.lanshan.manmu.model.CoordinatorRoute;
import top.lanshan.manmu.model.PlanValidatorRoute;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchTeamRoute;
import top.lanshan.manmu.node.ResearchNode;
import top.lanshan.manmu.sessioncontext.SessionContextService;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ResearchGraphBuilder {

	public static final String COORDINATOR = "coordinator";

	public static final String REWRITE_MULTI_QUERY = "rewrite_multi_query";

	public static final String BACKGROUND_INVESTIGATOR = "background_investigator";

	public static final String PLANNER = "planner";

	public static final String PLAN_VALIDATOR = "plan_validator";

	public static final String HUMAN_FEEDBACK = "human_feedback";

	public static final String INFORMATION = "information";

	public static final String RESEARCH_TEAM = "research_team";

	public static final String RESEARCHER = "researcher";

	public static final String PROCESSOR = "processor";

	public static final String REPORTER = "reporter";

	private final Map<String, ResearchNode> nodes;

	public ResearchGraphBuilder(List<ResearchNode> nodes) {
		Objects.requireNonNull(nodes, "nodes must not be null");
		this.nodes = nodes.stream()
			.sorted(Comparator.comparingInt(ResearchNode::order))
			.collect(LinkedHashMap::new, (byName, node) -> byName.put(node.name(), node), Map::putAll);
	}

	public CompiledGraph buildAutoResearchGraph() {
		return buildAutoResearchGraph(null);
	}

	public CompiledGraph buildAutoResearchGraph(SessionContextService sessionContextService) {
		try {
			StateGraph graph = newGraph("research-auto");
			addRequiredNodes(graph, sessionContextService);
			graph.addEdge(StateGraph.START, COORDINATOR);
			graph.addConditionalEdges(COORDINATOR, edge(this::routeCoordinator),
					Map.of("direct_answer", StateGraph.END, "deep_research", REWRITE_MULTI_QUERY));
			graph.addEdge(REWRITE_MULTI_QUERY, BACKGROUND_INVESTIGATOR);
			graph.addEdge(BACKGROUND_INVESTIGATOR, PLANNER);
			graph.addEdge(PLANNER, PLAN_VALIDATOR);
			graph.addConditionalEdges(PLAN_VALIDATOR, edge(this::routePlanValidator),
					Map.of("planner", PLANNER, "research_team", INFORMATION));
			graph.addEdge(INFORMATION, RESEARCH_TEAM);
			graph.addConditionalEdges(RESEARCH_TEAM, edge(this::routeResearchTeam),
					Map.of("researcher", RESEARCHER, "processor", PROCESSOR, "reporter", REPORTER));
			graph.addEdge(RESEARCHER, RESEARCH_TEAM);
			graph.addEdge(PROCESSOR, RESEARCH_TEAM);
			graph.addEdge(REPORTER, StateGraph.END);
			return graph.compile(CompileConfig.builder().build());
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to build research graph", ex);
		}
	}

	public CompiledGraph buildPlanGateResearchGraph(SessionContextService sessionContextService) {
		try {
			StateGraph graph = newGraph("research-plan-gate");
			addRequiredNodes(graph, sessionContextService);
			addHumanFeedbackNode(graph);
			graph.addEdge(StateGraph.START, COORDINATOR);
			graph.addConditionalEdges(COORDINATOR, edge(this::routeCoordinator),
					Map.of("direct_answer", StateGraph.END, "deep_research", REWRITE_MULTI_QUERY));
			graph.addEdge(REWRITE_MULTI_QUERY, BACKGROUND_INVESTIGATOR);
			graph.addEdge(BACKGROUND_INVESTIGATOR, PLANNER);
			graph.addEdge(PLANNER, PLAN_VALIDATOR);
			graph.addConditionalEdges(PLAN_VALIDATOR, edge(this::routePlanValidatorWithHumanFeedback),
					Map.of("planner", PLANNER, "research_team", INFORMATION, "human_feedback", HUMAN_FEEDBACK));
			graph.addEdge(HUMAN_FEEDBACK, StateGraph.END);
			graph.addEdge(INFORMATION, RESEARCH_TEAM);
			graph.addConditionalEdges(RESEARCH_TEAM, edge(this::routeResearchTeam),
					Map.of("researcher", RESEARCHER, "processor", PROCESSOR, "reporter", REPORTER));
			graph.addEdge(RESEARCHER, RESEARCH_TEAM);
			graph.addEdge(PROCESSOR, RESEARCH_TEAM);
			graph.addEdge(REPORTER, StateGraph.END);
			return graph.compile(CompileConfig.builder().build());
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to build plan gate research graph", ex);
		}
	}

	public CompiledGraph buildAcceptedResumeGraph() {
		try {
			StateGraph graph = newGraph("research-accepted-resume");
			addHumanFeedbackNode(graph);
			graph.addNode(INFORMATION, ResearchNodeGraphAction.async(requiredNode(INFORMATION)));
			graph.addNode(RESEARCH_TEAM, ResearchNodeGraphAction.async(requiredNode(RESEARCH_TEAM)));
			graph.addNode(RESEARCHER, ResearchNodeGraphAction.async(requiredNode(RESEARCHER)));
			graph.addNode(PROCESSOR, ResearchNodeGraphAction.async(requiredNode(PROCESSOR)));
			graph.addNode(REPORTER, ResearchNodeGraphAction.async(requiredNode(REPORTER)));
			graph.addEdge(StateGraph.START, HUMAN_FEEDBACK);
			graph.addConditionalEdges(HUMAN_FEEDBACK, edge(this::routeAcceptedHumanFeedback),
					Map.of("research_team", INFORMATION));
			graph.addEdge(INFORMATION, RESEARCH_TEAM);
			graph.addConditionalEdges(RESEARCH_TEAM, edge(this::routeResearchTeam),
					Map.of("researcher", RESEARCHER, "processor", PROCESSOR, "reporter", REPORTER));
			graph.addEdge(RESEARCHER, RESEARCH_TEAM);
			graph.addEdge(PROCESSOR, RESEARCH_TEAM);
			graph.addEdge(REPORTER, StateGraph.END);
			return graph.compile(CompileConfig.builder().build());
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to build accepted resume research graph", ex);
		}
	}

	private StateGraph newGraph(String name) {
		return new StateGraph(name, KeyStrategy.builder().defaultStrategy(KeyStrategy.REPLACE).build(),
				new ResearchGraphStateSerializer());
	}

	private void addRequiredNodes(StateGraph graph, SessionContextService sessionContextService) throws Exception {
		graph.addNode(COORDINATOR, ResearchNodeGraphAction.async(requiredNode(COORDINATOR)));
		graph.addNode(REWRITE_MULTI_QUERY, ResearchNodeGraphAction.async(requiredNode(REWRITE_MULTI_QUERY)));
		graph.addNode(BACKGROUND_INVESTIGATOR, ResearchNodeGraphAction.async(requiredNode(BACKGROUND_INVESTIGATOR)));
		graph.addNode(PLANNER, plannerAction(requiredNode(PLANNER), sessionContextService));
		graph.addNode(PLAN_VALIDATOR, ResearchNodeGraphAction.async(requiredNode(PLAN_VALIDATOR)));
		graph.addNode(INFORMATION, ResearchNodeGraphAction.async(requiredNode(INFORMATION)));
		graph.addNode(RESEARCH_TEAM, ResearchNodeGraphAction.async(requiredNode(RESEARCH_TEAM)));
		graph.addNode(RESEARCHER, ResearchNodeGraphAction.async(requiredNode(RESEARCHER)));
		graph.addNode(PROCESSOR, ResearchNodeGraphAction.async(requiredNode(PROCESSOR)));
		graph.addNode(REPORTER, ResearchNodeGraphAction.async(requiredNode(REPORTER)));
	}

	private void addHumanFeedbackNode(StateGraph graph) throws Exception {
		graph.addNode(HUMAN_FEEDBACK, ResearchNodeGraphAction.async(requiredNode(HUMAN_FEEDBACK)));
	}

	private ResearchNode requiredNode(String name) {
		ResearchNode node = nodes.get(name);
		if (node == null) {
			throw new IllegalStateException("Missing research node: " + name);
		}
		return node;
	}

	private AsyncNodeAction plannerAction(ResearchNode plannerNode, SessionContextService sessionContextService) {
		if (sessionContextService == null) {
			return ResearchNodeGraphAction.async(plannerNode);
		}
		ResearchNodeGraphAction plannerAction = ResearchNodeGraphAction.from(plannerNode);
		return AsyncNodeAction.node_async(state -> {
			loadBackgroundContext(state, sessionContextService);
			return plannerAction.apply(state);
		});
	}

	private void loadBackgroundContext(OverAllState state, SessionContextService sessionContextService) {
		ResearchState researchState = ResearchGraphState.researchState(state.data());
		String context = sessionContextService.formatRecentCompletedReports(researchState.sessionId(),
				researchState.threadId()).block();
		researchState.backgroundContext(context);
	}

	private AsyncEdgeAction edge(EdgeAction action) {
		return AsyncEdgeAction.edge_async(action);
	}

	private String routeCoordinator(com.alibaba.cloud.ai.graph.OverAllState state) {
		CoordinatorRoute route = ResearchGraphState.coordinatorRoute(state.data())
			.orElseThrow(() -> new IllegalStateException("Coordinator did not produce a route"));
		return switch (route) {
			case DIRECT_ANSWER -> "direct_answer";
			case DEEP_RESEARCH -> "deep_research";
		};
	}

	private String routePlanValidator(com.alibaba.cloud.ai.graph.OverAllState state) {
		PlanValidatorRoute route = ResearchGraphState.planValidatorRoute(state.data())
			.orElseThrow(() -> new IllegalStateException("Plan validator did not produce a route"));
		return switch (route) {
			case PLANNER -> "planner";
			case RESEARCH_TEAM -> "research_team";
			case FAILED -> throw new IllegalStateException("Planner did not produce a valid plan after "
					+ ResearchGraphState.researchState(state.data()).planIterations() + " attempt(s): "
					+ ResearchGraphState.researchState(state.data()).planValidatorDecision().reason());
			case HUMAN_FEEDBACK -> throw new IllegalStateException(
					"GraphResearchRunner does not support human feedback in auto-complete mode");
		};
	}

	private String routePlanValidatorWithHumanFeedback(com.alibaba.cloud.ai.graph.OverAllState state) {
		PlanValidatorRoute route = ResearchGraphState.planValidatorRoute(state.data())
			.orElseThrow(() -> new IllegalStateException("Plan validator did not produce a route"));
		return switch (route) {
			case PLANNER -> "planner";
			case RESEARCH_TEAM -> "research_team";
			case HUMAN_FEEDBACK -> "human_feedback";
			case FAILED -> throw new IllegalStateException("Planner did not produce a valid plan after "
					+ ResearchGraphState.researchState(state.data()).planIterations() + " attempt(s): "
					+ ResearchGraphState.researchState(state.data()).planValidatorDecision().reason());
		};
	}

	private String routeAcceptedHumanFeedback(com.alibaba.cloud.ai.graph.OverAllState state) {
		return switch (ResearchGraphState.humanFeedbackRoute(state.data())
			.orElseThrow(() -> new IllegalStateException("Human feedback did not produce a route"))) {
			case RESEARCH_TEAM -> "research_team";
			case PLANNER -> throw new UnsupportedOperationException(
					"GraphResearchRunner does not support rejected feedback yet");
			case WAITING -> throw new IllegalStateException("Accepted resume did not receive human feedback input");
		};
	}

	private String routeResearchTeam(com.alibaba.cloud.ai.graph.OverAllState state) {
		ResearchTeamRoute route = ResearchGraphState.researchTeamRoute(state.data())
			.orElseThrow(() -> new IllegalStateException("Research team did not produce a route"));
		return switch (route) {
			case RESEARCHER -> "researcher";
			case PROCESSOR -> "processor";
			case REPORTER -> "reporter";
		};
	}

}
