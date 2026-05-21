package top.lanshan.manmu.model;

public record ResearchTeamDecision(ResearchTeamRoute nextRoute,
		StepType nextStepType,
		int totalSteps,
		int completedSteps,
		int errorSteps,
		int remainingSteps) {
}
