package top.lanshan.manmu.model;

import java.util.ArrayList;
import java.util.List;

public class ResearchState {

	private final String threadId;

	private final String query;

	private final int maxSteps;

	private ResearchPlan plan;

	private final List<String> observations = new ArrayList<>();

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

	public List<String> observations() {
		return observations;
	}

	public void addObservation(String observation) {
		this.observations.add(observation);
	}

	public String report() {
		return report;
	}

	public void report(String report) {
		this.report = report;
	}

}
