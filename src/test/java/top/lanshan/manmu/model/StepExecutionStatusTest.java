package top.lanshan.manmu.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class StepExecutionStatusTest {

	@Test
	void recognizesDynamicAssignedAndProcessingStatusesAsNonTerminal() {
		assertThat(StepExecutionStatus.isTerminal(StepExecutionStatus.assigned("researcher_0"))).isFalse();
		assertThat(StepExecutionStatus.isTerminal(StepExecutionStatus.processing("researcher_0"))).isFalse();
	}

	@Test
	void checksDynamicAssignedOwnershipByNodeName() {
		assertThat(StepExecutionStatus.isAssignedTo(StepExecutionStatus.assigned("researcher_0"), "researcher_0"))
			.isTrue();
		assertThat(StepExecutionStatus.isAssignedTo(StepExecutionStatus.assigned("researcher_0"), "researcher_1"))
			.isFalse();
		assertThat(StepExecutionStatus.isAssignedTo(StepExecutionStatus.processing("researcher_0"), "researcher_0"))
			.isFalse();
	}

	@Test
	void recognizesDynamicCompletedAndErrorStatusesAsTerminal() {
		assertThat(StepExecutionStatus.isTerminal(StepExecutionStatus.completed("researcher_0"))).isTrue();
		assertThat(StepExecutionStatus.isCompleted(StepExecutionStatus.completed("researcher_0"))).isTrue();
		assertThat(StepExecutionStatus.isTerminal(StepExecutionStatus.error("researcher_0"))).isTrue();
		assertThat(StepExecutionStatus.isError(StepExecutionStatus.error("researcher_0"))).isTrue();
	}

	@Test
	void preservesLegacyCompletedAndErrorCompatibility() {
		assertThat(StepExecutionStatus.isTerminal(ResearchStep.STATUS_COMPLETED)).isTrue();
		assertThat(StepExecutionStatus.isCompleted(ResearchStep.STATUS_COMPLETED)).isTrue();
		assertThat(StepExecutionStatus.isTerminal("error: provider rejected request")).isTrue();
		assertThat(StepExecutionStatus.isError("error: provider rejected request")).isTrue();
	}

	@Test
	void serializesStepExecutionFieldsForEventPayloads() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
		ResearchStep step = new ResearchStep("Inspect workflow", "Read the current workflow.", true,
				StepType.RESEARCH, "Observation", StepExecutionStatus.completed("researcher_0"));
		step.id("step-1");
		step.assignedNode("researcher_0");
		step.attempt(1);
		step.startedAt(Instant.parse("2026-05-23T00:00:00Z"));
		step.completedAt(Instant.parse("2026-05-23T00:00:01Z"));

		String json = objectMapper.writeValueAsString(step);

		assertThat(json).contains("\"id\":\"step-1\"");
		assertThat(json).contains("\"assigned_node\":\"researcher_0\"");
		assertThat(json).contains("\"attempt\":1");
		assertThat(json).contains("\"started_at\":");
		assertThat(json).contains("\"completed_at\":");
		assertThat(json).contains("\"execution_status\":\"completed_researcher_0\"");
		assertThat(json).contains("\"execution_res\":\"Observation\"");
	}

}
