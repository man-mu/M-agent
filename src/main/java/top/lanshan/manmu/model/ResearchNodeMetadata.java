package top.lanshan.manmu.model;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ResearchNodeMetadata(String nodeName, String nodeType, Integer executorId, String displayTitle) {

	private static final Pattern EXECUTOR_NODE = Pattern.compile("^(researcher|coder)_(\\d+)$");

	private static final Map<String, ResearchNodeMetadata> KNOWN_NODES = Map.ofEntries(
			Map.entry("__START__", metadata("__START__", "graph", "\u5f00\u59cb")),
			Map.entry("__END__", metadata("__END__", "graph", "\u5b8c\u6210")),
			Map.entry("runner", metadata("runner", "graph", "\u8fd0\u884c\u5f02\u5e38")),
			Map.entry("rewrite_multi_query", metadata("rewrite_multi_query", "query_rewrite", "\u4f18\u5316\u95ee\u9898")),
			Map.entry("coordinator", metadata("coordinator", "coordinator", "\u7406\u89e3\u9700\u6c42")),
			Map.entry("background_investigator",
					metadata("background_investigator", "background_investigator", "\u80cc\u666f\u68c0\u7d22")),
			Map.entry("user_file_rag", metadata("user_file_rag", "user_file_rag", "\u8bfb\u53d6\u4e0a\u4f20\u8d44\u6599")),
			Map.entry("professional_kb_decision",
					metadata("professional_kb_decision", "professional_kb_decision", "\u5339\u914d\u4e13\u4e1a\u77e5\u8bc6")),
			Map.entry("professional_kb_rag",
					metadata("professional_kb_rag", "professional_kb_rag", "\u8865\u5145\u4e13\u4e1a\u8d44\u6599")),
			Map.entry("human_feedback", metadata("human_feedback", "human_feedback", "\u786e\u8ba4\u8ba1\u5212")),
			Map.entry("planner", metadata("planner", "planner", "\u5236\u5b9a\u7814\u7a76\u8ba1\u5212")),
			Map.entry("plan_validator", metadata("plan_validator", "plan_validator", "\u68c0\u67e5\u8ba1\u5212")),
			Map.entry("information", metadata("information", "information", "\u4fe1\u606f\u68c0\u7d22")),
			Map.entry("research_team", metadata("research_team", "research_team", "\u5b89\u6392\u7814\u7a76\u6b65\u9aa4")),
			Map.entry("parallel_executor", metadata("parallel_executor", "parallel_executor", "\u4efb\u52a1\u5206\u914d")),
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
			return "\u8d44\u6599\u5206\u6790";
		}
		if ("coder".equals(nodeType)) {
			return "\u5185\u5bb9\u6574\u7406";
		}
		return nodeType + " " + executorId;
	}

}
