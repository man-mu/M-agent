package top.lanshan.manmu.memory;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record UserProfileRecord(
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("profile_summary") String profileSummary,
        @JsonProperty("updated_at") Instant updatedAt) {
}
