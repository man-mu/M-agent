package top.lanshan.manmu.agent;

import top.lanshan.manmu.model.ResearchPlan;

public interface PlannerAgent {

	ResearchPlan plan(String query, int maxSteps);

	default ResearchPlan plan(String query, int maxSteps, String feedbackContent) {
		return plan(query, maxSteps);
	}

}
