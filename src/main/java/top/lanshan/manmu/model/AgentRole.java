package top.lanshan.manmu.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AgentRole {

	@JsonProperty("planner") PLANNER,
	@JsonProperty("executor") EXECUTOR,
	@JsonProperty("reviewer") REVIEWER;

}
