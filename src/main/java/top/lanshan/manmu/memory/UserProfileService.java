package top.lanshan.manmu.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import top.lanshan.manmu.agent.client.AgentClient;
import top.lanshan.manmu.config.UserProfileProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserProfileService {

    private static final Logger logger = LoggerFactory.getLogger(UserProfileService.class);

    private static final String DEFAULT_PROFILE_SUMMARY = "general user";

    private static final int MAX_PROFILE_SUMMARY_CHARS = 500;

    private static final int MAX_PROFILE_CONTEXT_CHARS = 700;

    private static final int MAX_CONVERSATION_TEXT_CHARS = 8000;

    private static final Set<String> EXPERTISE_LEVELS = Set.of("beginner", "intermediate", "advanced");

    private static final Set<String> DETAIL_PREFERENCES = Set.of("concise", "balanced", "comprehensive");

    private static final Set<String> STYLE_PREFERENCES = Set.of("practical", "theoretical", "mixed");

    private final UserProfileRepository profileRepository;

    private final ConversationMessageRepository messageRepository;

    private final AgentClient agentClient;

    private final UserProfileProperties properties;

    private final ObjectMapper objectMapper;

    public UserProfileService(UserProfileRepository profileRepository, ConversationMessageRepository messageRepository,
            AgentClient agentClient, UserProfileProperties properties, ObjectMapper objectMapper) {
        this.profileRepository = profileRepository;
        this.messageRepository = messageRepository;
        this.agentClient = agentClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String getOrCreateProfile(String sessionId) {
        if (properties == null || !properties.isEnabled()) {
            return "";
        }
        if (sessionId == null || sessionId.isBlank()) {
            return "";
        }

        try {
            return getOrCreateProfileSafely(sessionId.strip());
        } catch (Exception e) {
            logger.warn("Failed to build user profile for session {}: {}", sessionId, safeErrorMessage(e));
            return "";
        }
    }

    private String getOrCreateProfileSafely(String sessionId) {
        // GraphResearchRunner invokes graph nodes on boundedElastic, so this synchronous boundary stays off event loops.
        UserProfileEntity existing = profileRepository.findTopBySessionIdOrderByUpdatedAtDesc(sessionId).block();
        if (existing != null && isRecent(existing.getUpdatedAt())) {
            return formatProfileContext(existing);
        }

        List<String> recentMessages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
            .takeLast(properties.getMaxMessagesForExtraction())
            .map(ConversationMessageEntity::getContent)
            .filter(content -> content != null && !content.isBlank())
            .collectList()
            .block();
        if (recentMessages == null || recentMessages.isEmpty()) {
            return "";
        }

        String conversationText = truncate(recentMessages.stream()
            .map(String::strip)
            .collect(Collectors.joining("\n")), MAX_CONVERSATION_TEXT_CHARS);
        Optional<UserProfileFields> fields = parseProfile(extractProfile(conversationText, existing));
        if (fields.isEmpty()) {
            return "";
        }

        UserProfileEntity entity = new UserProfileEntity();
        entity.setNewEntity(true);
        entity.setId(UUID.randomUUID());
        entity.setSessionId(sessionId);
        entity.setUpdatedAt(Instant.now());
        fillEntity(entity, fields.get());
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
        String historyContext = previousProfileContext(previous);
        String systemPrompt = """
                You are a user profile extractor. Based on the user's conversation messages, extract structured profile information. %s
                Output ONLY valid JSON with these four fields:
                {
                  "profile_summary": "one sentence describing the user's role and background",
                  "expertise_level": "beginner|intermediate|advanced",
                  "detail_preference": "concise|balanced|comprehensive",
                  "style_preference": "practical|theoretical|mixed"
                }
                Output ONLY the JSON object, nothing else.
                """.formatted(previous != null
                ? "The previous profile is provided; update it only if new information is richer or different."
                : "");
        String userPrompt = "User conversation messages:" + historyContext + "\n" + conversationText;

        try {
            String result = agentClient.call(systemPrompt, userPrompt);
            if (result == null || result.isBlank()) {
                return "";
            }
            return result.trim();
        } catch (Exception e) {
            logger.warn("Failed to extract user profile: {}", safeErrorMessage(e));
            return "";
        }
    }

    private String previousProfileContext(UserProfileEntity previous) {
        if (previous == null || previous.getProfileSummary() == null || previous.getProfileSummary().isBlank()) {
            return "";
        }
        return """

                Previous profile:
                - Summary: %s
                - Expertise: %s
                - Detail: %s
                - Style: %s

                Update this profile if the user's new messages reveal more or different information.
                """.formatted(
                previous.getProfileSummary(),
                previous.getExpertiseLevel() == null ? "unknown" : previous.getExpertiseLevel(),
                previous.getDetailPreference() == null ? "unknown" : previous.getDetailPreference(),
                previous.getStylePreference() == null ? "unknown" : previous.getStylePreference());
    }

    private Optional<UserProfileFields> parseProfile(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isObject()) {
                return Optional.empty();
            }
            return Optional.of(new UserProfileFields(
                    summary(node),
                    normalizeEnum(textField(node, "expertise_level"), EXPERTISE_LEVELS),
                    normalizeEnum(textField(node, "detail_preference"), DETAIL_PREFERENCES),
                    normalizeEnum(textField(node, "style_preference"), STYLE_PREFERENCES)));
        } catch (Exception e) {
            logger.warn("Failed to parse profile JSON: {}", safeErrorMessage(e));
            return Optional.empty();
        }
    }

    private void fillEntity(UserProfileEntity entity, UserProfileFields fields) {
        entity.setProfileSummary(fields.profileSummary());
        entity.setExpertiseLevel(fields.expertiseLevel());
        entity.setDetailPreference(fields.detailPreference());
        entity.setStylePreference(fields.stylePreference());
    }

    private String formatProfileContext(UserProfileEntity entity) {
        if (entity == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        String summary = normalizeSummary(entity.getProfileSummary(), false);
        if (summary != null && !summary.isBlank()) {
            parts.add("summary: " + summary);
        }
        String expertise = normalizeEnum(entity.getExpertiseLevel(), EXPERTISE_LEVELS);
        if (expertise != null) {
            parts.add("expertise: " + expertise);
        }
        String detail = normalizeEnum(entity.getDetailPreference(), DETAIL_PREFERENCES);
        if (detail != null) {
            parts.add("detail: " + detail);
        }
        String style = normalizeEnum(entity.getStylePreference(), STYLE_PREFERENCES);
        if (style != null) {
            parts.add("style: " + style);
        }
        return truncate(String.join("; ", parts), MAX_PROFILE_CONTEXT_CHARS);
    }

    private String summary(JsonNode node) {
        return normalizeSummary(textField(node, "profile_summary"), true);
    }

    private String normalizeSummary(String value, boolean fallback) {
        if (value == null || value.isBlank()) {
            return fallback ? DEFAULT_PROFILE_SUMMARY : "";
        }
        return truncate(value.strip().replaceAll("\\s+", " "), MAX_PROFILE_SUMMARY_CHARS);
    }

    private String normalizeEnum(String value, Set<String> allowed) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : null;
    }

    private String textField(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "...[truncated]";
    }

    private String safeErrorMessage(Throwable error) {
        if (error == null) {
            return "Unknown error";
        }
        if (error.getMessage() != null && !error.getMessage().isBlank()) {
            return error.getMessage();
        }
        return error.getClass().getSimpleName();
    }

    private record UserProfileFields(String profileSummary, String expertiseLevel,
            String detailPreference, String stylePreference) {
    }

}
