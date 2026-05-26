package top.lanshan.manmu.memory;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record UserProfileRecord(
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("profile_summary") String profileSummary,
        @JsonProperty("expertise_level") String expertiseLevel,
        @JsonProperty("detail_preference") String detailPreference,
        @JsonProperty("style_preference") String stylePreference,
        @JsonProperty("updated_at") Instant updatedAt) {
}
