package top.lanshan.manmu.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchNodeMetadataTest {

	@Test
	void plannerNodesHavePlannerRole() {
		assertThat(ResearchNodeMetadata.from("coordinator").agentRole()).isEqualTo(AgentRole.PLANNER);
		assertThat(ResearchNodeMetadata.from("planner").agentRole()).isEqualTo(AgentRole.PLANNER);
		assertThat(ResearchNodeMetadata.from("plan_validator").agentRole()).isEqualTo(AgentRole.PLANNER);
		assertThat(ResearchNodeMetadata.from("human_feedback").agentRole()).isEqualTo(AgentRole.PLANNER);
	}

	@Test
	void executorNodesHaveExecutorRole() {
		assertThat(ResearchNodeMetadata.from("research_team").agentRole()).isEqualTo(AgentRole.EXECUTOR);
		assertThat(ResearchNodeMetadata.from("parallel_executor").agentRole()).isEqualTo(AgentRole.EXECUTOR);
		assertThat(ResearchNodeMetadata.from("information").agentRole()).isEqualTo(AgentRole.EXECUTOR);
		assertThat(ResearchNodeMetadata.from("background_investigator").agentRole()).isEqualTo(AgentRole.EXECUTOR);
		assertThat(ResearchNodeMetadata.from("rewrite_multi_query").agentRole()).isEqualTo(AgentRole.EXECUTOR);
	}

	@Test
	void dynamicExecutorNodesHaveExecutorRole() {
		assertThat(ResearchNodeMetadata.from("researcher_0").agentRole()).isEqualTo(AgentRole.EXECUTOR);
		assertThat(ResearchNodeMetadata.from("researcher_1").agentRole()).isEqualTo(AgentRole.EXECUTOR);
		assertThat(ResearchNodeMetadata.from("coder_0").agentRole()).isEqualTo(AgentRole.EXECUTOR);
		assertThat(ResearchNodeMetadata.from("coder_1").agentRole()).isEqualTo(AgentRole.EXECUTOR);
	}

	@Test
	void reporterHasReviewerRole() {
		assertThat(ResearchNodeMetadata.from("reporter").agentRole()).isEqualTo(AgentRole.REVIEWER);
	}

	@Test
	void infrastructureNodesHaveNoRole() {
		assertThat(ResearchNodeMetadata.from("__START__").agentRole()).isNull();
		assertThat(ResearchNodeMetadata.from("__END__").agentRole()).isNull();
		assertThat(ResearchNodeMetadata.from("runner").agentRole()).isNull();
		assertThat(ResearchNodeMetadata.from("user_file_rag").agentRole()).isNull();
		assertThat(ResearchNodeMetadata.from("professional_kb_decision").agentRole()).isNull();
		assertThat(ResearchNodeMetadata.from("professional_kb_rag").agentRole()).isNull();
	}

	@Test
	void unknownNodeHasNoRole() {
		assertThat(ResearchNodeMetadata.from("unknown_node").agentRole()).isNull();
	}

	@Test
	void withSequenceFillsAgentRole() {
		ResearchEvent raw = ResearchEvent.message("thread-1", "planner", "started", "planning", null);
		ResearchEvent sequenced = raw.withSequence(1);
		assertThat(sequenced.agentRole()).isEqualTo(AgentRole.PLANNER);
	}

	@Test
	void withSequenceFillsExecutorRoleForDynamicNode() {
		ResearchEvent raw = ResearchEvent.message("thread-1", "researcher_0", "started", "researching", null);
		ResearchEvent sequenced = raw.withSequence(1);
		assertThat(sequenced.agentRole()).isEqualTo(AgentRole.EXECUTOR);
		assertThat(sequenced.executorId()).isEqualTo(0);
	}

	@Test
	void withSequenceFillsReviewerRole() {
		ResearchEvent raw = ResearchEvent.message("thread-1", "reporter", "started", "reporting", null);
		ResearchEvent sequenced = raw.withSequence(1);
		assertThat(sequenced.agentRole()).isEqualTo(AgentRole.REVIEWER);
	}

}
