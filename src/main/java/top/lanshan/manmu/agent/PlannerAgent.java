package top.lanshan.manmu.agent;

import top.lanshan.manmu.model.ResearchPlan;

public interface PlannerAgent {

	ResearchPlan plan(String query, int maxSteps);

}
