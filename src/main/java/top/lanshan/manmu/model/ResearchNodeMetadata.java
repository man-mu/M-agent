package top.lanshan.manmu.model;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ResearchNodeMetadata(String nodeName, String nodeType, Integer executorId, String displayTitle) {

	private static final Pattern EXECUTOR_NODE = Pattern.compile("^(researcher|coder)_(\\d+)$");

	private static final Map<String, ResearchNodeMetadata> KNOWN_NODES = Map.ofEntries(
			Map.entry("__START__", metadata("__START__", "graph", "\u5f00\u59cb")),
			Map.entry("__END__", metadata("__END__", "graph", "\u7ed3\u675f")),
			Map.entry("runner", metadata("runner", "graph", "\u8fd0\u884c\u5f02\u5e38")),
			Map.entry("rewrite_multi_query", metadata("rewrite_multi_query", "query_rewrite", "Query Rewrite")),
			Map.entry("coordinator", metadata("coordinator", "coordinator", "Coordinator")),
			Map.entry("background_investigator",
					metadata("background_investigator", "background_investigator", "Background Investigation")),
			Map.entry("human_feedback", metadata("human_feedback", "human_feedback", "\u4eba\u5de5\u53cd\u9988")),
			Map.entry("planner", metadata("planner", "planner", "\u7814\u7a76\u8ba1\u5212")),
			Map.entry("plan_validator", metadata("plan_validator", "plan_validator", "Plan Validator")),
			Map.entry("information", metadata("information", "information", "\u4fe1\u606f\u68c0\u7d22")),
			Map.entry("research_team", metadata("research_team", "research_team", "\u7814\u7a76\u56e2\u961f")),
			Map.entry("parallel_executor", metadata("parallel_executor", "parallel_executor", "Parallel Executor")),
			Map.entry("researcher", metadata("researcher", "researcher", "\u7814\u7a76\u6267\u884c")),
			Map.entry("processor", metadata("processor", "processor", "\u4fe1\u606f\u6574\u7406")),
			Map.entry("coder", metadata("coder", "coder", "Coder")),
			Map.entry("reporter", metadata("reporter", "reporter", "\u62a5\u544a\u751f\u6210")));

	public static ResearchNodeMetadata from(String nodeName) {
		String normalizedNodeName = nodeName == null || nodeName.isBlank() ? "unknown" : nodeName;
		ResearchNodeMetadata known = KNOWN_NODES.get(normalizedNodeName);
		if (known != null) {
			return known;
		}
		Matcher matcher = EXECUTOR_NODE.matcher(normalizedNodeName);
		if (matcher.matches()) {
			String nodeType = matcher.group(1);
			Integer executorId = Integer.valueOf(matcher.group(2));
			String displayTitle = displayTitle(nodeType, executorId);
			return new ResearchNodeMetadata(normalizedNodeName, nodeType, executorId, displayTitle);
		}
		return new ResearchNodeMetadata(normalizedNodeName, normalizedNodeName, null, normalizedNodeName);
	}

	private static ResearchNodeMetadata metadata(String nodeName, String nodeType, String displayTitle) {
		return new ResearchNodeMetadata(nodeName, nodeType, null, displayTitle);
	}

	private static String displayTitle(String nodeType, Integer executorId) {
		if ("researcher".equals(nodeType)) {
			return "\u7814\u7a76\u6267\u884c " + executorId;
		}
		if ("coder".equals(nodeType)) {
			return "Coder " + executorId;
		}
		return nodeType + " " + executorId;
	}

}
