package top.lanshan.manmu.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.lanshan.manmu.agent.client.AgentClient;
import top.lanshan.manmu.config.UserProfileProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserProfileService {

    private static final Logger logger = LoggerFactory.getLogger(UserProfileService.class);

    private final UserProfileRepository profileRepository;
    private final ConversationMessageRepository messageRepository;
    private final AgentClient agentClient;
    private final UserProfileProperties properties;

    public UserProfileService(UserProfileRepository profileRepository,
            ConversationMessageRepository messageRepository,
            AgentClient agentClient,
            UserProfileProperties properties) {
        this.profileRepository = profileRepository;
        this.messageRepository = messageRepository;
        this.agentClient = agentClient;
        this.properties = properties;
    }

    public String getOrCreateProfile(String sessionId) {
        if (!properties.isEnabled()) {
            return "";
        }
        if (sessionId == null || sessionId.isBlank()) {
            return "";
        }

        UserProfileEntity existing = profileRepository
                .findTopBySessionIdOrderByUpdatedAtDesc(sessionId)
                .block();
        if (existing != null && isRecent(existing.getUpdatedAt())) {
            return existing.getProfileSummary();
        }

        List<String> recentMessages = messageRepository
                .findBySessionIdOrderByCreatedAtAsc(sessionId)
                .takeLast(properties.getMaxMessagesForExtraction())
                .map(ConversationMessageEntity::getContent)
                .collectList()
                .block();
        if (recentMessages == null || recentMessages.isEmpty()) {
            return "";
        }

        String conversationText = recentMessages.stream()
                .collect(Collectors.joining("\n"));
        String profileSummary = extractProfile(conversationText);
        if (profileSummary == null || profileSummary.isBlank()) {
            return "";
        }

        saveProfile(sessionId, profileSummary);
        return profileSummary;
    }

    private boolean isRecent(Instant updatedAt) {
        if (updatedAt == null) {
            return false;
        }
        long cacheSeconds = (long) properties.getCacheMinutes() * 60;
        return Instant.now().minusSeconds(cacheSeconds).isBefore(updatedAt);
    }

    private String extractProfile(String conversationText) {
        String systemPrompt = """
                You are a user profile extractor. Based on the user's conversation messages, \
                summarize the user's role, background, and expertise level in ONE concise sentence. \
                Focus on information that would help tailor research responses to this user. \
                If you cannot determine any profile information, output "general user". \
                Output ONLY the summary sentence, nothing else.\
                """;

        String userPrompt = "User conversation messages:\n" + conversationText;

        try {
            String result = agentClient.call(systemPrompt, userPrompt);
            if (result == null || result.isBlank()) {
                return "";
            }
            return result.trim();
        } catch (Exception e) {
            logger.warn("Failed to extract user profile: {}", e.getMessage());
            return "";
        }
    }

    private void saveProfile(String sessionId, String profileSummary) {
        UserProfileEntity entity = new UserProfileEntity();
        entity.setNewEntity(true);
        entity.setId(UUID.randomUUID());
        entity.setSessionId(sessionId);
        entity.setProfileSummary(profileSummary);
        entity.setUpdatedAt(Instant.now());
        profileRepository.save(entity).block();
    }
}
