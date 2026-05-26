package top.lanshan.manmu.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import top.lanshan.manmu.config.UserProfileProperties;
import top.lanshan.manmu.memory.UserProfileService;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.StepType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReporterNodeTest {

	@Test
	void guideReporterEnabledPassesUserProfileContext() {
		RecordingUserProfileService profileService = new RecordingUserProfileService("summary: backend engineer");
		RecordingReporterAgent reporterAgent = new RecordingReporterAgent("report");
		ReporterNode node = new ReporterNode(reporterAgent, profileService, new UserProfileProperties());

		StepVerifier.create(node.run(state())).expectNextCount(2).verifyComplete();

		assertThat(profileService.calls()).isEqualTo(1);
		assertThat(reporterAgent.userProfileContext()).isEqualTo("summary: backend engineer");
	}

	@Test
	void guideReporterDisabledDoesNotLoadUserProfileContext() {
		UserProfileProperties properties = new UserProfileProperties();
		properties.setGuideReporter(false);
		RecordingUserProfileService profileService = new RecordingUserProfileService("summary: backend engineer");
		RecordingReporterAgent reporterAgent = new RecordingReporterAgent("report");
		ReporterNode node = new ReporterNode(reporterAgent, profileService, properties);

		StepVerifier.create(node.run(state())).expectNextCount(2).verifyComplete();

		assertThat(profileService.calls()).isZero();
		assertThat(reporterAgent.userProfileContext()).isEmpty();
	}

	@Test
	void emptyUserProfileContextStillGeneratesReport() {
		RecordingUserProfileService profileService = new RecordingUserProfileService("");
		RecordingReporterAgent reporterAgent = new RecordingReporterAgent("report");
		ReporterNode node = new ReporterNode(reporterAgent, profileService, new UserProfileProperties());
		ResearchState state = state();

		StepVerifier.create(node.run(state)).expectNextCount(2).verifyComplete();

		assertThat(state.report()).isEqualTo("report");
		assertThat(reporterAgent.userProfileContext()).isEmpty();
	}

	private ResearchState state() {
		ResearchState state = ResearchState.from(new ResearchRequest("Explain workflow.", "thread-1", 2));
		ResearchStep step = new ResearchStep("Step", "Do work", false, StepType.RESEARCH, "Findings",
				ResearchStep.STATUS_COMPLETED);
		state.plan(new ResearchPlan("Plan", true, "Think", List.of(step)));
		return state;
	}

	private static class RecordingReporterAgent implements top.lanshan.manmu.agent.ReporterAgent {

		private final String report;

		private String userProfileContext;

		RecordingReporterAgent(String report) {
			this.report = report;
		}

		@Override
		public String report(ResearchState state, String userProfileContext) {
			this.userProfileContext = userProfileContext;
			return report;
		}

		String userProfileContext() {
			return userProfileContext;
		}

	}

	private static class RecordingUserProfileService extends UserProfileService {

		private final String userProfileContext;

		private int calls;

		RecordingUserProfileService(String userProfileContext) {
			super(null, null, null, null, new ObjectMapper());
			this.userProfileContext = userProfileContext;
		}

		@Override
		public String getOrCreateProfile(String sessionId) {
			calls++;
			return userProfileContext;
		}

		int calls() {
			return calls;
		}

	}

}
