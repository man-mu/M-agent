package top.lanshan.manmu.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ResearchState {

	private final String threadId;

	private final String query;

	private final int maxSteps;

	private ResearchPlan plan;

	private String planFeedback;

	private ResearchTeamDecision researchTeamDecision;

	private final List<String> observations = new ArrayList<>();

	private final List<StepSearchContext> searchContexts = new ArrayList<>();

	private final List<SiteInformation> siteInformation = new ArrayList<>();

	private String report;

	private ResearchState(String threadId, String query, int maxSteps) {
		this.threadId = threadId;
		this.query = query;
		this.maxSteps = maxSteps;
	}

	public static ResearchState from(ResearchRequest request) {
		return new ResearchState(request.threadId(), request.query(), request.maxSteps());
	}

	public String threadId() {
		return threadId;
	}

	public String query() {
		return query;
	}

	public int maxSteps() {
		return maxSteps;
	}

	public ResearchPlan plan() {
		return plan;
	}

	public void plan(ResearchPlan plan) {
		this.plan = plan;
	}

	public String planFeedback() {
		return planFeedback;
	}

	public void planFeedback(String planFeedback) {
		this.planFeedback = planFeedback;
	}

	public ResearchTeamDecision researchTeamDecision() {
		return researchTeamDecision;
	}

	public void researchTeamDecision(ResearchTeamDecision researchTeamDecision) {
		this.researchTeamDecision = researchTeamDecision;
	}

	public List<String> observations() {
		return observations;
	}

	public void addObservation(String observation) {
		this.observations.add(observation);
	}

	public List<StepSearchContext> searchContexts() {
		return searchContexts;
	}

	public void addSearchContext(StepSearchContext searchContext) {
		this.searchContexts.add(searchContext);
		for (SiteInformation site : searchContext.results()) {
			if (this.siteInformation.stream().noneMatch(existing -> sameSite(existing, site))) {
				this.siteInformation.add(site);
			}
		}
	}

	private boolean sameSite(SiteInformation first, SiteInformation second) {
		if (first.url() == null || first.url().isBlank() || second.url() == null || second.url().isBlank()) {
			return first.equals(second);
		}
		return first.url().equals(second.url());
	}

	public Optional<StepSearchContext> searchContextFor(ResearchStep step) {
		return searchContexts.stream()
			.filter(context -> step.title().equals(context.stepTitle()))
			.findFirst();
	}

	public List<SiteInformation> siteInformation() {
		return siteInformation;
	}

	public String report() {
		return report;
	}

	public void report(String report) {
		this.report = report;
	}

}
