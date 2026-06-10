package top.lanshan.manmu.agent;

import top.lanshan.manmu.model.CoordinatorDecision;

public interface CoordinatorAgent {

	CoordinatorDecision coordinate(String query, boolean deepResearchEnabled, String userProfileContext);

	default CoordinatorDecision coordinate(String query, boolean deepResearchEnabled, String userProfileContext,
			String conversationHistoryContext) {
		return coordinate(query, deepResearchEnabled, userProfileContext);
	}

}
