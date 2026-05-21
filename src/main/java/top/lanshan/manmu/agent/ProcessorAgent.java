package top.lanshan.manmu.agent;

import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;

public interface ProcessorAgent {

	String process(ResearchState state, ResearchStep step);

}
