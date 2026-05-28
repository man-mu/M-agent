package top.lanshan.manmu.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

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

	private final Set<String> runningNodes = new LinkedHashSet<>();

	private final Set<String> completedNodes = new LinkedHashSet<>();

	private final Set<String> failedNodes = new LinkedHashSet<>();

	private final Map<String, String> lastAssignedNodes = new LinkedHashMap<>();
    private List<String> selectedKnowledgeBases = Collections.emptyList();

	private String report;

	private transient Consumer<ResearchEvent> liveEventConsumer;

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
		String sessionId = request.sessionId();
		if (sessionId == null || sessionId.isBlank()) {
			sessionId = request.threadId();
		}
		return from(request, sessionId);
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

	public void liveEventConsumer(Consumer<ResearchEvent> liveEventConsumer) {
		this.liveEventConsumer = liveEventConsumer;
	}

	public void emitLiveEvent(ResearchEvent event) {
		if (liveEventConsumer != null) {
			liveEventConsumer.accept(event);
		}
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

	public Set<String> runningNodes() {
		return runningNodes;
	}

	public Set<String> completedNodes() {
		return completedNodes;
	}

	public Set<String> failedNodes() {
		return failedNodes;
	}

	public Map<String, String> lastAssignedNodes() {
		return lastAssignedNodes;
	}

	public void recordNodeStarted(String nodeName, ResearchStep step) {
		if (nodeName == null || nodeName.isBlank()) {
			return;
		}
		String normalizedNodeName = nodeName.strip();
		runningNodes.add(normalizedNodeName);
		failedNodes.remove(normalizedNodeName);
		if (step != null && step.id() != null && !step.id().isBlank()) {
			lastAssignedNodes.put(normalizedNodeName, step.id());
		}
	}

	public void recordNodeCompleted(String nodeName) {
		if (nodeName == null || nodeName.isBlank()) {
			return;
		}
		String normalizedNodeName = nodeName.strip();
		runningNodes.remove(normalizedNodeName);
		completedNodes.add(normalizedNodeName);
	}

	public void recordNodeFailed(String nodeName) {
		if (nodeName == null || nodeName.isBlank()) {
			return;
		}
		String normalizedNodeName = nodeName.strip();
		runningNodes.remove(normalizedNodeName);
		failedNodes.add(normalizedNodeName);
	}

    public List<String> selectedKnowledgeBases() { return selectedKnowledgeBases; }
    public void selectedKnowledgeBases(List<String> selectedKnowledgeBases) {
        this.selectedKnowledgeBases = selectedKnowledgeBases == null ? Collections.emptyList() : selectedKnowledgeBases;
    }

	public String report() {
		return report;
	}

	public void report(String report) {
		this.report = report;
	}

}
