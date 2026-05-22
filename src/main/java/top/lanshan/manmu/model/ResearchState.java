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

	private final boolean deepResearchEnabled;

	private boolean autoAcceptedPlan;

	private final int maxPlanIterations;

	private int planIterations;

	private String plannerError;

	private CoordinatorDecision coordinatorDecision;

	private PlanValidatorDecision planValidatorDecision;

	private Boolean humanFeedbackAccepted;

	private String humanFeedbackContent;

	private HumanFeedbackDecision humanFeedbackDecision;

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

	private ResearchState(String threadId, String sessionId, String query, int maxSteps, int optimizeQueryNum,
			boolean deepResearchEnabled, boolean autoAcceptedPlan, int maxPlanIterations) {
		this.threadId = threadId;
		this.sessionId = sessionId;
		this.query = query;
		this.maxSteps = maxSteps;
		this.optimizeQueryNum = optimizeQueryNum;
		this.deepResearchEnabled = deepResearchEnabled;
		this.autoAcceptedPlan = autoAcceptedPlan;
		this.maxPlanIterations = maxPlanIterations;
	}

	public static ResearchState from(ResearchRequest request) {
		return from(request, request.threadId());
	}

	public static ResearchState from(ResearchRequest request, String sessionId) {
		if (sessionId == null || sessionId.isBlank()) {
			sessionId = request.threadId();
		}
		return new ResearchState(request.threadId(), sessionId, request.query(), request.maxSteps(),
				request.optimizeQueryNum(), request.enableDeepResearch(), request.autoAcceptedPlan(),
				request.maxPlanIterations());
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

	public boolean deepResearchEnabled() {
		return deepResearchEnabled;
	}

	public boolean autoAcceptedPlan() {
		return autoAcceptedPlan;
	}

	public void autoAcceptedPlan(boolean autoAcceptedPlan) {
		this.autoAcceptedPlan = autoAcceptedPlan;
	}

	public int maxPlanIterations() {
		return maxPlanIterations;
	}

	public int planIterations() {
		return planIterations;
	}

	public void recordPlanAttempt() {
		this.planIterations++;
	}

	public String plannerError() {
		return plannerError;
	}

	public void plannerError(String plannerError) {
		this.plannerError = plannerError;
	}

	public CoordinatorDecision coordinatorDecision() {
		return coordinatorDecision;
	}

	public void coordinatorDecision(CoordinatorDecision coordinatorDecision) {
		this.coordinatorDecision = coordinatorDecision;
	}

	public boolean directAnswerRoute() {
		return coordinatorDecision != null && coordinatorDecision.directAnswerRoute();
	}

	public PlanValidatorDecision planValidatorDecision() {
		return planValidatorDecision;
	}

	public void planValidatorDecision(PlanValidatorDecision planValidatorDecision) {
		this.planValidatorDecision = planValidatorDecision;
	}

	public Boolean humanFeedbackAccepted() {
		return humanFeedbackAccepted;
	}

	public String humanFeedbackContent() {
		return humanFeedbackContent;
	}

	public void humanFeedback(Boolean accepted, String feedbackContent) {
		this.humanFeedbackAccepted = accepted;
		this.humanFeedbackContent = feedbackContent;
	}

	public void clearHumanFeedbackInput() {
		this.humanFeedbackAccepted = null;
		this.humanFeedbackContent = null;
	}

	public HumanFeedbackDecision humanFeedbackDecision() {
		return humanFeedbackDecision;
	}

	public void humanFeedbackDecision(HumanFeedbackDecision humanFeedbackDecision) {
		this.humanFeedbackDecision = humanFeedbackDecision;
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
