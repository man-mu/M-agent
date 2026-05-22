package top.lanshan.manmu.agent;

import org.springframework.stereotype.Component;
import top.lanshan.manmu.model.CoordinatorDecision;
import top.lanshan.manmu.model.CoordinatorRoute;

@Component
public class CoordinatorOutputMapper {

	public CoordinatorDecision toDecision(CoordinatorResponse response, String query, boolean deepResearchEnabled) {
		if (response == null) {
			throw new IllegalArgumentException("Coordinator response is empty");
		}

		CoordinatorRoute route = response.nextRoute();
		if (!deepResearchEnabled) {
			route = CoordinatorRoute.DIRECT_ANSWER;
		}
		else if (isSubstantiveResearchRequest(query)) {
			route = CoordinatorRoute.DEEP_RESEARCH;
		}
		if (route == null) {
			route = CoordinatorRoute.DEEP_RESEARCH;
		}

		String answer = normalize(response.directAnswer());
		if (CoordinatorRoute.DIRECT_ANSWER.equals(route) && answer.isBlank()) {
			throw new IllegalArgumentException("Coordinator direct answer is empty");
		}

		return new CoordinatorDecision(route, CoordinatorRoute.DEEP_RESEARCH.equals(route),
				CoordinatorRoute.DIRECT_ANSWER.equals(route) ? answer : null, normalize(response.thought()));
	}

	private String normalize(String text) {
		return text == null ? "" : text.strip();
	}

	public boolean isSubstantiveResearchRequest(String query) {
		if (query == null || query.isBlank()) {
			return false;
		}
		String normalized = query.toLowerCase();
		return containsAny(normalized, "explain", "compare", "analyze", "analyse", "investigate", "research",
				"summarize", "summarise", "report", "implementation", "workflow", "why", "how",
				"解释", "比较", "分析", "调研", "研究", "总结", "报告", "实现", "工作流", "为什么", "如何");
	}

	private boolean containsAny(String text, String... candidates) {
		for (String candidate : candidates) {
			if (text.contains(candidate)) {
				return true;
			}
		}
		return false;
	}

}
