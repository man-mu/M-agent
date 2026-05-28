package top.lanshan.manmu.node;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import top.lanshan.manmu.config.AdvancedExecutionProperties;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.StepExecutionStatus;
import top.lanshan.manmu.model.StepType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ParallelExecutorNode implements ResearchNode {

	private static final String RESEARCHER_NODE_TYPE = "researcher";

	private static final String CODER_NODE_TYPE = "coder";

	private final AdvancedExecutionProperties properties;

	public ParallelExecutorNode(AdvancedExecutionProperties properties) {
		this.properties = properties == null ? new AdvancedExecutionProperties() : properties;
	}

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
		return Flux.defer(() -> {
			ResearchPlan plan = state.plan();
			if (plan == null) {
				return Flux.error(new IllegalStateException("Research plan is missing"));
			}
			if (plan.steps() == null || plan.steps().isEmpty()) {
				return Flux.error(new IllegalStateException("Research plan has no steps"));
			}

			List<ResearchStep> steps = plan.steps();
			List<ResearchEvent> events = hasNonTerminalResearchSteps(steps)
					? assignSteps(state, steps, StepType.RESEARCH, RESEARCHER_NODE_TYPE, researcherNodeCount())
					: assignSteps(state, steps, StepType.PROCESSING, CODER_NODE_TYPE, coderNodeCount());
			return Flux.fromIterable(events);
		});
	}

	private List<ResearchEvent> assignSteps(ResearchState state, List<ResearchStep> steps, StepType stepType,
			String nodeType, int nodeCount) {
		LinkedHashMap<String, Integer> nodeLoads = nodeLoads(steps, nodeType, nodeCount);
		List<ResearchEvent> events = new ArrayList<>();
		for (ResearchStep step : steps) {
			if (!isAssignable(step, stepType)) {
				continue;
			}
			String nodeName = nextNodeName(nodeLoads);
			assignStep(state, step, nodeName);
			nodeLoads.computeIfPresent(nodeName, (key, load) -> load + 1);
			events.add(assignmentEvent(state, step, nodeName));
		}
		return events;
	}

	private void assignStep(ResearchState state, ResearchStep step, String nodeName) {
		step.assignedNode(nodeName);
		step.incrementAttempt();
		step.completedAt(null);
		step.error(null);
		step.executionStatus(StepExecutionStatus.assigned(nodeName));
		state.recordNodeStarted(nodeName, step);
	}

	private ResearchEvent assignmentEvent(ResearchState state, ResearchStep step, String nodeName) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("step_id", step.id());
		payload.put("assigned_node", nodeName);
		payload.put("step_type", step.stepType());
		payload.put("execution_status", step.executionStatus());
		payload.put("status", step.executionStatus());
		payload.put("step", step.copy());
		return new ResearchEvent(state.threadId(), null, null, name(), name(), null, null, step.id(),
				"step.assigned", step.executionStatus(), null, "\u5df2\u5b89\u6392\uff1a" + step.title(), payload, null, false,
				Instant.now());
	}

	private boolean hasNonTerminalResearchSteps(List<ResearchStep> steps) {
		return steps.stream()
			.filter(step -> stepTypeEquals(step, StepType.RESEARCH))
			.anyMatch(step -> !StepExecutionStatus.isTerminal(step));
	}

	private boolean isAssignable(ResearchStep step, StepType stepType) {
		if (step == null || !stepTypeEquals(step, stepType)) {
			return false;
		}
		if (StepExecutionStatus.isTerminal(step) || StepExecutionStatus.isRunning(step.executionStatus())) {
			return false;
		}
		return step.assignedNode() == null || step.assignedNode().isBlank();
	}

	private boolean stepTypeEquals(ResearchStep step, StepType expected) {
		if (step == null) {
			return false;
		}
		StepType actual = step.stepType() == null ? StepType.RESEARCH : step.stepType();
		if (StepType.PROCESSING.equals(expected)) {
			return StepType.PROCESSING.equals(actual);
		}
		return expected.equals(actual);
	}

	private LinkedHashMap<String, Integer> nodeLoads(List<ResearchStep> steps, String nodeType, int nodeCount) {
		LinkedHashMap<String, Integer> loads = new LinkedHashMap<>();
		for (int index = 0; index < nodeCount; index++) {
			loads.put(nodeType + "_" + index, 0);
		}
		for (ResearchStep step : steps) {
			String nodeName = activeNodeName(step, nodeType);
			if (nodeName != null && loads.containsKey(nodeName)) {
				loads.computeIfPresent(nodeName, (key, load) -> load + 1);
			}
		}
		return loads;
	}

	private String activeNodeName(ResearchStep step, String nodeType) {
		if (step == null || StepExecutionStatus.isTerminal(step)) {
			return null;
		}
		String assignedNode = step.assignedNode();
		if (assignedNode != null && assignedNode.startsWith(nodeType + "_")) {
			return assignedNode;
		}
		String status = StepExecutionStatus.normalize(step.executionStatus());
		String assignedPrefix = "assigned_";
		String processingPrefix = "processing_";
		if (status.startsWith(assignedPrefix)) {
			String nodeName = status.substring(assignedPrefix.length());
			return nodeName.startsWith(nodeType + "_") ? nodeName : null;
		}
		if (status.startsWith(processingPrefix)) {
			String nodeName = status.substring(processingPrefix.length());
			return nodeName.startsWith(nodeType + "_") ? nodeName : null;
		}
		return null;
	}

	private String nextNodeName(LinkedHashMap<String, Integer> nodeLoads) {
		return nodeLoads.entrySet()
			.stream()
			.min(Comparator.comparingInt(Map.Entry<String, Integer>::getValue))
			.map(Map.Entry::getKey)
			.orElseThrow(() -> new IllegalStateException("No executor nodes are configured"));
	}

	private int researcherNodeCount() {
		return properties.getParallelNodeCount().getResearcher();
	}

	private int coderNodeCount() {
		return properties.getParallelNodeCount().getCoder();
	}

}
