package top.lanshan.manmu.agent;

import top.lanshan.manmu.model.ResearchPlan;

import java.util.List;

public interface PlannerAgent {

	ResearchPlan plan(String query, int maxSteps);

	default ResearchPlan plan(String query, int maxSteps, String feedbackContent) {
		return plan(query, maxSteps);
	}

	default ResearchPlan plan(String query, int maxSteps, String feedbackContent, String backgroundContext) {
		return plan(query, maxSteps, feedbackContent);
	}

	default ResearchPlan plan(String query, int maxSteps, String feedbackContent, String backgroundContext,
			List<String> optimizedQueries) {
		return plan(query, maxSteps, feedbackContent, backgroundContext);
	}

}
