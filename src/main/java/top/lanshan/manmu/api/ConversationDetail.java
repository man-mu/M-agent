package top.lanshan.manmu.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import top.lanshan.manmu.memory.ConversationMessageRecord;

import java.util.List;

public record ConversationDetail(
        @JsonProperty("session_id") String sessionId,
        String title,
        @JsonProperty("message_count") long messageCount,
        List<ConversationMessageRecord> messages) {
}
