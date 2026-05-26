package top.lanshan.manmu.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final ObjectMapper objectMapper = new ObjectMapper();

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
            return formatProfileContext(existing);
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
        String rawResult = extractProfile(conversationText, existing);
        if (rawResult == null || rawResult.isBlank()) {
            return "";
        }

        UserProfileEntity entity = new UserProfileEntity();
        entity.setNewEntity(true);
        entity.setId(UUID.randomUUID());
        entity.setSessionId(sessionId);
        entity.setUpdatedAt(Instant.now());
        parseAndFillEntity(entity, rawResult);
        profileRepository.save(entity).block();
        return formatProfileContext(entity);
    }

    private boolean isRecent(Instant updatedAt) {
        if (updatedAt == null) {
            return false;
        }
        long cacheSeconds = (long) properties.getCacheMinutes() * 60;
        return Instant.now().minusSeconds(cacheSeconds).isBefore(updatedAt);
    }

    private String extractProfile(String conversationText, UserProfileEntity previous) {
        String historyContext = "";
        if (previous != null && previous.getProfileSummary() != null) {
            historyContext = """

                    Previous profile:
                    - Role: %s
                    - Expertise: %s
                    - Detail: %s
                    - Style: %s

                    Update this profile if the user's new messages reveal more or different information.\
                    """.formatted(
                    previous.getProfileSummary() != null ? previous.getProfileSummary() : "unknown",
                    previous.getExpertiseLevel() != null ? previous.getExpertiseLevel() : "unknown",
                    previous.getDetailPreference() != null ? previous.getDetailPreference() : "unknown",
                    previous.getStylePreference() != null ? previous.getStylePreference() : "unknown");
        }

        String systemPrompt = """
                You are a user profile extractor. Based on the user's conversation messages, \
                extract structured profile information. %s \
                Output ONLY valid JSON with these four fields:
                {
                  "profile_summary": "one sentence describing the user's role and background",
                  "expertise_level": "beginner|intermediate|advanced",
                  "detail_preference": "concise|balanced|comprehensive",
                  "style_preference": "practical|theoretical|mixed"
                }
                Output ONLY the JSON object, nothing else.\
                """.formatted(previous != null ? "The previous profile is provided — update it if new information is richer or different." : "");

        String userPrompt = "User conversation messages:" + historyContext + "\n" + conversationText;

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

    private void parseAndFillEntity(UserProfileEntity entity, String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.has("profile_summary")) {
                entity.setProfileSummary(node.get("profile_summary").asText());
            }
            if (node.has("expertise_level")) {
                entity.setExpertiseLevel(node.get("expertise_level").asText());
            }
            if (node.has("detail_preference")) {
                entity.setDetailPreference(node.get("detail_preference").asText());
            }
            if (node.has("style_preference")) {
                entity.setStylePreference(node.get("style_preference").asText());
            }
        } catch (Exception e) {
            logger.warn("Failed to parse profile JSON, using raw text as summary: {}", e.getMessage());
            entity.setProfileSummary(json);
        }
    }

    private String formatProfileContext(UserProfileEntity entity) {
        StringBuilder sb = new StringBuilder();
        if (entity.getProfileSummary() != null && !entity.getProfileSummary().isBlank()) {
            sb.append(entity.getProfileSummary());
        }
        if (entity.getExpertiseLevel() != null && !entity.getExpertiseLevel().isBlank()) {
            sb.append(" (expertise: ").append(entity.getExpertiseLevel()).append(")");
        }
        if (entity.getDetailPreference() != null && !entity.getDetailPreference().isBlank()) {
            sb.append(", detail: ").append(entity.getDetailPreference());
        }
        if (entity.getStylePreference() != null && !entity.getStylePreference().isBlank()) {
            sb.append(", style: ").append(entity.getStylePreference());
        }
        return sb.toString();
    }
}
