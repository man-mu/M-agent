package top.lanshan.manmu.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ConversationSummary(
        @JsonProperty("session_id") String sessionId,
        String title,
        @JsonProperty("message_count") long messageCount,
        @JsonProperty("last_message_at") Instant lastMessageAt) {
}
