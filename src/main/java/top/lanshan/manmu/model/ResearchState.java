package top.lanshan.manmu.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ResearchState {

	private final String threadId;

	private final String sessionId;

	private final String query;

	private final int maxSteps;

	private final int optimizeQueryNum;

	private ResearchPlan plan;

	private String planFeedback;

	private String backgroundContext;

	private String backgroundInvestigationContext;

	private List<String> optimizedQueries = List.of();

	private boolean queryRewriteCompleted;

	private boolean backgroundInvestigationCompleted;

	private ResearchTeamDecision researchTeamDecision;

	private final List<String> observations = new ArrayList<>();

	private final List<StepSearchContext> searchContexts = new ArrayList<>();

	private List<BackgroundInvestigationSearchResult> backgroundInvestigationResults = List.of();

	private final List<SiteInformation> siteInformation = new ArrayList<>();

	private String report;

	private ResearchState(String threadId, String sessionId, String query, int maxSteps, int optimizeQueryNum) {
		this.threadId = threadId;
		this.sessionId = sessionId;
		this.query = query;
		this.maxSteps = maxSteps;
		this.optimizeQueryNum = optimizeQueryNum;
	}

	public static ResearchState from(ResearchRequest request) {
		return from(request, request.threadId());
	}

	public static ResearchState from(ResearchRequest request, String sessionId) {
		if (sessionId == null || sessionId.isBlank()) {
			sessionId = request.threadId();
		}
		return new ResearchState(request.threadId(), sessionId, request.query(), request.maxSteps(),
				request.optimizeQueryNum());
	}

	public String threadId() {
		return threadId;
	}

	public String sessionId() {
		return sessionId;
	}

	public String query() {
		return query;
	}

	public int maxSteps() {
		return maxSteps;
	}

	public int optimizeQueryNum() {
		return optimizeQueryNum;
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

	public String backgroundContext() {
		return backgroundContext;
	}

	public void backgroundContext(String backgroundContext) {
		this.backgroundContext = backgroundContext;
	}

	public String backgroundInvestigationContext() {
		return backgroundInvestigationContext;
	}

	public void backgroundInvestigationContext(String backgroundInvestigationContext) {
		this.backgroundInvestigationContext = backgroundInvestigationContext;
	}

	public List<String> optimizedQueries() {
		return optimizedQueries;
	}

	public void optimizedQueries(List<String> optimizedQueries) {
		this.optimizedQueries = optimizedQueries == null ? List.of() : List.copyOf(optimizedQueries);
	}

	public boolean queryRewriteCompleted() {
		return queryRewriteCompleted;
	}

	public void queryRewriteCompleted(boolean queryRewriteCompleted) {
		this.queryRewriteCompleted = queryRewriteCompleted;
	}

	public boolean backgroundInvestigationCompleted() {
		return backgroundInvestigationCompleted;
	}

	public void backgroundInvestigationCompleted(boolean backgroundInvestigationCompleted) {
		this.backgroundInvestigationCompleted = backgroundInvestigationCompleted;
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

	public List<BackgroundInvestigationSearchResult> backgroundInvestigationResults() {
		return backgroundInvestigationResults;
	}

	public void backgroundInvestigationResults(List<BackgroundInvestigationSearchResult> backgroundInvestigationResults) {
		this.backgroundInvestigationResults =
				backgroundInvestigationResults == null ? List.of() : List.copyOf(backgroundInvestigationResults);
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
