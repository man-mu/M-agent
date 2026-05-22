package top.lanshan.manmu.node;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import top.lanshan.manmu.model.CoordinatorDecision;
import top.lanshan.manmu.model.CoordinatorRoute;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;

import static org.assertj.core.api.Assertions.assertThat;

class CoordinatorNodeTest {

	@Test
	void storesDeepResearchDecision() {
		CoordinatorNode node = new CoordinatorNode((query, deepResearchEnabled) -> new CoordinatorDecision(
				CoordinatorRoute.DEEP_RESEARCH, true, null, "Needs investigation."));
		ResearchState state = ResearchState.from(new ResearchRequest("Compare frameworks.", "thread-1", 2));

		StepVerifier.create(node.run(state)).assertNext(event -> {
			assertThat(event.node()).isEqualTo("coordinator");
			assertThat(event.phase()).isEqualTo("decision");
			assertThat(event.payload()).isInstanceOf(CoordinatorDecision.class);
		}).verifyComplete();

		assertThat(state.directAnswerRoute()).isFalse();
		assertThat(state.report()).isNull();
	}

	@Test
	void storesDirectAnswerAsReport() {
		CoordinatorNode node = new CoordinatorNode((query, deepResearchEnabled) -> new CoordinatorDecision(
				CoordinatorRoute.DIRECT_ANSWER, false, "A concise answer.", "Simple request."));
		ResearchState state = ResearchState.from(new ResearchRequest("Say hi.", "thread-1", 2, null, false));

		StepVerifier.create(node.run(state)).assertNext(event -> {
			CoordinatorDecision decision = (CoordinatorDecision) event.payload();
			assertThat(decision.nextRoute()).isEqualTo(CoordinatorRoute.DIRECT_ANSWER);
			assertThat(decision.deepResearch()).isFalse();
		}).verifyComplete();

		assertThat(state.directAnswerRoute()).isTrue();
		assertThat(state.report()).isEqualTo("A concise answer.");
	}

}
