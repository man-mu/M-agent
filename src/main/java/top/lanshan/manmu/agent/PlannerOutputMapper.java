package top.lanshan.manmu.agent;

import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.StepType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PlannerOutputMapper {

	public ResearchPlan toResearchPlan(PlannerResponse response, int maxSteps) {
		return toResearchPlan(response, null, maxSteps);
	}

	public ResearchPlan toResearchPlan(PlannerResponse response, String query, int maxSteps) {
		if (response == null) {
			throw new IllegalArgumentException("Planner response is empty");
		}
		if (response.steps() == null || response.steps().isEmpty()) {
			throw new IllegalArgumentException("Planner response steps are empty");
		}

		int limit = Math.max(1, Math.min(maxSteps, response.steps().size()));
		List<ResearchStep> steps = new ArrayList<>(response.steps()
			.stream()
			.limit(limit)
			.map(this::toResearchStep)
			.toList());
		normalizeStepTypes(steps, maxSteps);

		boolean hasEnoughContext = response.hasEnoughContext() == null || response.hasEnoughContext();
		String thought = response.thought() == null ? "" : response.thought();
		String title = normalizeTitle(response.title(), query);
		return new ResearchPlan(title, hasEnoughContext, thought, steps);
	}

	private void normalizeStepTypes(List<ResearchStep> steps, int maxSteps) {
		if (maxSteps > 1 && steps.size() == 1) {
			ResearchStep sourceStep = steps.get(0);
			sourceStep.stepType(StepType.RESEARCH);
			steps.add(new ResearchStep("Synthesize findings",
					"Organize the collected context into a concise result for the final report.", false,
					StepType.PROCESSING, null, ResearchStep.STATUS_PENDING));
		}
		if (steps.size() < 2) {
			return;
		}
		steps.get(steps.size() - 1).stepType(StepType.PROCESSING);
		if (steps.stream().limit(steps.size() - 1).noneMatch(step -> StepType.RESEARCH.equals(step.stepType()))) {
			steps.get(0).stepType(StepType.RESEARCH);
		}
	}

	private String normalizeTitle(String title, String query) {
		if (title != null && !title.isBlank()) {
			return title;
		}
		if (query != null && !query.isBlank()) {
			return "Research plan for: " + query.strip();
		}
		return "Research plan";
	}

	private ResearchStep toResearchStep(PlannerResponse.Step step) {
		if (step.title() == null || step.title().isBlank()) {
			throw new IllegalArgumentException("Planner step title is empty");
		}
		if (step.description() == null || step.description().isBlank()) {
			throw new IllegalArgumentException("Planner step description is empty");
		}
		StepType type = step.stepType() != null ? step.stepType() : step.type();
		if (type != null && "SYNTHESIS".equals(type.name())) {
			type = StepType.PROCESSING;
		}
		boolean needWebSearch = step.needWebSearch() != null && step.needWebSearch();
		return new ResearchStep(step.title(), step.description(), needWebSearch, type, null,
				ResearchStep.STATUS_PENDING);
	}

}
