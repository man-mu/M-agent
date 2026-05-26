package top.lanshan.manmu.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import top.lanshan.manmu.agent.ProcessorAgent;
import top.lanshan.manmu.agent.ResearcherAgent;
import top.lanshan.manmu.config.AdvancedExecutionProperties;
import top.lanshan.manmu.config.RagProperties;
import top.lanshan.manmu.model.CoordinatorRoute;
import top.lanshan.manmu.model.HumanFeedbackRoute;
import top.lanshan.manmu.model.PlanValidatorRoute;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.ResearchTeamRoute;
import top.lanshan.manmu.model.StepExecutionStatus;
import top.lanshan.manmu.model.StepType;
import top.lanshan.manmu.node.CoderNode;
import top.lanshan.manmu.node.ResearchNode;
import top.lanshan.manmu.node.ResearcherNode;
import top.lanshan.manmu.sessioncontext.SessionContextService;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the advanced execution Graph. Extension points:
 * <ul>
 *   <li>RAG: insert a new node (e.g. {@code rag_retrieval}) between
 *       {@code BACKGROUND_INVESTIGATOR} and {@code PLANNER}.</li>
 *   <li>MCP: inject {@code McpToolRegistry} into {@code ResearcherNode} /
 *       {@code CoderNode} through their agents.</li>
 *   <li>Docker coder: replace {@code CoderNode}'s {@code ProcessorAgent}
 *       with a {@code DockerCoderAgent} that spawns a sandbox container.</li>
 *   <li>Frontend: the stable SSE event envelope from Stage 1 already
 *       supports a node-timeline debug console.</li>
 * </ul>
 */
public class ResearchGraphBuilder {

	public static final String COORDINATOR = "coordinator";

	public static final String REWRITE_MULTI_QUERY = "rewrite_multi_query";

	public static final String BACKGROUND_INVESTIGATOR = "background_investigator";

	public static final String PLANNER = "planner";

	public static final String PLAN_VALIDATOR = "plan_validator";

	public static final String HUMAN_FEEDBACK = "human_feedback";

	public static final String INFORMATION = "information";

	public static final String RESEARCH_TEAM = "research_team";

	public static final String PARALLEL_EXECUTOR = "parallel_executor";

	public static final String REPORTER = "reporter";

	public static final String USER_FILE_RAG = "user_file_rag";

	public static final String PROFESSIONAL_KB_DECISION = "professional_kb_decision";

	public static final String PROFESSIONAL_KB_RAG = "professional_kb_rag";

	private final Map<String, ResearchNode> nodes;

	private final AdvancedExecutionProperties advancedExecutionProperties;

	private final ResearcherAgent researcherAgent;

	private final ProcessorAgent processorAgent;

	private final RagProperties ragProperties;

	public ResearchGraphBuilder(List<ResearchNode> nodes) {
		this(nodes, new AdvancedExecutionProperties(), null, null, new RagProperties());
	}

	public ResearchGraphBuilder(List<ResearchNode> nodes, AdvancedExecutionProperties advancedExecutionProperties,
			ResearcherAgent researcherAgent, ProcessorAgent processorAgent) {
		this(nodes, advancedExecutionProperties, researcherAgent, processorAgent, new RagProperties());
	}

	public ResearchGraphBuilder(List<ResearchNode> nodes, AdvancedExecutionProperties advancedExecutionProperties,
			ResearcherAgent researcherAgent, ProcessorAgent processorAgent, RagProperties ragProperties) {
		Objects.requireNonNull(nodes, "nodes must not be null");
		this.nodes = nodes.stream()
			.sorted(Comparator.comparingInt(ResearchNode::order))
			.collect(LinkedHashMap::new, (byName, node) -> byName.put(node.name(), node), Map::putAll);
		this.advancedExecutionProperties =
				advancedExecutionProperties == null ? new AdvancedExecutionProperties() : advancedExecutionProperties;
		this.researcherAgent = researcherAgent;
		this.processorAgent = processorAgent;
		this.ragProperties = ragProperties == null ? new RagProperties() : ragProperties;
	}

	public CompiledGraph buildAutoResearchGraph() {
		return buildAutoResearchGraph(null);
	}

	public CompiledGraph buildAutoResearchGraph(SessionContextService sessionContextService) {
		return buildAdvancedAutoResearchGraph(sessionContextService);
	}

	public CompiledGraph buildPlanGateResearchGraph(SessionContextService sessionContextService) {
		return buildAdvancedPlanGateResearchGraph(sessionContextService);
	}

