package top.lanshan.manmu.node;

import org.junit.jupiter.api.Test;
import top.lanshan.manmu.agent.PlannerAgent;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.StepType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerNodeTest {

	@Test
	void passesBackgroundContextToPlannerAgent() {
		RecordingPlannerAgent plannerAgent = new RecordingPlannerAgent();
		PlannerNode plannerNode = new PlannerNode(plannerAgent);
		ResearchState state = ResearchState.from(new ResearchRequest("Continue research.", "thread-1", 2),
				"session-1");
		state.planFeedback("Prefer implementation details.");
		state.backgroundContext("Previous report context");

		plannerNode.run(state).collectList().block();

		assertThat(plannerAgent.lastQuery).isEqualTo("Continue research.");
		assertThat(plannerAgent.lastMaxSteps).isEqualTo(2);
		assertThat(plannerAgent.lastFeedback).isEqualTo("Prefer implementation details.");
		assertThat(plannerAgent.lastBackgroundContext).isEqualTo("Previous report context");
		assertThat(state.plan()).isNotNull();
	}

	private static class RecordingPlannerAgent implements PlannerAgent {

		private String lastQuery;

		private int lastMaxSteps;

		private String lastFeedback;

		private String lastBackgroundContext;

		@Override
		public ResearchPlan plan(String query, int maxSteps) {
			return plan(query, maxSteps, null, null);
		}

		@Override
		public ResearchPlan plan(String query, int maxSteps, String feedbackContent, String backgroundContext) {
			lastQuery = query;
			lastMaxSteps = maxSteps;
			lastFeedback = feedbackContent;
			lastBackgroundContext = backgroundContext;
			return new ResearchPlan("Plan", true, "Use context.",
					List.of(new ResearchStep("Step", "Do work.", false, StepType.RESEARCH, null,
							ResearchStep.STATUS_PENDING)));
		}

	}

}
