package top.lanshan.manmu.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.lanshan.manmu.agent.client.AgentClient;
import top.lanshan.manmu.config.UserProfileProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProfileServiceTest {

	private final UserProfileRepository profileRepository = mock(UserProfileRepository.class);

	private final ConversationMessageRepository messageRepository = mock(ConversationMessageRepository.class);

	private final AgentClient agentClient = mock(AgentClient.class);

	private final UserProfileProperties properties = new UserProfileProperties();

	private final List<UserProfileEntity> savedProfiles = new ArrayList<>();

	private final UserProfileService service = new UserProfileService(profileRepository, messageRepository,
			agentClient, properties, new ObjectMapper());

	@Test
	void cachedProfileDoesNotCallAgentClient() {
		when(profileRepository.findTopBySessionIdOrderByUpdatedAtDesc("session"))
			.thenReturn(Mono.just(profile("session", "Backend engineer", "advanced", "concise", "practical",
					Instant.now())));

		String context = service.getOrCreateProfile("session");

		assertThat(context).isEqualTo("summary: Backend engineer; expertise: advanced; detail: concise; style: practical");
		verify(agentClient, never()).call(any(), any());
	}

	@Test
	void emptyHistoryReturnsEmptyProfile() {
		when(profileRepository.findTopBySessionIdOrderByUpdatedAtDesc("session")).thenReturn(Mono.empty());
		when(messageRepository.findBySessionIdOrderByCreatedAtAsc("session")).thenReturn(Flux.empty());

		assertThat(service.getOrCreateProfile("session")).isEmpty();

		verify(agentClient, never()).call(any(), any());
	}

	@Test
	void validJsonIsNormalizedAndSaved() {
		when(profileRepository.findTopBySessionIdOrderByUpdatedAtDesc("session")).thenReturn(Mono.empty());
		when(messageRepository.findBySessionIdOrderByCreatedAtAsc("session"))
			.thenReturn(Flux.just(message("session", "thread", "USER", "I am a senior Java backend engineer.")));
		when(agentClient.call(any(), any())).thenReturn("""
				{
				  "profile_summary": "  Senior Java backend engineer  ",
				  "expertise_level": "ADVANCED",
				  "detail_preference": "balanced",
				  "style_preference": "practical"
				}
				""");
		when(profileRepository.save(any())).thenAnswer(invocation -> {
			UserProfileEntity entity = invocation.getArgument(0);
			savedProfiles.add(entity);
			return Mono.just(entity);
		});

		String context = service.getOrCreateProfile("session");

		assertThat(context)
			.isEqualTo("summary: Senior Java backend engineer; expertise: advanced; detail: balanced; style: practical");
		assertThat(savedProfiles).singleElement().satisfies(profile -> {
			assertThat(profile.getProfileSummary()).isEqualTo("Senior Java backend engineer");
			assertThat(profile.getExpertiseLevel()).isEqualTo("advanced");
			assertThat(profile.getDetailPreference()).isEqualTo("balanced");
			assertThat(profile.getStylePreference()).isEqualTo("practical");
		});
	}

	@Test
	void invalidJsonDoesNotPersistRawModelOutput() {
		when(profileRepository.findTopBySessionIdOrderByUpdatedAtDesc("session")).thenReturn(Mono.empty());
		when(messageRepository.findBySessionIdOrderByCreatedAtAsc("session"))
			.thenReturn(Flux.just(message("session", "thread", "USER", "hello")));
		when(agentClient.call(any(), any())).thenReturn("not json " + "x".repeat(1200));

		assertThat(service.getOrCreateProfile("session")).isEmpty();

		verify(profileRepository, never()).save(any());
	}

	@Test
	void missingSummaryUsesFallbackAndUnknownEnumsAreDropped() {
		when(profileRepository.findTopBySessionIdOrderByUpdatedAtDesc("session")).thenReturn(Mono.empty());
		when(messageRepository.findBySessionIdOrderByCreatedAtAsc("session"))
			.thenReturn(Flux.just(message("session", "thread", "USER", "hello")));
		when(agentClient.call(any(), any())).thenReturn("""
				{
				  "expertise_level": "expert",
				  "detail_preference": "balanced",
				  "style_preference": "storytelling"
				}
				""");
		when(profileRepository.save(any())).thenAnswer(invocation -> {
			UserProfileEntity entity = invocation.getArgument(0);
			savedProfiles.add(entity);
			return Mono.just(entity);
		});

		String context = service.getOrCreateProfile("session");

		assertThat(context).isEqualTo("summary: general user; detail: balanced");
		assertThat(savedProfiles).singleElement().satisfies(profile -> {
			assertThat(profile.getProfileSummary()).isEqualTo("general user");
			assertThat(profile.getExpertiseLevel()).isNull();
			assertThat(profile.getDetailPreference()).isEqualTo("balanced");
			assertThat(profile.getStylePreference()).isNull();
		});
	}

	@Test
	void agentClientExceptionReturnsEmptyProfile() {
		when(profileRepository.findTopBySessionIdOrderByUpdatedAtDesc("session")).thenReturn(Mono.empty());
		when(messageRepository.findBySessionIdOrderByCreatedAtAsc("session"))
			.thenReturn(Flux.just(message("session", "thread", "USER", "hello")));
		when(agentClient.call(any(), any())).thenThrow(new IllegalStateException("provider unavailable"));

		assertThat(service.getOrCreateProfile("session")).isEmpty();

		verify(profileRepository, never()).save(any());
	}

	@Test
	void expiredProfileIsIncludedInExtractionPrompt() {
		when(profileRepository.findTopBySessionIdOrderByUpdatedAtDesc("session"))
			.thenReturn(Mono.just(profile("session", "Old summary", "beginner", "concise", "mixed",
					Instant.now().minusSeconds(7200))));
		when(messageRepository.findBySessionIdOrderByCreatedAtAsc("session"))
			.thenReturn(Flux.just(message("session", "thread", "USER", "I now need advanced details.")));
		when(agentClient.call(any(), any())).thenReturn("""
				{
				  "profile_summary": "Advanced backend engineer",
				  "expertise_level": "advanced",
				  "detail_preference": "comprehensive",
				  "style_preference": "mixed"
				}
				""");
		when(profileRepository.save(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

		service.getOrCreateProfile("session");

		verify(agentClient).call(any(), org.mockito.ArgumentMatchers.contains("Previous profile:"));
		verify(agentClient).call(any(), org.mockito.ArgumentMatchers.contains("Old summary"));
	}

	@Test
	void repositoryFailureReturnsEmptyProfile() {
		when(profileRepository.findTopBySessionIdOrderByUpdatedAtDesc("session"))
			.thenReturn(Mono.error(new IllegalStateException()));

		assertThat(service.getOrCreateProfile("session")).isEmpty();
	}

	private ConversationMessageEntity message(String sessionId, String threadId, String role, String content) {
		ConversationMessageEntity entity = new ConversationMessageEntity();
		entity.setId(UUID.randomUUID());
		entity.setSessionId(sessionId);
		entity.setThreadId(threadId);
		entity.setRole(role);
		entity.setContent(content);
		entity.setCreatedAt(Instant.now());
		return entity;
	}

	private UserProfileEntity profile(String sessionId, String summary, String expertise, String detail,
			String style, Instant updatedAt) {
		UserProfileEntity entity = new UserProfileEntity();
		entity.setId(UUID.randomUUID());
		entity.setSessionId(sessionId);
		entity.setProfileSummary(summary);
		entity.setExpertiseLevel(expertise);
		entity.setDetailPreference(detail);
		entity.setStylePreference(style);
		entity.setUpdatedAt(updatedAt);
		return entity;
	}

}