	public CompiledGraph buildAcceptedResumeGraph() {
		return buildResumeGraph(null);
	}

	public CompiledGraph buildResumeGraph(SessionContextService sessionContextService) {
		return buildAdvancedResumeGraph(sessionContextService);
	}

	private CompiledGraph buildAdvancedAutoResearchGraph(SessionContextService sessionContextService) {
		try {
			StateGraph graph = newGraph("research-auto-advanced");
			Map<String, ResearchNode> executorNodes = executorNodes();
			addRequiredNodes(graph, sessionContextService);
			addExecutorNodes(graph, executorNodes);
			graph.addEdge(StateGraph.START, COORDINATOR);
			graph.addConditionalEdges(COORDINATOR, edge(this::routeCoordinator),
					Map.of("direct_answer", StateGraph.END, "deep_research", REWRITE_MULTI_QUERY));
			addRagConditionalEdge(graph, REWRITE_MULTI_QUERY, BACKGROUND_INVESTIGATOR);
			graph.addEdge(BACKGROUND_INVESTIGATOR, PLANNER);
			graph.addEdge(PLANNER, PLAN_VALIDATOR);
			graph.addConditionalEdges(PLAN_VALIDATOR, edge(this::routePlanValidator),
					Map.of("planner", PLANNER, "research_team", INFORMATION));
			graph.addEdge(INFORMATION, RESEARCH_TEAM);
			addAdvancedExecutionEdges(graph, executorNodes);
			return graph.compile(CompileConfig.builder().build());
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to build advanced research graph", ex);
		}
	}

	private CompiledGraph buildAdvancedPlanGateResearchGraph(SessionContextService sessionContextService) {
		try {
			StateGraph graph = newGraph("research-plan-gate-advanced");
			Map<String, ResearchNode> executorNodes = executorNodes();
			addRequiredNodes(graph, sessionContextService);
			addHumanFeedbackNode(graph);
			addExecutorNodes(graph, executorNodes);
			graph.addEdge(StateGraph.START, COORDINATOR);
			graph.addConditionalEdges(COORDINATOR, edge(this::routeCoordinator),
					Map.of("direct_answer", StateGraph.END, "deep_research", REWRITE_MULTI_QUERY));
			addRagConditionalEdge(graph, REWRITE_MULTI_QUERY, BACKGROUND_INVESTIGATOR);
			graph.addEdge(BACKGROUND_INVESTIGATOR, PLANNER);
			graph.addEdge(PLANNER, PLAN_VALIDATOR);
			graph.addConditionalEdges(PLAN_VALIDATOR, edge(this::routePlanValidatorWithHumanFeedback),
					Map.of("planner", PLANNER, "research_team", INFORMATION, "human_feedback", HUMAN_FEEDBACK));
			graph.addEdge(HUMAN_FEEDBACK, StateGraph.END);
			graph.addEdge(INFORMATION, RESEARCH_TEAM);
			addAdvancedExecutionEdges(graph, executorNodes);
			return graph.compile(CompileConfig.builder().build());
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to build advanced plan gate research graph", ex);
		}
	}

