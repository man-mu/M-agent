package top.lanshan.manmu.memory;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ConversationMessageRecord(
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("thread_id") String threadId,
        String role,
        String content,
        @JsonProperty("created_at") Instant createdAt) {
}
