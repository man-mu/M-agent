package top.lanshan.manmu.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum StepType {

	@JsonProperty("research")
	@JsonAlias("RESEARCH")
	RESEARCH,

	@JsonProperty("processing")
	@JsonAlias({ "PROCESSING", "SYNTHESIS" })
	PROCESSING,

	@Deprecated
	@JsonProperty("synthesis")
	SYNTHESIS

}
