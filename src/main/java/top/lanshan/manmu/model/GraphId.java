package top.lanshan.manmu.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GraphId(@JsonProperty("session_id") String sessionId, @JsonProperty("thread_id") String threadId) {
}