	private CompiledGraph buildAdvancedResumeGraph(SessionContextService sessionContextService) {
		try {
			StateGraph graph = newGraph("research-resume-advanced");
			Map<String, ResearchNode> executorNodes = executorNodes();
			addHumanFeedbackNode(graph);
			graph.addNode(PLANNER, plannerAction(requiredNode(PLANNER), sessionContextService));
			graph.addNode(PLAN_VALIDATOR, ResearchNodeGraphAction.async(requiredNode(PLAN_VALIDATOR)));
			graph.addNode(INFORMATION, ResearchNodeGraphAction.async(requiredNode(INFORMATION)));
			graph.addNode(RESEARCH_TEAM, ResearchNodeGraphAction.async(requiredNode(RESEARCH_TEAM)));
			graph.addNode(PARALLEL_EXECUTOR, ResearchNodeGraphAction.async(requiredNode(PARALLEL_EXECUTOR)));
			addExecutorNodes(graph, executorNodes);
			addProfessionalKbNodes(graph);
			graph.addNode(REPORTER, ResearchNodeGraphAction.async(requiredNode(REPORTER)));
			graph.addEdge(StateGraph.START, HUMAN_FEEDBACK);
			graph.addConditionalEdges(HUMAN_FEEDBACK, edge(this::routeHumanFeedback),
					Map.of("planner", PLANNER, "research_team", INFORMATION, "waiting", StateGraph.END));
			graph.addEdge(PLANNER, PLAN_VALIDATOR);
			graph.addConditionalEdges(PLAN_VALIDATOR, edge(this::routePlanValidatorWithHumanFeedback),
					Map.of("planner", PLANNER, "research_team", INFORMATION, "human_feedback", HUMAN_FEEDBACK));
			graph.addEdge(INFORMATION, RESEARCH_TEAM);
			addAdvancedExecutionEdges(graph, executorNodes);
			return graph.compile(CompileConfig.builder().build());
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to build advanced resume research graph", ex);
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
		graph.addNode(PARALLEL_EXECUTOR, ResearchNodeGraphAction.async(requiredNode(PARALLEL_EXECUTOR)));
		graph.addNode(REPORTER, ResearchNodeGraphAction.async(requiredNode(REPORTER)));
		if (ragProperties.isEnabled() && nodes.containsKey(USER_FILE_RAG)) {
			graph.addNode(USER_FILE_RAG, ResearchNodeGraphAction.async(requiredNode(USER_FILE_RAG)));
		}
		addProfessionalKbNodes(graph);
	}

	private void addProfessionalKbNodes(StateGraph graph) throws Exception {
		if (ragProperties.isEnabled() && nodes.containsKey(PROFESSIONAL_KB_DECISION)) {
			graph.addNode(PROFESSIONAL_KB_DECISION,
					ResearchNodeGraphAction.async(requiredNode(PROFESSIONAL_KB_DECISION)));
		}
		if (ragProperties.isEnabled() && nodes.containsKey(PROFESSIONAL_KB_RAG)) {
			graph.addNode(PROFESSIONAL_KB_RAG,
					ResearchNodeGraphAction.async(requiredNode(PROFESSIONAL_KB_RAG)));
		}
	}

	private void addExecutorNodes(StateGraph graph, Map<String, ResearchNode> executorNodes) throws Exception {
		for (ResearchNode node : executorNodes.values()) {
			graph.addNode(node.name(), ResearchNodeGraphAction.async(node));
		}
	}

	private void addAdvancedExecutionEdges(StateGraph graph, Map<String, ResearchNode> executorNodes) throws Exception {
		if (ragProperties.isEnabled() && nodes.containsKey(PROFESSIONAL_KB_DECISION)) {
			graph.addConditionalEdges(RESEARCH_TEAM, edge(this::routeResearchTeam),
					Map.of("parallel_executor", PARALLEL_EXECUTOR,
							"reporter", PROFESSIONAL_KB_DECISION));
			graph.addConditionalEdges(PROFESSIONAL_KB_DECISION, edge(this::routeProfessionalKbDecision),
					Map.of("professional_kb_rag", PROFESSIONAL_KB_RAG,
							"reporter", REPORTER));
			if (nodes.containsKey(PROFESSIONAL_KB_RAG)) {
				graph.addEdge(PROFESSIONAL_KB_RAG, REPORTER);
			}
		} else {
			graph.addConditionalEdges(RESEARCH_TEAM, edge(this::routeResearchTeam),
					Map.of("parallel_executor", PARALLEL_EXECUTOR, "reporter", REPORTER));
		}
		graph.addConditionalEdges(PARALLEL_EXECUTOR, edge(this::routeParallelExecutor),
				executorRouteTargets(executorNodes));
		for (ResearchNode node : executorNodes.values()) {
			graph.addEdge(node.name(), RESEARCH_TEAM);
		}
		graph.addEdge(REPORTER, StateGraph.END);
	}

	private Map<String, String> executorRouteTargets(Map<String, ResearchNode> executorNodes) {
		LinkedHashMap<String, String> targets = new LinkedHashMap<>();
		targets.put("research_team", RESEARCH_TEAM);
		for (String nodeName : executorNodes.keySet()) {
			targets.put(nodeName, nodeName);
		}
		return targets;
	}

	private Map<String, ResearchNode> executorNodes() {
		LinkedHashMap<String, ResearchNode> executorNodes = new LinkedHashMap<>();
		for (int index = 0; index < advancedExecutionProperties.getParallelNodeCount().getResearcher(); index++) {
			String nodeName = "researcher_" + index;
			ResearchNode node = nodes.get(nodeName);
			executorNodes.put(nodeName, node == null ? researcherNode(String.valueOf(index)) : node);
		}
		for (int index = 0; index < advancedExecutionProperties.getParallelNodeCount().getCoder(); index++) {
			String nodeName = "coder_" + index;
			ResearchNode node = nodes.get(nodeName);
			executorNodes.put(nodeName, node == null ? coderNode(String.valueOf(index)) : node);
		}
		return executorNodes;
	}

	private ResearchNode researcherNode(String executorId) {
		if (researcherAgent == null) {
			throw new IllegalStateException("Missing researcher agent for advanced execution node researcher_"
					+ executorId);
		}
		return new ResearcherNode(researcherAgent, executorId);
	}

	private ResearchNode coderNode(String executorId) {
		if (processorAgent == null) {
			throw new IllegalStateException("Missing processor agent for advanced execution node coder_" + executorId);
		}
		return new CoderNode(processorAgent, executorId);
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

	private void addRagConditionalEdge(StateGraph graph, String from, String defaultTo) throws Exception {
		if (ragProperties.isEnabled() && nodes.containsKey(USER_FILE_RAG)) {
			graph.addEdge(from, USER_FILE_RAG);
			graph.addEdge(USER_FILE_RAG, defaultTo);
		} else {
			graph.addEdge(from, defaultTo);
		}
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

	private String routeHumanFeedback(com.alibaba.cloud.ai.graph.OverAllState state) {
		HumanFeedbackRoute route = ResearchGraphState.humanFeedbackRoute(state.data())
			.orElseThrow(() -> new IllegalStateException("Human feedback did not produce a route"));
		return switch (route) {
			case RESEARCH_TEAM -> "research_team";
			case PLANNER -> "planner";
			case WAITING -> "waiting";
		};
	}

	private String routeResearchTeam(com.alibaba.cloud.ai.graph.OverAllState state) {
		ResearchTeamRoute route = ResearchGraphState.researchTeamRoute(state.data())
			.orElseThrow(() -> new IllegalStateException("Research team did not produce a route"));
		return switch (route) {
			case PARALLEL_EXECUTOR -> "parallel_executor";
			case REPORTER -> "reporter";
		};
	}

	private String routeProfessionalKbDecision(com.alibaba.cloud.ai.graph.OverAllState state) {
		ResearchState researchState = ResearchGraphState.researchState(state.data());
		List<String> selectedKbs = researchState.selectedKnowledgeBases();
		if (selectedKbs != null && !selectedKbs.isEmpty()) {
			return "professional_kb_rag";
		}
		return "reporter";
	}

	private String routeParallelExecutor(com.alibaba.cloud.ai.graph.OverAllState state) {
		ResearchState researchState = ResearchGraphState.researchState(state.data());
		return nextExecutorNode(researchState).orElseThrow(
				() -> new IllegalStateException("Parallel executor did not assign a configured executor node"));
	}

	private java.util.Optional<String> nextExecutorNode(ResearchState state) {
		if (state.plan() == null || state.plan().steps() == null) {
			return java.util.Optional.empty();
		}
		return nextExecutorNode(state, StepType.RESEARCH).or(() -> nextExecutorNode(state, StepType.PROCESSING));
	}

	private java.util.Optional<String> nextExecutorNode(ResearchState state, StepType stepType) {
		return state.plan()
			.steps()
			.stream()
			.filter(step -> stepType.equals(step.stepType()))
			.filter(step -> !StepExecutionStatus.isTerminal(step))
			.map(this::assignedExecutorNode)
			.filter(java.util.Optional::isPresent)
			.map(java.util.Optional::get)
			.filter(this::isConfiguredExecutorNode)
			.findFirst();
	}

	private java.util.Optional<String> assignedExecutorNode(ResearchStep step) {
		if (step.assignedNode() != null && !step.assignedNode().isBlank()) {
			return java.util.Optional.of(step.assignedNode().strip());
		}
		String status = StepExecutionStatus.normalize(step.executionStatus());
		String assignedPrefix = "assigned_";
		if (status.startsWith(assignedPrefix)) {
			return java.util.Optional.of(status.substring(assignedPrefix.length()));
		}
		String processingPrefix = "processing_";
		if (status.startsWith(processingPrefix)) {
			return java.util.Optional.of(status.substring(processingPrefix.length()));
		}
		return java.util.Optional.empty();
	}

	private boolean isConfiguredExecutorNode(String nodeName) {
		return isConfiguredNode(nodeName, "researcher", advancedExecutionProperties.getParallelNodeCount().getResearcher())
				|| isConfiguredNode(nodeName, "coder", advancedExecutionProperties.getParallelNodeCount().getCoder());
	}

	private boolean isConfiguredNode(String nodeName, String nodeType, int nodeCount) {
		for (int index = 0; index < nodeCount; index++) {
			if ((nodeType + "_" + index).equals(nodeName)) {
				return true;
			}
		}
		return false;
	}

}
