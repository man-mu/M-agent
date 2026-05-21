package top.lanshan.manmu.agent;

import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.StepSearchContext;

public interface ResearcherAgent {

	String research(String query, ResearchStep step, StepSearchContext searchContext);

}
