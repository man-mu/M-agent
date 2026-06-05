package top.lanshan.manmu.skill.health;

import java.time.Instant;
import java.util.Map;

public record SkillInvocationRecord(String id, String skillName, String source,
        Instant invokedAt, boolean success, Map<String, Object> input,
        String output, String error, long durationMs) {
}
